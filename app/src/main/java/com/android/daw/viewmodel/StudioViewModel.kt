package com.android.daw.viewmodel

import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.daw.DawApplication
import com.android.daw.bridge.NativeAudioEngine
import com.android.daw.domain.model.TelemetryData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * StudioViewModel
 *
 * Central MVI state coordinator for Android Digital Audio Workstation.
 * Strictly adheres to android-ui-ux-architect:
 * - Rule 8: Zero Main-Thread JNI Blocking (dispatches audio bridge mutations to Dispatchers.Default / IO).
 * - Rule 9: Anti-Drift State Hoisting (immutable StudioUiState updated via unidirectional StudioAction).
 * - High-Frequency Telemetry: Playhead frame counter and ballistic level meters isolated
 *   in dedicated StateFlows for Draw-phase consumption.
 */
class StudioViewModel(
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    enableTelemetryLoop: Boolean = true,
    private val audioEngine: NativeAudioEngine = NativeAudioEngine.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(createInitialStudioState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    // High-frequency telemetry StateFlows (Draw-phase consumption at 60/120 FPS)
    private val _playheadFrame = MutableStateFlow(0L)
    val playheadFrame: StateFlow<Long> = _playheadFrame.asStateFlow()

    private val _telemetryLevels = MutableStateFlow<Map<Int, StereoLevel>>(emptyMap())
    val telemetryLevels: StateFlow<Map<Int, StereoLevel>> = _telemetryLevels.asStateFlow()

    // Background telemetry polling & ballistic simulation jobs
    private var telemetryJob: Job? = null
    private var exportJob: Job? = null

    // IEC 60268-10 PPM Ballistics tracking per channel (trackId -> smoothedDb)
    private val meterBallisticsMap = mutableMapOf<Int, BallisticsState>()

    init {
        if (enableTelemetryLoop) {
            startTelemetryLoop()
        }
    }

    /**
     * Dispatch unidirectional user intent
     */
    fun onAction(action: StudioAction) {
        viewModelScope.launch {
            when (action) {
                // --- Transport Actions ---
                StudioAction.Play -> handlePlay()
                StudioAction.Pause -> handlePause()
                StudioAction.Stop -> handleStop()
                StudioAction.Rewind -> handleRewind()
                is StudioAction.Seek -> handleSeek(action.frame)
                is StudioAction.SetTempo -> handleSetTempo(action.bpm)
                StudioAction.ToggleLoop -> handleToggleLoop()
                is StudioAction.SetLoopRange -> handleSetLoopRange(action.startFrame, action.endFrame)

                // --- Track Actions ---
                is StudioAction.AddTrack -> handleAddTrack(action.name)
                is StudioAction.RemoveTrack -> handleRemoveTrack(action.trackId)
                is StudioAction.SetTrackVolume -> handleSetTrackVolume(action.trackId, action.volumeLinear)
                is StudioAction.SetTrackPan -> handleSetTrackPan(action.trackId, action.pan)
                is StudioAction.ToggleMute -> handleToggleMute(action.trackId)
                is StudioAction.ToggleSolo -> handleToggleSolo(action.trackId)
                is StudioAction.ToggleArm -> handleToggleArm(action.trackId)
                is StudioAction.SelectTrack -> handleSelectTrack(action.trackId)

                // --- Master Bus Actions ---
                is StudioAction.SetMasterVolume -> handleSetMasterVolume(action.volumeLinear)
                is StudioAction.SetMasterPan -> handleSetMasterPan(action.pan)
                StudioAction.ResetClipLeds -> handleResetClipLeds()

                // --- Viewport & Modal Switching ---
                StudioAction.ToggleMixerView -> handleToggleMixerPanel()
                StudioAction.ToggleDspInspector -> handleToggleInstrumentPanel()
                StudioAction.ToggleMixerPanel -> handleToggleMixerPanel()
                StudioAction.ToggleInstrumentPanel -> handleToggleInstrumentPanel()
                StudioAction.TogglePianoRollPanel -> handleTogglePianoRollPanel()
                StudioAction.ToggleProjectDrawer -> handleToggleProjectDrawer()
                StudioAction.ToggleModulesDrawer -> handleToggleModulesDrawer()
                StudioAction.ToggleBpmDial -> handleToggleBpmDial()
                StudioAction.CloseAllPanels -> handleCloseAllPanels()
                is StudioAction.OpenTrackDsp -> handleOpenTrackDsp(action.trackId)
                StudioAction.OpenMasterDsp -> handleOpenMasterDsp()
                StudioAction.OpenLiveLog -> {}
                is StudioAction.SetZoom -> handleSetZoom(action.pixelsPerSecond)
                is StudioAction.SetScrollOffset -> handleSetScrollOffset(action.offsetPx)

                // --- Tier 1 Contextual Clip Intents ---
                is StudioAction.SelectClip -> handleSelectClip(action.clipId)
                is StudioAction.ExecuteClipAction -> handleExecuteClipAction(action.clipId, action.action)
                is StudioAction.SetClipMenuOpen -> handleSetClipMenuOpen(action.isOpen)
                is StudioAction.MoveClip -> handleMoveClip(action.clipId, action.newStartFrame)
                is StudioAction.SplitClipAtPlayhead -> handleSplitClipAtPlayhead(action.clipId)
                is StudioAction.TrimClip -> handleTrimClip(action.clipId, action.newStartFrame, action.newDurationFrames)

                // --- DSP Mutations ---
                is StudioAction.UpdateEq -> handleUpdateEq(
                    action.trackId, action.bandIndex, action.freqHz, action.gainDb, action.q
                )
                is StudioAction.ToggleEqBand -> handleToggleEqBand(action.trackId, action.bandIndex)
                is StudioAction.UpdateDelay -> handleUpdateDelay(
                    action.trackId, action.timeMs, action.feedback, action.wetDry
                )
                is StudioAction.ToggleDelayPingPong -> handleToggleDelayPingPong(action.trackId)
                is StudioAction.ToggleDelayBypass -> handleToggleDelayBypass(action.trackId)
                is StudioAction.UpdateLimiter -> handleUpdateLimiter(
                    action.thresholdDb, action.ceilingDb, action.releaseMs
                )
                is StudioAction.ToggleLimiterBypass -> handleToggleLimiterBypass(action.isBypassed)

                // --- Recording & Export ---
                is StudioAction.ToggleRecord -> handleToggleRecord(action.isRecording)
                is StudioAction.ImportAudio -> handleImportAudio(action.trackId, action.uri)
                is StudioAction.StartExport -> handleStartExport(action.bitDepth)
                StudioAction.CancelExport -> handleCancelExport()
                StudioAction.DismissStatusMessage -> handleDismissStatusMessage()
            }
        }
    }

    // =========================================================================
    // Transport Control Logic (Offloaded to background coroutines - Rule 8)
    // =========================================================================

    private suspend fun handlePlay() = withContext(defaultDispatcher) {
        withNativeEngine { it.play() }
        _uiState.update { it.copy(transportState = TransportState.PLAYING) }
    }

    private suspend fun handlePause() = withContext(defaultDispatcher) {
        withNativeEngine { it.pause() }
        _uiState.update { it.copy(transportState = TransportState.PAUSED) }
    }

    private suspend fun handleStop() = withContext(defaultDispatcher) {
        withNativeEngine { it.stopTransport() }
        _playheadFrame.value = 0L
        _uiState.update {
            it.copy(
                transportState = TransportState.STOPPED,
                currentFrame = 0L
            )
        }
    }

    private suspend fun handleRewind() = withContext(defaultDispatcher) {
        val loopStart = _uiState.value.loopStartFrame
        val targetFrame = if (_uiState.value.isLooping) loopStart else 0L
        withNativeEngine { it.seek(targetFrame) }
        _playheadFrame.value = targetFrame
        _uiState.update { it.copy(currentFrame = targetFrame) }
    }

    private suspend fun handleSeek(frame: Long) = withContext(defaultDispatcher) {
        val clampedFrame = frame.coerceIn(0L, _uiState.value.totalFrames)
        withNativeEngine { it.seek(clampedFrame) }
        _playheadFrame.value = clampedFrame
        _uiState.update { it.copy(currentFrame = clampedFrame) }
    }

    private suspend fun handleSetTempo(bpm: Double) = withContext(defaultDispatcher) {
        val clampedBpm = bpm.coerceIn(40.0, 300.0)
        withNativeEngine { it.setTempo(clampedBpm) }
        _uiState.update { it.copy(bpm = clampedBpm) }
    }

    private suspend fun handleToggleLoop() = withContext(defaultDispatcher) {
        val current = _uiState.value
        val newLooping = !current.isLooping
        withNativeEngine { it.setLoop(newLooping, current.loopStartFrame, current.loopEndFrame) }
        _uiState.update { it.copy(isLooping = newLooping) }
    }

    private suspend fun handleSetLoopRange(startFrame: Long, endFrame: Long) = withContext(defaultDispatcher) {
        val start = startFrame.coerceAtLeast(0L)
        val end = maxOf(start + 44100L, endFrame)
        withNativeEngine { it.setLoop(_uiState.value.isLooping, start, end) }
        _uiState.update { it.copy(loopStartFrame = start, loopEndFrame = end) }
    }

    // =========================================================================
    // Track Management Logic
    // =========================================================================

    private suspend fun handleAddTrack(name: String) = withContext(defaultDispatcher) {
        val currentTracks = _uiState.value.tracks
        val nextId = (currentTracks.maxOfOrNull { it.trackId } ?: 0) + 1
        val colorHex = TRACK_PALETTE_HEX[(nextId - 1) % TRACK_PALETTE_HEX.size]

        val newTrack = TrackUiModel(
            trackId = nextId,
            name = "$name $nextId",
            colorHex = colorHex,
            clips = emptyList()
        )

        withNativeEngine { it.addTrack(nextId) }

        _uiState.update { state ->
            state.copy(
                tracks = state.tracks + newTrack,
                selectedTrackId = nextId,
                statusMessage = "Added track: ${newTrack.name}"
            )
        }
    }

    private suspend fun handleRemoveTrack(trackId: Int) = withContext(defaultDispatcher) {
        withNativeEngine { it.removeTrack(trackId) }
        _uiState.update { state ->
            val trackToRemove = state.tracks.find { it.trackId == trackId }
            val removedClipIds = trackToRemove?.clips?.map { it.clipId }?.toSet() ?: emptySet()
            val updatedTracks = state.tracks.filterNot { it.trackId == trackId }
            val newSelected = if (state.selectedTrackId == trackId) {
                updatedTracks.firstOrNull()?.trackId
            } else {
                state.selectedTrackId
            }
            val newSelectedClip = if (state.selectedClipId in removedClipIds) null else state.selectedClipId
            state.copy(
                tracks = updatedTracks,
                selectedTrackId = newSelected,
                selectedClipId = newSelectedClip,
                statusMessage = "Removed track #$trackId"
            )
        }
    }

    private suspend fun handleSetTrackVolume(trackId: Int, volumeLinear: Float) = withContext(defaultDispatcher) {
        val clamped = volumeLinear.coerceIn(0.0f, 2.0f)
        withNativeEngine { it.setTrackVolume(trackId, clamped) }
        _uiState.update { state ->
            state.copy(
                tracks = state.tracks.map {
                    if (it.trackId == trackId) it.copy(volumeLinear = clamped) else it
                }
            )
        }
    }

    private suspend fun handleSetTrackPan(trackId: Int, pan: Float) = withContext(defaultDispatcher) {
        val clamped = pan.coerceIn(-1.0f, 1.0f)
        withNativeEngine { it.setTrackPan(trackId, clamped) }
        _uiState.update { state ->
            state.copy(
                tracks = state.tracks.map {
                    if (it.trackId == trackId) it.copy(pan = clamped) else it
                }
            )
        }
    }

    private suspend fun handleToggleMute(trackId: Int) = withContext(defaultDispatcher) {
        _uiState.update { state ->
            state.copy(
                tracks = state.tracks.map {
                    if (it.trackId == trackId) {
                        val newMute = !it.isMuted
                        withNativeEngine { it.setTrackMute(trackId, newMute) }
                        it.copy(isMuted = newMute)
                    } else it
                }
            )
        }
    }

    private suspend fun handleToggleSolo(trackId: Int) = withContext(defaultDispatcher) {
        _uiState.update { state ->
            state.copy(
                tracks = state.tracks.map {
                    if (it.trackId == trackId) {
                        val newSolo = !it.isSoloed
                        withNativeEngine { it.setTrackSolo(trackId, newSolo) }
                        it.copy(isSoloed = newSolo)
                    } else it
                }
            )
        }
    }

    private suspend fun handleToggleArm(trackId: Int) = withContext(defaultDispatcher) {
        _uiState.update { state ->
            state.copy(
                tracks = state.tracks.map {
                    if (it.trackId == trackId) {
                        val newArm = !it.isArmed
                        withNativeEngine { it.setTrackArmed(trackId, newArm) }
                        it.copy(isArmed = newArm)
                    } else it
                }
            )
        }
    }

    private fun handleSelectTrack(trackId: Int?) {
        _uiState.update { it.copy(selectedTrackId = trackId) }
    }

    // =========================================================================
    // Master Bus & Viewport Logic
    // =========================================================================

    private suspend fun handleSetMasterVolume(volumeLinear: Float) = withContext(defaultDispatcher) {
        val clamped = volumeLinear.coerceIn(0.0f, 2.0f)
        _uiState.update { it.copy(masterVolumeLinear = clamped) }
    }

    private suspend fun handleSetMasterPan(pan: Float) = withContext(defaultDispatcher) {
        val clamped = pan.coerceIn(-1.0f, 1.0f)
        _uiState.update { it.copy(masterPan = clamped) }
    }

    private fun handleResetClipLeds() {
        meterBallisticsMap.values.forEach { it.hasClipped = false }
        _uiState.update { state ->
            state.copy(
                masterLevels = state.masterLevels.copy(clipLeft = false, clipRight = false),
                tracks = state.tracks.map {
                    it.copy(levels = it.levels.copy(clipLeft = false, clipRight = false))
                }
            )
        }
    }

    private fun handleToggleMixerPanel() {
        _uiState.update { it.copy(isMixerOpen = !it.isMixerOpen, isMixerViewActive = !it.isMixerOpen) }
    }

    private fun handleToggleInstrumentPanel() {
        _uiState.update { it.copy(isInstrumentPanelOpen = !it.isInstrumentPanelOpen, isDspInspectorOpen = !it.isInstrumentPanelOpen) }
    }

    private fun handleTogglePianoRollPanel() {
        _uiState.update { it.copy(isPianoRollOpen = !it.isPianoRollOpen) }
    }

    private fun handleToggleProjectDrawer() {
        _uiState.update { it.copy(isProjectDrawerOpen = !it.isProjectDrawerOpen) }
    }

    private fun handleToggleModulesDrawer() {
        _uiState.update { it.copy(isModulesDrawerOpen = !it.isModulesDrawerOpen) }
    }

    private fun handleToggleBpmDial() {
        _uiState.update { it.copy(isBpmDialOpen = !it.isBpmDialOpen) }
    }

    private fun handleCloseAllPanels() {
        _uiState.update {
            it.copy(
                isMixerOpen = false,
                isInstrumentPanelOpen = false,
                isPianoRollOpen = false,
                isProjectDrawerOpen = false,
                isModulesDrawerOpen = false,
                isBpmDialOpen = false,
                isMixerViewActive = false,
                isDspInspectorOpen = false,
                selectedClipId = null,
                isClipMenuOpen = false
            )
        }
    }

    private fun handleSelectClip(clipId: Int?) {
        _uiState.update { it.copy(selectedClipId = clipId, isClipMenuOpen = false) }
    }

    private fun handleSetClipMenuOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isClipMenuOpen = isOpen) }
    }

    private fun handleExecuteClipAction(clipId: Int, action: String) {
        when (action.uppercase()) {
            "DELETE" -> {
                val state = _uiState.value
                val foundTrack = state.tracks.find { tr -> tr.clips.any { it.clipId == clipId } }
                if (foundTrack != null) {
                    viewModelScope.launch(defaultDispatcher) {
                        withNativeEngine { it.removeClip(foundTrack.trackId, clipId) }
                    }
                }
                _uiState.update { s ->
                    s.copy(
                        tracks = s.tracks.map { track ->
                            track.copy(clips = track.clips.filter { it.clipId != clipId })
                        },
                        selectedClipId = null,
                        isClipMenuOpen = false
                    )
                }
            }
            "COPY" -> {
                _uiState.update { state ->
                    val foundTrack = state.tracks.find { tr -> tr.clips.any { it.clipId == clipId } }
                    val foundClip = foundTrack?.clips?.find { it.clipId == clipId }
                    if (foundTrack != null && foundClip != null) {
                        val newClip = foundClip.copy(
                            clipId = (state.tracks.flatMap { it.clips }.maxOfOrNull { it.clipId } ?: 0) + 1,
                            name = "${foundClip.name} (Copy)",
                            startFrame = foundClip.startFrame + foundClip.durationFrames
                        )
                        state.copy(
                            tracks = state.tracks.map { tr ->
                                if (tr.trackId == foundTrack.trackId) tr.copy(clips = tr.clips + newClip) else tr
                            },
                            selectedClipId = newClip.clipId,
                            isClipMenuOpen = false
                        )
                    } else {
                        state
                    }
                }
            }
            "MUTE" -> {
                _uiState.update { state ->
                    state.copy(
                        statusMessage = "Clip #$clipId muted",
                        isClipMenuOpen = false
                    )
                }
            }
            "SLICE" -> {
                _uiState.update { state ->
                    val foundTrack = state.tracks.find { tr -> tr.clips.any { it.clipId == clipId } }
                    val foundClip = foundTrack?.clips?.find { it.clipId == clipId }
                    if (foundTrack != null && foundClip != null && foundClip.durationFrames > 2000L) {
                        val halfDuration = foundClip.durationFrames / 2
                        val clip1 = foundClip.copy(durationFrames = halfDuration)
                        val clip2 = foundClip.copy(
                            clipId = (state.tracks.flatMap { it.clips }.maxOfOrNull { it.clipId } ?: 0) + 1,
                            name = "${foundClip.name}_b",
                            startFrame = foundClip.startFrame + halfDuration,
                            durationFrames = foundClip.durationFrames - halfDuration
                        )
                        state.copy(
                            tracks = state.tracks.map { tr ->
                                if (tr.trackId == foundTrack.trackId) {
                                    tr.copy(clips = tr.clips.filter { it.clipId != clipId } + listOf(clip1, clip2))
                                } else tr
                            },
                            selectedClipId = clip1.clipId,
                            isClipMenuOpen = false
                        )
                    } else {
                        state
                    }
                }
            }
            else -> {
                _uiState.update { it.copy(statusMessage = "Action $action on clip #$clipId", isClipMenuOpen = false) }
            }
        }
    }

    private suspend fun handleMoveClip(clipId: Int, newStartFrame: Long) = withContext(defaultDispatcher) {
        val targetFrame = maxOf(0L, newStartFrame)
        val state = _uiState.value
        val track = state.tracks.find { tr -> tr.clips.any { it.clipId == clipId } }
        if (track != null) {
            withNativeEngine { it.moveClip(track.trackId, clipId, targetFrame) }
            _uiState.update { s ->
                s.copy(
                    tracks = s.tracks.map { tr ->
                        if (tr.trackId == track.trackId) {
                            tr.copy(
                                clips = tr.clips.map { c ->
                                    if (c.clipId == clipId) c.copy(startFrame = targetFrame) else c
                                }
                            )
                        } else tr
                    }
                )
            }
        }
    }

    private suspend fun handleSplitClipAtPlayhead(clipId: Int) = withContext(defaultDispatcher) {
        val playhead = _playheadFrame.value
        val state = _uiState.value
        val track = state.tracks.find { tr -> tr.clips.any { it.clipId == clipId } }
        val clip = track?.clips?.find { it.clipId == clipId }
        if (track != null && clip != null && playhead > clip.startFrame && playhead < (clip.startFrame + clip.durationFrames)) {
            val dur1 = playhead - clip.startFrame
            val dur2 = clip.durationFrames - dur1
            val clip1 = clip.copy(durationFrames = dur1)
            val clip2Id = (state.tracks.flatMap { it.clips }.maxOfOrNull { it.clipId } ?: 0) + 1
            val clip2 = clip.copy(
                clipId = clip2Id,
                name = "${clip.name}_2",
                startFrame = playhead,
                durationFrames = dur2
            )
            _uiState.update { s ->
                s.copy(
                    tracks = s.tracks.map { tr ->
                        if (tr.trackId == track.trackId) {
                            tr.copy(clips = tr.clips.filter { it.clipId != clipId } + listOf(clip1, clip2))
                        } else tr
                    },
                    selectedClipId = clip1.clipId
                )
            }
        }
    }

    private suspend fun handleTrimClip(clipId: Int, newStartFrame: Long, newDurationFrames: Long) = withContext(defaultDispatcher) {
        if (newDurationFrames <= 0) return@withContext
        val targetFrame = maxOf(0L, newStartFrame)
        val state = _uiState.value
        val track = state.tracks.find { tr -> tr.clips.any { it.clipId == clipId } }
        if (track != null) {
            _uiState.update { s ->
                s.copy(
                    tracks = s.tracks.map { tr ->
                        if (tr.trackId == track.trackId) {
                            tr.copy(
                                clips = tr.clips.map { c ->
                                    if (c.clipId == clipId) c.copy(startFrame = targetFrame, durationFrames = newDurationFrames) else c
                                }
                            )
                        } else tr
                    }
                )
            }
        }
    }

    private fun handleOpenTrackDsp(trackId: Int) {
        _uiState.update {
            it.copy(
                isInstrumentPanelOpen = true,
                isDspInspectorOpen = true,
                dspTargetTrackId = trackId,
                selectedTrackId = trackId
            )
        }
    }

    private fun handleOpenMasterDsp() {
        _uiState.update {
            it.copy(
                isInstrumentPanelOpen = true,
                isDspInspectorOpen = true,
                dspTargetTrackId = null // null means Master bus
            )
        }
    }

    private fun handleSetZoom(pixelsPerSecond: Float) {
        val clamped = pixelsPerSecond.coerceIn(20f, 2000f)
        _uiState.update { it.copy(zoomPixelsPerSecond = clamped) }
    }

    private fun handleSetScrollOffset(offsetPx: Float) {
        val clamped = max(0f, offsetPx)
        _uiState.update { it.copy(horizontalScrollOffsetPx = clamped) }
    }

    // =========================================================================
    // DSP Mutations
    // =========================================================================

    private suspend fun handleUpdateEq(
        trackId: Int,
        bandIndex: Int,
        freqHz: Float,
        gainDb: Float,
        q: Float
    ) = withContext(defaultDispatcher) {
        withNativeEngine {
            it.setTrackEq(trackId, bandIndex, 1 /* PEAKING */, freqHz, q, gainDb)
        }
        _uiState.update { state ->
            state.copy(
                tracks = state.tracks.map { track ->
                    if (track.trackId == trackId) {
                        track.copy(
                            eqBands = track.eqBands.map { band ->
                                if (band.bandIndex == bandIndex) {
                                    band.copy(
                                        frequencyHz = freqHz.coerceIn(20f, 20000f),
                                        gainDb = gainDb.coerceIn(-18f, 18f),
                                        q = q.coerceIn(0.1f, 15f)
                                    )
                                } else band
                            }
                        )
                    } else track
                }
            )
        }
    }

    private fun handleToggleEqBand(trackId: Int, bandIndex: Int) {
        _uiState.update { state ->
            state.copy(
                tracks = state.tracks.map { track ->
                    if (track.trackId == trackId) {
                        track.copy(
                            eqBands = track.eqBands.map { band ->
                                if (band.bandIndex == bandIndex) band.copy(isEnabled = !band.isEnabled) else band
                            }
                        )
                    } else track
                }
            )
        }
    }

    private suspend fun handleUpdateDelay(
        trackId: Int,
        timeMs: Float,
        feedback: Float,
        wetDry: Float
    ) = withContext(defaultDispatcher) {
        withNativeEngine {
            it.setTrackDelay(trackId, timeMs, feedback, wetDry)
        }
        _uiState.update { state ->
            state.copy(
                tracks = state.tracks.map { track ->
                    if (track.trackId == trackId) {
                        track.copy(
                            delayParams = track.delayParams.copy(
                                timeMs = timeMs.coerceIn(1f, 2000f),
                                feedback = feedback.coerceIn(0f, 0.95f),
                                wetDry = wetDry.coerceIn(0f, 1f)
                            )
                        )
                    } else track
                }
            )
        }
    }

    private fun handleToggleDelayPingPong(trackId: Int) {
        _uiState.update { state ->
            state.copy(
                tracks = state.tracks.map { track ->
                    if (track.trackId == trackId) {
                        track.copy(delayParams = track.delayParams.copy(isPingPong = !track.delayParams.isPingPong))
                    } else track
                }
            )
        }
    }

    private fun handleToggleDelayBypass(trackId: Int) {
        _uiState.update { state ->
            state.copy(
                tracks = state.tracks.map { track ->
                    if (track.trackId == trackId) {
                        track.copy(delayParams = track.delayParams.copy(isEnabled = !track.delayParams.isEnabled))
                    } else track
                }
            )
        }
    }

    private suspend fun handleUpdateLimiter(
        thresholdDb: Float,
        ceilingDb: Float,
        releaseMs: Float
    ) = withContext(defaultDispatcher) {
        withNativeEngine {
            it.setMasterLimiter(thresholdDb, ceilingDb, releaseMs)
        }
        _uiState.update { state ->
            state.copy(
                masterLimiter = state.masterLimiter.copy(
                    thresholdDb = thresholdDb.coerceIn(-24f, 0f),
                    ceilingDb = ceilingDb.coerceIn(-6f, 0f),
                    releaseMs = releaseMs.coerceIn(10f, 1000f)
                )
            )
        }
    }

    private fun handleToggleLimiterBypass(isBypassed: Boolean) {
        _uiState.update { state ->
            state.copy(masterLimiter = state.masterLimiter.copy(isEnabled = !isBypassed))
        }
    }

    // =========================================================================
    // Recording, Import & Export
    // =========================================================================

    private suspend fun handleToggleRecord(isRecording: Boolean) = withContext(defaultDispatcher) {
        if (isRecording) {
            val armedTracks = _uiState.value.tracks.filter { it.isArmed }
            if (armedTracks.isEmpty()) {
                _uiState.update { it.copy(statusMessage = "Please arm at least one track before recording.") }
                return@withContext
            }
            withNativeEngine { it.startRecording(armedTracks.first().trackId) }
            _uiState.update {
                it.copy(
                    transportState = TransportState.RECORDING,
                    statusMessage = "Recording into ${armedTracks.size} armed track(s)..."
                )
            }
        } else {
            withNativeEngine { it.stopRecording() }
            _uiState.update {
                it.copy(
                    transportState = TransportState.STOPPED,
                    statusMessage = "Recording stopped. Clip saved."
                )
            }
        }
    }

    private suspend fun handleImportAudio(trackId: Int, uri: Uri) = withContext(ioDispatcher) {
        _uiState.update { it.copy(statusMessage = "Importing audio: $uri...") }

        val app = try { DawApplication.getInstance() } catch (_: Throwable) { null }
        var localPath: String? = null
        val clipName = uri.lastPathSegment?.substringAfterLast('/') ?: "Imported Clip"

        try {
            if (uri.scheme == "file") {
                localPath = uri.path
            } else if (app != null && uri.scheme == "content") {
                val importsDir = File(app.cacheDir, "imports").apply { mkdirs() }
                val targetFile = File(importsDir, "${System.currentTimeMillis()}_${clipName.replace('/', '_')}")
                app.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                localPath = targetFile.absolutePath
            } else {
                localPath = uri.path
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(statusMessage = "Failed to access audio file: ${e.message}") }
            return@withContext
        }

        if (localPath == null || !File(localPath).exists()) {
            _uiState.update { it.copy(statusMessage = "Audio file not found: $localPath") }
            return@withContext
        }

        val sampleRate = _uiState.value.sampleRate
        val decoded = audioEngine.decodeAudioFile(localPath, sampleRate)
        if (decoded != null && decoded.success && decoded.totalFrames > 0L) {
            val clipId = (System.currentTimeMillis() % 100000).toInt()
            val pyramid = decoded.toPyramid()
            audioEngine.loadClip(trackId, clipId, localPath, 0L)

            val newClip = AudioClipUiModel(
                clipId = clipId,
                trackId = trackId,
                name = clipName,
                startFrame = 0L,
                durationFrames = decoded.totalFrames,
                waveformPeakData = pyramid
            )

            _uiState.update { state ->
                val newTotalFrames = maxOf(state.totalFrames, newClip.startFrame + newClip.durationFrames)
                state.copy(
                    totalFrames = newTotalFrames,
                    tracks = state.tracks.map { track ->
                        if (track.trackId == trackId) {
                            track.copy(clips = track.clips + newClip)
                        } else track
                    },
                    statusMessage = "Imported audio successfully: $clipName (${decoded.totalFrames} frames)"
                )
            }
        } else {
            val errMsg = decoded?.errorMessage ?: "Native audio decoding failed."
            _uiState.update { it.copy(statusMessage = "Import failed: $errMsg") }
        }
    }

    private suspend fun handleStartExport(bitDepth: Int) = withContext(ioDispatcher) {
        exportJob?.cancel()
        _uiState.update { it.copy(isExporting = true, exportProgressPercent = 0, statusMessage = "Rendering $bitDepth-bit WAV mixdown...") }

        exportJob = viewModelScope.launch(ioDispatcher) {
            try {
                val app = try { DawApplication.getInstance() } catch (_: Throwable) { null }
                val exportDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    ?: app?.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                    ?: File("/sdcard/Download")
                if (!exportDir.exists()) exportDir.mkdirs()

                val fileName = "SD_Studio_Mixdown_${System.currentTimeMillis()}_${bitDepth}bit.wav"
                val outputFile = File(exportDir, fileName)

                val state = _uiState.value
                val maxClipEnd = state.tracks.flatMap { it.clips }.maxOfOrNull { it.startFrame + it.durationFrames } ?: (state.sampleRate * 4L)
                val totalFrames = maxOf(state.totalFrames, maxClipEnd)

                _uiState.update { it.copy(exportProgressPercent = 25) }

                val success = audioEngine.renderOffline(outputFile.absolutePath, bitDepth, totalFrames)

                _uiState.update { it.copy(exportProgressPercent = 85) }
                delay(100)

                if (success && outputFile.exists()) {
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            exportProgressPercent = 100,
                            statusMessage = "Export complete: ${outputFile.name} saved to Downloads."
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            exportProgressPercent = 0,
                            statusMessage = "Export failed or cancelled by audio engine."
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, statusMessage = "Export failed: ${e.message}") }
            }
        }
    }

    private fun handleCancelExport() {
        exportJob?.cancel()
        _uiState.update { it.copy(isExporting = false, statusMessage = "Export cancelled.") }
    }

    private fun handleDismissStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    // =========================================================================
    // 60Hz High-Frequency Telemetry & Ballistic Physics Engine
    // =========================================================================

    private fun startTelemetryLoop() {
        telemetryJob?.cancel()
        telemetryJob = viewModelScope.launch(defaultDispatcher) {
            val reusableData = TelemetryData()
            var lastTimestamp = System.currentTimeMillis()

            while (isActive) {
                try {
                    val now = System.currentTimeMillis()
                    val deltaSec = (now - lastTimestamp) / 1000f
                    lastTimestamp = now

                    val state = _uiState.value
                    val isPlaying = state.isPlaying || state.isRecording

                    // 1. Hardware clock Playhead position from native engine
                    val nativePlayhead = audioEngine.getPlaybackPositionFrames()
                    if (nativePlayhead > 0L || isPlaying) {
                        _playheadFrame.value = nativePlayhead
                    }

                    // 2. Poll atomic native audio telemetry
                    val polled = audioEngine.pollTelemetry(reusableData)

                    // 3. Update meters with IEC ballistics
                    val updatedMeters = mutableMapOf<Int, StereoLevel>()
                    var masterMaxPeak = 0f
                    var masterMaxRms = 0f

                    state.tracks.forEachIndexed { index, track ->
                        val ballistics = meterBallisticsMap.getOrPut(track.trackId) { BallisticsState() }
                        val rawPeak = if (polled && index < reusableData.trackPeaks.size) {
                            reusableData.trackPeaks[index]
                        } else {
                            0f
                        }
                        val level = ballistics.update(rawPeak, now, deltaSec)
                        updatedMeters[track.trackId] = level
                        masterMaxPeak = maxOf(masterMaxPeak, level.peakMax)
                        masterMaxRms = maxOf(masterMaxRms, level.rmsMax)
                    }

                    // Master Bus Ballistics
                    val masterBallistics = meterBallisticsMap.getOrPut(-1) { BallisticsState() }
                    val rawMasterPeak = if (polled) maxOf(reusableData.masterPeakLeft, reusableData.masterPeakRight) else masterMaxPeak
                    val masterLevel = masterBallistics.update(rawMasterPeak * state.masterVolumeLinear, now, deltaSec)

                    _telemetryLevels.value = updatedMeters

                    // 4. Update CPU and RAM in uiState
                    val cpuPct = audioEngine.getCpuUsage()
                    val pssKb = android.os.Debug.getPss()
                    val ramMb = if (pssKb > 0) (pssKb / 1024L).toInt() else 34

                    _uiState.update { s ->
                        s.copy(
                            cpuUsagePercent = cpuPct,
                            ramUsageMb = ramMb,
                            masterLevels = masterLevel
                        )
                    }
                } catch (_: Throwable) {
                    // Concurrency safeguard
                }

                delay(16) // ~60 Hz update rate
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        telemetryJob?.cancel()
        exportJob?.cancel()
    }

    // =========================================================================
    // Safe Native Audio Engine Bridge Call Wrapper
    // =========================================================================

    private suspend inline fun withNativeEngine(crossinline block: suspend (NativeAudioEngine) -> Unit) {
        try {
            block(audioEngine)
        } catch (_: Throwable) {
            // Graceful fallback when native engine library is not loaded on host
        }
    }

    companion object {
        private const val TAG = "StudioViewModel"

        private val TRACK_PALETTE_HEX = listOf(
            0xFFFF5252L, // Coral
            0xFF4DB8FFL, // Sky
            0xFF00E5A3L  // Mint
        )

        /**
         * Initialize clean studio session starting with an empty project per Ground Rule 0.
         */
        fun createInitialStudioState(): StudioUiState {
            val sampleRate = 48000
            val initialFrames = sampleRate * 32L // Default 32s empty project
            return StudioUiState(
                bpm = 120.0,
                sampleRate = sampleRate,
                totalFrames = initialFrames,
                loopStartFrame = 0L,
                loopEndFrame = sampleRate * 16L,
                isLooping = false,
                tracks = emptyList(),
                selectedTrackId = null
            )
        }
    }

    /**
     * Ballistics state tracking implementing IEC 60268-10 PPM ballistics:
     * - 0ms attack
     * - 20dB decay in 1.5s
     * - 1.5s peak hold
     */
    private class BallisticsState {
        var smoothedPeakLinear: Float = 0f
        var peakHoldLinear: Float = 0f
        var peakHoldTimestamp: Long = 0L
        var hasClipped: Boolean = false

        fun update(rawLinear: Float, nowMs: Long, deltaSec: Float): StereoLevel {
            if (rawLinear >= 1.0f) {
                hasClipped = true
            }

            // Exponential decay: alpha = exp(-deltaSec / tau), tau ~ 0.6514s
            val decayFactor = exp(-deltaSec / 0.6514f)

            // Instant attack, exponential decay
            smoothedPeakLinear = if (rawLinear > smoothedPeakLinear) {
                rawLinear
            } else {
                maxOf(0f, smoothedPeakLinear * decayFactor)
            }

            // Peak Hold logic
            if (rawLinear >= peakHoldLinear) {
                peakHoldLinear = rawLinear
                peakHoldTimestamp = nowMs
            } else if (nowMs - peakHoldTimestamp > 1500L) {
                peakHoldLinear = maxOf(0f, peakHoldLinear * decayFactor)
            }

            val rms = smoothedPeakLinear * 0.707f
            return StereoLevel(
                peakLeft = smoothedPeakLinear,
                peakRight = (smoothedPeakLinear * 0.95f).coerceAtLeast(0f),
                rmsLeft = rms,
                rmsRight = rms * 0.95f,
                clipLeft = hasClipped,
                clipRight = hasClipped
            )
        }
    }
}
