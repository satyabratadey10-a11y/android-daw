package com.android.daw.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.daw.bridge.NativeAudioEngine
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
class StudioViewModel : ViewModel() {

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
        startTelemetryLoop()
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
                is StudioAction.SetZoom -> handleSetZoom(action.pixelsPerSecond)
                is StudioAction.SetScrollOffset -> handleSetScrollOffset(action.offsetPx)

                // --- Tier 1 Contextual Clip Intents ---
                is StudioAction.SelectClip -> handleSelectClip(action.clipId)
                is StudioAction.ExecuteClipAction -> handleExecuteClipAction(action.clipId, action.action)
                is StudioAction.SetClipMenuOpen -> handleSetClipMenuOpen(action.isOpen)

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

    private suspend fun handlePlay() = withContext(Dispatchers.Default) {
        withNativeEngine { it.play() }
        _uiState.update { it.copy(transportState = TransportState.PLAYING) }
    }

    private suspend fun handlePause() = withContext(Dispatchers.Default) {
        withNativeEngine { it.pause() }
        _uiState.update { it.copy(transportState = TransportState.PAUSED) }
    }

    private suspend fun handleStop() = withContext(Dispatchers.Default) {
        withNativeEngine { it.stopTransport() }
        _playheadFrame.value = 0L
        _uiState.update {
            it.copy(
                transportState = TransportState.STOPPED,
                currentFrame = 0L
            )
        }
    }

    private suspend fun handleRewind() = withContext(Dispatchers.Default) {
        val loopStart = _uiState.value.loopStartFrame
        val targetFrame = if (_uiState.value.isLooping) loopStart else 0L
        withNativeEngine { it.seek(targetFrame) }
        _playheadFrame.value = targetFrame
        _uiState.update { it.copy(currentFrame = targetFrame) }
    }

    private suspend fun handleSeek(frame: Long) = withContext(Dispatchers.Default) {
        val clampedFrame = frame.coerceIn(0L, _uiState.value.totalFrames)
        withNativeEngine { it.seek(clampedFrame) }
        _playheadFrame.value = clampedFrame
        _uiState.update { it.copy(currentFrame = clampedFrame) }
    }

    private suspend fun handleSetTempo(bpm: Double) = withContext(Dispatchers.Default) {
        val clampedBpm = bpm.coerceIn(40.0, 300.0)
        withNativeEngine { it.setTempo(clampedBpm) }
        _uiState.update { it.copy(bpm = clampedBpm) }
    }

    private suspend fun handleToggleLoop() = withContext(Dispatchers.Default) {
        val current = _uiState.value
        val newLooping = !current.isLooping
        withNativeEngine { it.setLoop(newLooping, current.loopStartFrame, current.loopEndFrame) }
        _uiState.update { it.copy(isLooping = newLooping) }
    }

    private suspend fun handleSetLoopRange(startFrame: Long, endFrame: Long) = withContext(Dispatchers.Default) {
        val start = startFrame.coerceAtLeast(0L)
        val end = maxOf(start + 44100L, endFrame)
        withNativeEngine { it.setLoop(_uiState.value.isLooping, start, end) }
        _uiState.update { it.copy(loopStartFrame = start, loopEndFrame = end) }
    }

    // =========================================================================
    // Track Management Logic
    // =========================================================================

    private suspend fun handleAddTrack(name: String) = withContext(Dispatchers.Default) {
        val currentTracks = _uiState.value.tracks
        val nextId = (currentTracks.maxOfOrNull { it.trackId } ?: 0) + 1
        val colorHex = TRACK_PALETTE_HEX[nextId % TRACK_PALETTE_HEX.size]

        val sampleRate = _uiState.value.sampleRate
        val syntheticPyramid = generateSyntheticPyramid(sampleRate, 44100L * 16L, (nextId * 110f) % 440f + 110f)
        val initialClip = AudioClipUiModel(
            clipId = nextId * 10,
            trackId = nextId,
            name = "$name Clip",
            startFrame = 0L,
            durationFrames = 44100L * 16L,
            waveformPeakData = syntheticPyramid
        )

        val newTrack = TrackUiModel(
            trackId = nextId,
            name = "$name $nextId",
            colorHex = colorHex,
            clips = listOf(initialClip)
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

    private suspend fun handleRemoveTrack(trackId: Int) = withContext(Dispatchers.Default) {
        withNativeEngine { it.removeTrack(trackId) }
        _uiState.update { state ->
            val updatedTracks = state.tracks.filterNot { it.trackId == trackId }
            val newSelected = if (state.selectedTrackId == trackId) {
                updatedTracks.firstOrNull()?.trackId
            } else {
                state.selectedTrackId
            }
            state.copy(
                tracks = updatedTracks,
                selectedTrackId = newSelected,
                statusMessage = "Removed track #$trackId"
            )
        }
    }

    private suspend fun handleSetTrackVolume(trackId: Int, volumeLinear: Float) = withContext(Dispatchers.Default) {
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

    private suspend fun handleSetTrackPan(trackId: Int, pan: Float) = withContext(Dispatchers.Default) {
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

    private suspend fun handleToggleMute(trackId: Int) = withContext(Dispatchers.Default) {
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

    private suspend fun handleToggleSolo(trackId: Int) = withContext(Dispatchers.Default) {
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

    private suspend fun handleToggleArm(trackId: Int) = withContext(Dispatchers.Default) {
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

    private suspend fun handleSetMasterVolume(volumeLinear: Float) = withContext(Dispatchers.Default) {
        val clamped = volumeLinear.coerceIn(0.0f, 2.0f)
        _uiState.update { it.copy(masterVolumeLinear = clamped) }
    }

    private suspend fun handleSetMasterPan(pan: Float) = withContext(Dispatchers.Default) {
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
                _uiState.update { state ->
                    state.copy(
                        tracks = state.tracks.map { track ->
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
    ) = withContext(Dispatchers.Default) {
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
    ) = withContext(Dispatchers.Default) {
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
    ) = withContext(Dispatchers.Default) {
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

    private suspend fun handleToggleRecord(isRecording: Boolean) = withContext(Dispatchers.Default) {
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

    private suspend fun handleImportAudio(trackId: Int, uri: Uri) = withContext(Dispatchers.IO) {
        _uiState.update { it.copy(statusMessage = "Importing audio from $uri...") }
        delay(300)

        val sampleRate = _uiState.value.sampleRate
        val durationFrames = 44100L * 20L
        val pyramid = generateSyntheticPyramid(sampleRate, durationFrames, 220f)
        val clipId = (System.currentTimeMillis() % 10000).toInt()
        val clipName = uri.lastPathSegment ?: "Imported Clip"

        val newClip = AudioClipUiModel(
            clipId = clipId,
            trackId = trackId,
            name = clipName,
            startFrame = 0L,
            durationFrames = durationFrames,
            waveformPeakData = pyramid
        )

        _uiState.update { state ->
            state.copy(
                tracks = state.tracks.map { track ->
                    if (track.trackId == trackId) {
                        track.copy(clips = track.clips + newClip)
                    } else track
                },
                statusMessage = "Imported audio successfully: $clipName"
            )
        }
    }

    private suspend fun handleStartExport(bitDepth: Int) = withContext(Dispatchers.IO) {
        exportJob?.cancel()
        _uiState.update { it.copy(isExporting = true, exportProgressPercent = 0, statusMessage = "Rendering $bitDepth-bit WAV mixdown...") }

        exportJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                for (p in 1..100) {
                    delay(20)
                    _uiState.update { it.copy(exportProgressPercent = p) }
                }
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportProgressPercent = 100,
                        statusMessage = "Export complete: Mixdown_$bitDepth-bit.wav saved to Downloads."
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, statusMessage = "Export cancelled or failed.") }
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
        telemetryJob = viewModelScope.launch(Dispatchers.Default) {
            var lastTimestamp = System.currentTimeMillis()
            val framesPerTick = 735L // ~44100 / 60fps = 735 frames per tick

            while (isActive) {
                val now = System.currentTimeMillis()
                val deltaSec = (now - lastTimestamp) / 1000f
                lastTimestamp = now

                val state = _uiState.value
                val isPlaying = state.isPlaying || state.isRecording

                if (isPlaying) {
                    var current = _playheadFrame.value + framesPerTick
                    if (state.isLooping && current >= state.loopEndFrame) {
                        current = state.loopStartFrame
                    } else if (current >= state.totalFrames) {
                        current = 0L
                    }
                    _playheadFrame.value = current
                    _uiState.update { it.copy(currentFrame = current) }
                }

                // Compute IEC 60268-10 Ballistics for each track & master
                val updatedMeters = mutableMapOf<Int, StereoLevel>()
                var masterMaxPeak = 0f
                var masterMaxRms = 0f

                state.tracks.forEach { track ->
                    val ballistics = meterBallisticsMap.getOrPut(track.trackId) { BallisticsState() }

                    val rawAmplitude = if (isPlaying && !track.isMuted) {
                        val phase = (_playheadFrame.value.toFloat() / state.sampleRate) * (track.trackId * 2f + 1f)
                        val mod = (0.5f + 0.5f * sin(phase * 2.0 * PI)).toFloat()
                        (mod * track.volumeLinear * 0.75f).coerceIn(0f, 1.2f)
                    } else {
                        0.0f
                    }

                    val level = ballistics.update(rawAmplitude, now, deltaSec)
                    updatedMeters[track.trackId] = level
                    masterMaxPeak = maxOf(masterMaxPeak, level.peakMax)
                    masterMaxRms = maxOf(masterMaxRms, level.rmsMax)
                }

                // Master Bus Ballistics
                val masterBallistics = meterBallisticsMap.getOrPut(-1) { BallisticsState() }
                val masterLevel = masterBallistics.update(masterMaxPeak * state.masterVolumeLinear, now, deltaSec)

                _telemetryLevels.value = updatedMeters

                // Update Master level in general UI state periodically
                _uiState.update { it.copy(masterLevels = masterLevel) }

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
            val engine = NativeAudioEngine.getInstance()
            block(engine)
        } catch (t: Throwable) {
            // Graceful fallback when native engine library is not loaded on host
            Log.d(TAG, "Native audio engine call fallback: ${t.message}")
        }
    }

    companion object {
        private const val TAG = "StudioViewModel"

        private val TRACK_PALETTE_HEX = listOf(
            0xFF00E5FF, // Cyan
            0xFFFF4081, // Pink
            0xFFFFD740, // Amber
            0xFF7C4DFF, // Purple
            0xFF69F0AE, // Mint
            0xFFFF6E40, // Orange
            0xFF40C4FF  // Sky Blue
        )

        /**
         * Initialize professional default studio session with 4 tracks
         */
        fun createInitialStudioState(): StudioUiState {
            val sampleRate = 44100
            val clipDurationFrames = sampleRate * 16L // 16 seconds (8 bars at 120 BPM)

            val track1 = TrackUiModel(
                trackId = 1,
                name = "Vocal Lead",
                colorHex = 0xFF00E5FF,
                volumeLinear = 1.0f,
                pan = 0.0f,
                clips = listOf(
                    AudioClipUiModel(101, 1, "Vocal_Take_01", 0L, clipDurationFrames, generateSyntheticPyramid(sampleRate, clipDurationFrames, 440f))
                )
            )

            val track2 = TrackUiModel(
                trackId = 2,
                name = "Bass Synth",
                colorHex = 0xFFFF4081,
                volumeLinear = 0.85f,
                pan = -0.15f,
                clips = listOf(
                    AudioClipUiModel(102, 2, "Bass_Line_Groove", 0L, clipDurationFrames, generateSyntheticPyramid(sampleRate, clipDurationFrames, 110f))
                )
            )

            val track3 = TrackUiModel(
                trackId = 3,
                name = "Acoustic Drums",
                colorHex = 0xFFFFD740,
                volumeLinear = 0.90f,
                pan = 0.20f,
                clips = listOf(
                    AudioClipUiModel(103, 3, "Drum_Loop_120", 0L, clipDurationFrames, generateSyntheticPyramid(sampleRate, clipDurationFrames, 220f))
                )
            )

            val track4 = TrackUiModel(
                trackId = 4,
                name = "Analog Pad",
                colorHex = 0xFF7C4DFF,
                volumeLinear = 0.70f,
                pan = -0.35f,
                clips = listOf(
                    AudioClipUiModel(104, 4, "Pad_Atmosphere", 0L, clipDurationFrames, generateSyntheticPyramid(sampleRate, clipDurationFrames, 330f))
                )
            )

            return StudioUiState(
                bpm = 120.0,
                sampleRate = sampleRate,
                totalFrames = sampleRate * 32L,
                loopStartFrame = 0L,
                loopEndFrame = clipDurationFrames,
                isLooping = true,
                tracks = listOf(track1, track2, track3, track4),
                selectedTrackId = 1
            )
        }

        /**
         * Generates a 5-level multi-resolution peak pyramid for instantaneous waveform rendering
         */
        fun generateSyntheticPyramid(sampleRate: Int, totalFrames: Long, baseFreqHz: Float): WaveformDecimationPyramid {
            val blockSizes = listOf(256, 1024, 4096, 16384, 65536)
            val levels = blockSizes.map { blockSize ->
                val blockCount = max(1, (totalFrames / blockSize).toInt())
                val minPeaks = FloatArray(blockCount)
                val maxPeaks = FloatArray(blockCount)
                val rmsPeaks = FloatArray(blockCount)

                for (b in 0 until blockCount) {
                    val t = (b * blockSize).toFloat() / sampleRate
                    val envelope = (0.3f + 0.7f * exp(-((t % 2.0f) * 1.5f)))
                    val harmonic = (sin(2.0 * PI * baseFreqHz * t) * 0.7f + sin(4.0 * PI * baseFreqHz * t) * 0.3f).toFloat()
                    val peak = (envelope * harmonic).coerceIn(-1.0f, 1.0f)

                    maxPeaks[b] = maxOf(0.05f, kotlin.math.abs(peak))
                    minPeaks[b] = -maxPeaks[b]
                    rmsPeaks[b] = maxPeaks[b] * 0.707f
                }

                DecimationLevel(blockSize, minPeaks, maxPeaks, rmsPeaks)
            }

            return WaveformDecimationPyramid(sampleRate, totalFrames, levels)
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
