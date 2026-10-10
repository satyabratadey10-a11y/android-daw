package com.android.daw.viewmodel

import android.net.Uri

/**
 * StudioAction
 *
 * Sealed interface representing all unidirectional user intents (MVI / UDF).
 * Adheres strictly to android-ui-ux-architect Rule 2 (Zero Placeholder/Mock Controls)
 * and Rule 9 (Anti-Drift State Hoisting). Every interactive click/drag event in Compose
 * is mapped directly to one of these explicit actions.
 */
sealed interface StudioAction {

    // --- Transport Intents ---
    data object Play : StudioAction
    data object Pause : StudioAction
    data object Stop : StudioAction
    data object Rewind : StudioAction
    data class Seek(val frame: Long) : StudioAction
    data class SetTempo(val bpm: Double) : StudioAction
    data object ToggleLoop : StudioAction
    data class SetLoopRange(val startFrame: Long, val endFrame: Long) : StudioAction

    // --- Track Lane & Channel Strip Intents ---
    data class AddTrack(val name: String = "Audio Track") : StudioAction
    data class RemoveTrack(val trackId: Int) : StudioAction
    data class SetTrackVolume(val trackId: Int, val volumeLinear: Float) : StudioAction
    data class SetTrackPan(val trackId: Int, val pan: Float) : StudioAction // -1.0f (L) to +1.0f (R)
    data class ToggleMute(val trackId: Int) : StudioAction
    data class ToggleSolo(val trackId: Int) : StudioAction
    data class ToggleArm(val trackId: Int) : StudioAction
    data class SelectTrack(val trackId: Int?) : StudioAction

    // --- Master Bus Intents ---
    data class SetMasterVolume(val volumeLinear: Float) : StudioAction
    data class SetMasterPan(val pan: Float) : StudioAction
    data object ResetClipLeds : StudioAction

    // --- Viewport & Modal Switching ---
    data object ToggleMixerView : StudioAction
    data object ToggleDspInspector : StudioAction
    data object ToggleMixerPanel : StudioAction
    data object ToggleInstrumentPanel : StudioAction
    data object TogglePianoRollPanel : StudioAction
    data object ToggleProjectDrawer : StudioAction
    data object ToggleModulesDrawer : StudioAction
    data object ToggleBpmDial : StudioAction
    data object CloseAllPanels : StudioAction
    data class OpenTrackDsp(val trackId: Int) : StudioAction
    data object OpenMasterDsp : StudioAction
    data object OpenLiveLog : StudioAction
    data class SetZoom(val pixelsPerSecond: Float) : StudioAction
    data class SetScrollOffset(val offsetPx: Float) : StudioAction

    // --- Tier 1 Contextual Clip Intents ---
    data class SelectClip(val clipId: Int?) : StudioAction
    data class ExecuteClipAction(val clipId: Int, val action: String) : StudioAction
    data class SetClipMenuOpen(val isOpen: Boolean) : StudioAction

    // --- DSP Effects Rack Mutations ---
    data class UpdateEq(
        val trackId: Int,
        val bandIndex: Int,
        val freqHz: Float,
        val gainDb: Float,
        val q: Float
    ) : StudioAction

    data class ToggleEqBand(val trackId: Int, val bandIndex: Int) : StudioAction

    data class UpdateDelay(
        val trackId: Int,
        val timeMs: Float,
        val feedback: Float,
        val wetDry: Float
    ) : StudioAction

    data class ToggleDelayPingPong(val trackId: Int) : StudioAction
    data class ToggleDelayBypass(val trackId: Int) : StudioAction

    data class UpdateLimiter(
        val thresholdDb: Float,
        val ceilingDb: Float,
        val releaseMs: Float
    ) : StudioAction

    data class ToggleLimiterBypass(val isBypassed: Boolean) : StudioAction

    // --- Recording & File I/O ---
    data class ToggleRecord(val isRecording: Boolean) : StudioAction
    data class ImportAudio(val trackId: Int, val uri: Uri) : StudioAction
    data class StartExport(val bitDepth: Int = 16) : StudioAction
    data object CancelExport : StudioAction
    data object DismissStatusMessage : StudioAction
}
