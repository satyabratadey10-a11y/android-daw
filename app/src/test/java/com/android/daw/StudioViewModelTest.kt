package com.android.daw

import com.android.daw.bridge.NativeAudioEngine
import com.android.daw.viewmodel.AudioClipUiModel
import com.android.daw.viewmodel.StudioAction
import com.android.daw.viewmodel.StudioViewModel
import com.android.daw.viewmodel.TransportState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite for [StudioViewModel].
 * Validates MVI unidirectional data flow (UDF), [StudioAction] intent dispatching,
 * [StudioUiState] state transitions, track CRUD, solo/mute/arm logic, clip manipulation, and DSP mutations.
 * Adheres strictly to Ground Rule 0: Clean project starting with 0 tracks and no fake data.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StudioViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: StudioViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val testEngine = NativeAudioEngine(
            defaultDispatcher = testDispatcher,
            ioDispatcher = testDispatcher
        )
        viewModel = StudioViewModel(
            defaultDispatcher = testDispatcher,
            ioDispatcher = testDispatcher,
            enableTelemetryLoop = false,
            audioEngine = testEngine
        )
    }

    @After
    fun tearDown() {
        try {
            val onClearedMethod = StudioViewModel::class.java.getDeclaredMethod("onCleared")
            onClearedMethod.isAccessible = true
            onClearedMethod.invoke(viewModel)
        } catch (_: Exception) {
            // Ignore reflection errors
        }
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState() = runTest(testDispatcher) {
        val state = viewModel.uiState.value
        assertEquals(TransportState.STOPPED, state.transportState)
        assertEquals(0L, state.currentFrame)
        assertEquals(120.0, state.bpm, 1e-4)
        assertEquals(0, state.tracks.size) // Ground Rule 0: Starts with empty project
        assertNull(state.selectedTrackId)
        assertFalse(state.isLooping)
        assertFalse(state.isMixerViewActive)
        assertFalse(state.isDspInspectorOpen)
    }

    @Test
    fun testTransportLifecycleActions() = runTest(testDispatcher) {
        // Play
        viewModel.onAction(StudioAction.Play)
        advanceUntilIdle()
        assertEquals(TransportState.PLAYING, viewModel.uiState.value.transportState)
        assertTrue(viewModel.uiState.value.isPlaying)

        // Pause
        viewModel.onAction(StudioAction.Pause)
        advanceUntilIdle()
        assertEquals(TransportState.PAUSED, viewModel.uiState.value.transportState)
        assertFalse(viewModel.uiState.value.isPlaying)

        // Seek
        val seekTarget = 44100L
        viewModel.onAction(StudioAction.Seek(seekTarget))
        advanceUntilIdle()
        assertEquals(seekTarget, viewModel.uiState.value.currentFrame)

        // Stop
        viewModel.onAction(StudioAction.Stop)
        advanceUntilIdle()
        assertEquals(TransportState.STOPPED, viewModel.uiState.value.transportState)
        assertEquals(0L, viewModel.uiState.value.currentFrame)

        // Tempo Change
        viewModel.onAction(StudioAction.SetTempo(144.0))
        advanceUntilIdle()
        assertEquals(144.0, viewModel.uiState.value.bpm, 1e-4)

        // Toggle Loop
        viewModel.onAction(StudioAction.ToggleLoop)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLooping)

        // Set Loop Range
        viewModel.onAction(StudioAction.SetLoopRange(22050L, 88200L))
        advanceUntilIdle()
        assertEquals(22050L, viewModel.uiState.value.loopStartFrame)
        assertEquals(88200L, viewModel.uiState.value.loopEndFrame)
    }

    @Test
    fun testTrackCrudAndSelection() = runTest(testDispatcher) {
        val initialCount = viewModel.uiState.value.tracks.size

        // Add track
        viewModel.onAction(StudioAction.AddTrack("Guitar"))
        advanceUntilIdle()
        val afterAdd = viewModel.uiState.value
        assertEquals(initialCount + 1, afterAdd.tracks.size)
        val addedTrack = afterAdd.tracks.last()
        assertTrue(addedTrack.name.startsWith("Guitar"))
        assertEquals(addedTrack.trackId, afterAdd.selectedTrackId)

        // Add another track
        viewModel.onAction(StudioAction.AddTrack("Bass"))
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.tracks.size)

        // Select first track
        viewModel.onAction(StudioAction.SelectTrack(addedTrack.trackId))
        advanceUntilIdle()
        assertEquals(addedTrack.trackId, viewModel.uiState.value.selectedTrackId)

        // Remove track
        val trackToRemove = addedTrack.trackId
        viewModel.onAction(StudioAction.RemoveTrack(trackToRemove))
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.tracks.size)
        assertNull(viewModel.uiState.value.tracks.firstOrNull { it.trackId == trackToRemove })
    }

    @Test
    fun testTrackMixingControls() = runTest(testDispatcher) {
        viewModel.onAction(StudioAction.AddTrack("Audio Track"))
        advanceUntilIdle()
        val targetId = viewModel.uiState.value.tracks.first().trackId

        // Volume
        viewModel.onAction(StudioAction.SetTrackVolume(targetId, 0.72f))
        advanceUntilIdle()
        val trackVol = viewModel.uiState.value.tracks.first { it.trackId == targetId }
        assertEquals(0.72f, trackVol.volumeLinear, 1e-4f)

        // Pan
        viewModel.onAction(StudioAction.SetTrackPan(targetId, -0.45f))
        advanceUntilIdle()
        val trackPan = viewModel.uiState.value.tracks.first { it.trackId == targetId }
        assertEquals(-0.45f, trackPan.pan, 1e-4f)

        // Mute
        assertFalse(trackPan.isMuted)
        viewModel.onAction(StudioAction.ToggleMute(targetId))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.tracks.first { it.trackId == targetId }.isMuted)

        // Solo
        assertFalse(trackPan.isSoloed)
        viewModel.onAction(StudioAction.ToggleSolo(targetId))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.tracks.first { it.trackId == targetId }.isSoloed)

        // Arm
        assertFalse(trackPan.isArmed)
        viewModel.onAction(StudioAction.ToggleArm(targetId))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.tracks.first { it.trackId == targetId }.isArmed)
    }

    @Test
    fun testMasterBusAndViewportToggles() = runTest(testDispatcher) {
        // Master Volume
        viewModel.onAction(StudioAction.SetMasterVolume(0.88f))
        advanceUntilIdle()
        assertEquals(0.88f, viewModel.uiState.value.masterVolumeLinear, 1e-4f)

        // Master Pan
        viewModel.onAction(StudioAction.SetMasterPan(0.15f))
        advanceUntilIdle()
        assertEquals(0.15f, viewModel.uiState.value.masterPan, 1e-4f)

        // Mixer view toggle
        viewModel.onAction(StudioAction.ToggleMixerView)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isMixerViewActive)

        // DSP inspector toggle
        viewModel.onAction(StudioAction.ToggleDspInspector)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isDspInspectorOpen)

        // Open Track DSP
        viewModel.onAction(StudioAction.OpenTrackDsp(3))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isDspInspectorOpen)
        assertEquals(3, viewModel.uiState.value.dspTargetTrackId)

        // Open Master DSP
        viewModel.onAction(StudioAction.OpenMasterDsp)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isDspInspectorOpen)
        assertNull(viewModel.uiState.value.dspTargetTrackId)

        // Zoom & Scroll
        viewModel.onAction(StudioAction.SetZoom(300f))
        viewModel.onAction(StudioAction.SetScrollOffset(150f))
        advanceUntilIdle()
        assertEquals(300f, viewModel.uiState.value.zoomPixelsPerSecond, 1e-4f)
        assertEquals(150f, viewModel.uiState.value.horizontalScrollOffsetPx, 1e-4f)
    }

    @Test
    fun testDspEffectMutations() = runTest(testDispatcher) {
        viewModel.onAction(StudioAction.AddTrack("Audio Track"))
        advanceUntilIdle()
        val trackId = viewModel.uiState.value.tracks.first().trackId

        // Update EQ
        viewModel.onAction(StudioAction.UpdateEq(trackId, bandIndex = 1, freqHz = 550f, gainDb = 4.5f, q = 1.8f))
        advanceUntilIdle()
        val band = viewModel.uiState.value.tracks.first { it.trackId == trackId }.eqBands.first { it.bandIndex == 1 }
        assertEquals(550f, band.frequencyHz, 1e-4f)
        assertEquals(4.5f, band.gainDb, 1e-4f)
        assertEquals(1.8f, band.q, 1e-4f)

        // Toggle EQ Band
        val initialEnabled = band.isEnabled
        viewModel.onAction(StudioAction.ToggleEqBand(trackId, bandIndex = 1))
        advanceUntilIdle()
        val toggledBand = viewModel.uiState.value.tracks.first { it.trackId == trackId }.eqBands.first { it.bandIndex == 1 }
        assertEquals(!initialEnabled, toggledBand.isEnabled)

        // Update Delay
        viewModel.onAction(StudioAction.UpdateDelay(trackId, timeMs = 320f, feedback = 0.45f, wetDry = 0.28f))
        advanceUntilIdle()
        val delay = viewModel.uiState.value.tracks.first { it.trackId == trackId }.delayParams
        assertEquals(320f, delay.timeMs, 1e-4f)
        assertEquals(0.45f, delay.feedback, 1e-4f)
        assertEquals(0.28f, delay.wetDry, 1e-4f)

        // Master Limiter
        viewModel.onAction(StudioAction.UpdateLimiter(thresholdDb = -1.5f, ceilingDb = -0.2f, releaseMs = 75f))
        advanceUntilIdle()
        val limiter = viewModel.uiState.value.masterLimiter
        assertEquals(-1.5f, limiter.thresholdDb, 1e-4f)
        assertEquals(-0.2f, limiter.ceilingDb, 1e-4f)
        assertEquals(75f, limiter.releaseMs, 1e-4f)
    }

    @Test
    fun testRecordingArmGuardAndStatusMessage() = runTest(testDispatcher) {
        viewModel.onAction(StudioAction.AddTrack("Audio Track"))
        advanceUntilIdle()

        // Ensure all tracks unarmed first
        viewModel.uiState.value.tracks.forEach { track ->
            if (track.isArmed) {
                viewModel.onAction(StudioAction.ToggleArm(track.trackId))
            }
        }
        advanceUntilIdle()

        viewModel.onAction(StudioAction.ToggleRecord(true))
        advanceUntilIdle()

        // Should not enter recording state without an armed track
        assertEquals(TransportState.STOPPED, viewModel.uiState.value.transportState)
        assertNotNull(viewModel.uiState.value.statusMessage)
        assertTrue(viewModel.uiState.value.statusMessage!!.contains("arm at least one track", ignoreCase = true))

        // Dismiss status message
        viewModel.onAction(StudioAction.DismissStatusMessage)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.statusMessage)

        // Arm track and record
        val trackId = viewModel.uiState.value.tracks.first().trackId
        viewModel.onAction(StudioAction.ToggleArm(trackId))
        advanceUntilIdle()
        viewModel.onAction(StudioAction.ToggleRecord(true))
        advanceUntilIdle()
        assertEquals(TransportState.RECORDING, viewModel.uiState.value.transportState)
        assertTrue(viewModel.uiState.value.isRecording)

        // Stop recording
        viewModel.onAction(StudioAction.ToggleRecord(false))
        advanceUntilIdle()
        assertEquals(TransportState.STOPPED, viewModel.uiState.value.transportState)
    }

    @Test
    fun testPanelDrawerTogglesAndCloseAll() = runTest(testDispatcher) {
        // Initially all panels closed
        assertFalse(viewModel.uiState.value.isMixerOpen)
        assertFalse(viewModel.uiState.value.isInstrumentPanelOpen)
        assertFalse(viewModel.uiState.value.isPianoRollOpen)
        assertFalse(viewModel.uiState.value.isProjectDrawerOpen)
        assertFalse(viewModel.uiState.value.isModulesDrawerOpen)
        assertFalse(viewModel.uiState.value.isBpmDialOpen)

        // Toggle Mixer Panel
        viewModel.onAction(StudioAction.ToggleMixerPanel)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isMixerOpen)

        // Toggle Instrument Panel
        viewModel.onAction(StudioAction.ToggleInstrumentPanel)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isInstrumentPanelOpen)

        // Toggle Piano Roll Panel
        viewModel.onAction(StudioAction.TogglePianoRollPanel)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isPianoRollOpen)

        // Toggle Project Drawer
        viewModel.onAction(StudioAction.ToggleProjectDrawer)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isProjectDrawerOpen)

        // Toggle Modules Drawer
        viewModel.onAction(StudioAction.ToggleModulesDrawer)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isModulesDrawerOpen)

        // Toggle BPM Dial
        viewModel.onAction(StudioAction.ToggleBpmDial)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isBpmDialOpen)

        // Close All Panels
        viewModel.onAction(StudioAction.CloseAllPanels)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isMixerOpen)
        assertFalse(viewModel.uiState.value.isInstrumentPanelOpen)
        assertFalse(viewModel.uiState.value.isPianoRollOpen)
        assertFalse(viewModel.uiState.value.isProjectDrawerOpen)
        assertFalse(viewModel.uiState.value.isModulesDrawerOpen)
        assertFalse(viewModel.uiState.value.isBpmDialOpen)
        assertFalse(viewModel.uiState.value.isMixerViewActive)
        assertFalse(viewModel.uiState.value.isDspInspectorOpen)
    }

    @Test
    fun testClipPlacementAndActions() = runTest(testDispatcher) {
        viewModel.onAction(StudioAction.AddTrack("Audio Track"))
        advanceUntilIdle()

        val track = viewModel.uiState.value.tracks.first()
        val clipId = 201
        val testClip = AudioClipUiModel(
            clipId = clipId,
            trackId = track.trackId,
            name = "Test Clip",
            startFrame = 0L,
            durationFrames = 48000L * 4L,
            waveformPeakData = null
        )

        // Inject clip manually into state for testing clip actions
        val uiStateField = StudioViewModel::class.java.getDeclaredField("_uiState")
        uiStateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = uiStateField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<com.android.daw.viewmodel.StudioUiState>
        stateFlow.value = stateFlow.value.copy(
            tracks = listOf(track.copy(clips = listOf(testClip)))
        )

        // Select Clip
        viewModel.onAction(StudioAction.SelectClip(clipId))
        advanceUntilIdle()
        assertEquals(clipId, viewModel.uiState.value.selectedClipId)

        // Open clip menu
        viewModel.onAction(StudioAction.SetClipMenuOpen(true))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isClipMenuOpen)

        // Move Clip (FN-03)
        val newStart = 48000L * 2L
        viewModel.onAction(StudioAction.MoveClip(clipId, newStart))
        advanceUntilIdle()
        val movedClip = viewModel.uiState.value.tracks.first().clips.first { it.clipId == clipId }
        assertEquals(newStart, movedClip.startFrame)

        // Trim Clip (FN-03)
        val trimmedStart = 48000L * 3L
        val trimmedDur = 48000L * 2L
        viewModel.onAction(StudioAction.TrimClip(clipId, trimmedStart, trimmedDur))
        advanceUntilIdle()
        val trimmedClip = viewModel.uiState.value.tracks.first().clips.first { it.clipId == clipId }
        assertEquals(trimmedStart, trimmedClip.startFrame)
        assertEquals(trimmedDur, trimmedClip.durationFrames)

        // Copy Clip
        val countBeforeCopy = viewModel.uiState.value.tracks.first().clips.size
        viewModel.onAction(StudioAction.ExecuteClipAction(clipId, "COPY"))
        advanceUntilIdle()
        val countAfterCopy = viewModel.uiState.value.tracks.first().clips.size
        assertEquals(countBeforeCopy + 1, countAfterCopy)

        // Mute Clip
        viewModel.onAction(StudioAction.ExecuteClipAction(clipId, "MUTE"))
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.statusMessage)

        // Delete Clip
        viewModel.onAction(StudioAction.ExecuteClipAction(clipId, "DELETE"))
        advanceUntilIdle()
        val updatedClips = viewModel.uiState.value.tracks.first().clips
        assertNull(updatedClips.firstOrNull { it.clipId == clipId })
        assertNull(viewModel.uiState.value.selectedClipId)
    }

    @Test
    fun testOfflineExportStateTransitions() = runTest(testDispatcher) {
        viewModel.onAction(StudioAction.StartExport(16))
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.statusMessage)

        // Cancel export
        viewModel.onAction(StudioAction.CancelExport)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isExporting)
        assertEquals("Export cancelled.", viewModel.uiState.value.statusMessage)
    }

    @Test
    fun testInstrumentTrackCreation() = runTest(testDispatcher) {
        // Add Sub Synth Track
        viewModel.onAction(
            StudioAction.AddInstrumentTrack("Sub Synth", com.android.daw.viewmodel.TrackInstrumentType.SUB_SYNTH)
        )
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.tracks.size)
        val synthTrack = viewModel.uiState.value.tracks.first()
        assertEquals(com.android.daw.viewmodel.TrackInstrumentType.SUB_SYNTH, synthTrack.instrumentType)
        assertTrue(synthTrack.name.startsWith("Sub Synth"))

        // Add Drum Kit Track
        viewModel.onAction(
            StudioAction.AddInstrumentTrack("Drum Kit", com.android.daw.viewmodel.TrackInstrumentType.DRUM_KIT)
        )
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.tracks.size)
        val drumTrack = viewModel.uiState.value.tracks.last()
        assertEquals(com.android.daw.viewmodel.TrackInstrumentType.DRUM_KIT, drumTrack.instrumentType)
        assertTrue(drumTrack.name.startsWith("Drum Kit"))
    }

    @Test
    fun testPianoRollNoteEditingAndUndoRedo() = runTest(testDispatcher) {
        // 1. Add Sub Synth Track
        viewModel.onAction(
            StudioAction.AddInstrumentTrack("Lead Synth", com.android.daw.viewmodel.TrackInstrumentType.SUB_SYNTH)
        )
        advanceUntilIdle()
        val trackId = viewModel.uiState.value.tracks.first().trackId

        // 2. Create MIDI Clip
        viewModel.onAction(
            StudioAction.CreateMidiClip(trackId = trackId, startFrame = 0L, durationFrames = 48000L * 4L, name = "Pattern 1")
        )
        advanceUntilIdle()
        val clipId = viewModel.uiState.value.tracks.first().clips.first().clipId

        // 3. Open Piano Roll
        viewModel.onAction(StudioAction.OpenPianoRoll(trackId = trackId, clipId = clipId))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isPianoRollOpen)
        assertEquals(clipId, viewModel.uiState.value.pianoRollClipId)

        // 4. Add MIDI Note (C4 = 60)
        viewModel.onAction(
            StudioAction.AddMidiNote(
                trackId = trackId,
                clipId = clipId,
                pitch = 60,
                startFrame = 0L,
                durationFrames = 12000L,
                velocity = 0.85f
            )
        )
        advanceUntilIdle()
        val notesAfterAdd = viewModel.uiState.value.tracks.first().clips.first().notes
        assertEquals(1, notesAfterAdd.size)
        assertEquals(60, notesAfterAdd.first().pitch)
        val noteId = notesAfterAdd.first().id

        // 5. Update MIDI Note (Transpose to D4 = 62)
        viewModel.onAction(
            StudioAction.UpdateMidiNote(
                trackId = trackId,
                clipId = clipId,
                noteId = noteId,
                newPitch = 62,
                newStartFrame = 0L,
                newDurationFrames = 12000L,
                velocity = 0.9f
            )
        )
        advanceUntilIdle()
        val noteAfterUpdate = viewModel.uiState.value.tracks.first().clips.first().notes.first()
        assertEquals(62, noteAfterUpdate.pitch)
        assertEquals(0.9f, noteAfterUpdate.velocity, 1e-4f)

        // 6. Test Undo (Should revert pitch to 60)
        assertTrue(viewModel.uiState.value.canUndo)
        viewModel.onAction(StudioAction.Undo)
        advanceUntilIdle()
        val noteAfterUndo = viewModel.uiState.value.tracks.first().clips.first().notes.first()
        assertEquals(60, noteAfterUndo.pitch)

        // 7. Test Redo (Should re-apply pitch 62)
        assertTrue(viewModel.uiState.value.canRedo)
        viewModel.onAction(StudioAction.Redo)
        advanceUntilIdle()
        val noteAfterRedo = viewModel.uiState.value.tracks.first().clips.first().notes.first()
        assertEquals(62, noteAfterRedo.pitch)

        // 8. Delete Note
        viewModel.onAction(StudioAction.DeleteMidiNote(trackId, clipId, noteId))
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.tracks.first().clips.first().notes.size)

        // 9. Close Piano Roll
        viewModel.onAction(StudioAction.ClosePianoRoll)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isPianoRollOpen)
        assertNull(viewModel.uiState.value.pianoRollClipId)
    }

    @Test
    fun testPianoRollToolAndSnapToggles() = runTest(testDispatcher) {
        // Change Tool
        viewModel.onAction(StudioAction.SetPianoRollTool(com.android.daw.viewmodel.PianoRollTool.ERASE))
        advanceUntilIdle()
        assertEquals(com.android.daw.viewmodel.PianoRollTool.ERASE, viewModel.uiState.value.pianoRollTool)

        // Change Snap
        viewModel.onAction(StudioAction.SetPianoRollSnap(com.android.daw.viewmodel.PianoRollSnap.THIRTY_SECOND))
        advanceUntilIdle()
        assertEquals(com.android.daw.viewmodel.PianoRollSnap.THIRTY_SECOND, viewModel.uiState.value.pianoRollSnap)

        // Toggle Velocity Lane
        assertFalse(viewModel.uiState.value.isVelocityLaneVisible)
        viewModel.onAction(StudioAction.ToggleVelocityLane)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isVelocityLaneVisible)
        viewModel.onAction(StudioAction.ToggleVelocityLane)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isVelocityLaneVisible)

        // Toggle Follow Playhead
        assertTrue(viewModel.uiState.value.followPlayhead)
        viewModel.onAction(StudioAction.ToggleFollowPlayhead)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.followPlayhead)
    }
}
