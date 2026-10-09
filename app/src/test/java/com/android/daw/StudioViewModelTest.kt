package com.android.daw

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
 * [StudioUiState] state transitions, track CRUD, solo/mute/arm logic, and DSP mutations.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StudioViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: StudioViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = StudioViewModel(
            defaultDispatcher = testDispatcher,
            ioDispatcher = testDispatcher,
            enableTelemetryLoop = false
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
        assertEquals(4, state.tracks.size)
        assertEquals(1, state.selectedTrackId)
        assertTrue(state.isLooping)
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
        assertFalse(viewModel.uiState.value.isLooping)

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

        // Select different track
        viewModel.onAction(StudioAction.SelectTrack(2))
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.selectedTrackId)

        // Remove track
        val trackToRemove = addedTrack.trackId
        viewModel.onAction(StudioAction.RemoveTrack(trackToRemove))
        advanceUntilIdle()
        assertEquals(initialCount, viewModel.uiState.value.tracks.size)
        assertNull(viewModel.uiState.value.tracks.firstOrNull { it.trackId == trackToRemove })
    }

    @Test
    fun testTrackMixingControls() = runTest(testDispatcher) {
        val targetId = 1

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
        val trackId = 1

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
        // Try recording with no tracks armed
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

        // Arm track 1 and record
        viewModel.onAction(StudioAction.ToggleArm(1))
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
    fun testContextualClipSelectionAndActions() = runTest(testDispatcher) {
        val track1 = viewModel.uiState.value.tracks.first()
        val originalClip = track1.clips.first()
        val originalClipId = originalClip.clipId

        // Select Clip
        viewModel.onAction(StudioAction.SelectClip(originalClipId))
        advanceUntilIdle()
        assertEquals(originalClipId, viewModel.uiState.value.selectedClipId)

        // Open clip menu
        viewModel.onAction(StudioAction.SetClipMenuOpen(true))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isClipMenuOpen)

        // Copy Clip
        val initialClipCount = track1.clips.size
        viewModel.onAction(StudioAction.ExecuteClipAction(originalClipId, "COPY"))
        advanceUntilIdle()
        val updatedTrackAfterCopy = viewModel.uiState.value.tracks.first { it.trackId == track1.trackId }
        assertEquals(initialClipCount + 1, updatedTrackAfterCopy.clips.size)

        // Slice original clip
        viewModel.onAction(StudioAction.ExecuteClipAction(originalClipId, "SLICE"))
        advanceUntilIdle()
        val updatedTrackAfterSlice = viewModel.uiState.value.tracks.first { it.trackId == track1.trackId }
        assertEquals(initialClipCount + 2, updatedTrackAfterSlice.clips.size)

        // Mute clip
        viewModel.onAction(StudioAction.ExecuteClipAction(originalClipId, "MUTE"))
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.statusMessage)

        // Delete clip
        viewModel.onAction(StudioAction.ExecuteClipAction(originalClipId, "DELETE"))
        advanceUntilIdle()
        val updatedTrackAfterDelete = viewModel.uiState.value.tracks.first { it.trackId == track1.trackId }
        assertNull(updatedTrackAfterDelete.clips.firstOrNull { it.clipId == originalClipId })
        assertNull(viewModel.uiState.value.selectedClipId)
    }
}
