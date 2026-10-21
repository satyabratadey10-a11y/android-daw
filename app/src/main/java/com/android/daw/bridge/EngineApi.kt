package com.android.daw.bridge

/**
 * Clean Kotlin Engine API contract strictly matching engine_api.h.
 * Exposes core transport, mixing, clip management, metering, and rendering.
 */
interface EngineApi {
    suspend fun initialize(sampleRate: Int = 44100, framesPerBurst: Int = 192): Boolean
    suspend fun release()
    fun isInitialized(): Boolean

    // Transport
    suspend fun play(): Boolean
    suspend fun pause(): Boolean
    suspend fun stop(): Boolean
    suspend fun seek(framePosition: Long): Boolean
    suspend fun setTempo(bpm: Double): Boolean
    suspend fun setLoop(enabled: Boolean, startFrame: Long, endFrame: Long): Boolean
    suspend fun getPlaybackPosition(): Long
    fun isPlaying(): Boolean

    // Track Mixing
    suspend fun addTrack(name: String): Int
    suspend fun removeTrack(trackId: Int): Boolean
    suspend fun setTrackGain(trackId: Int, linearGain: Float): Boolean
    suspend fun setTrackPan(trackId: Int, pan: Float): Boolean
    suspend fun setTrackMute(trackId: Int, muted: Boolean): Boolean
    suspend fun setTrackSolo(trackId: Int, soloed: Boolean): Boolean
    suspend fun setTrackArmed(trackId: Int, armed: Boolean): Boolean

    // Audio Clips
    suspend fun loadClip(trackId: Int, clipId: Int, filePath: String, startOffsetFrames: Long): Boolean
    suspend fun moveClip(trackId: Int, clipId: Int, newStartFrame: Long): Boolean
    suspend fun removeClip(trackId: Int, clipId: Int): Boolean

    // MIDI & Audition
    suspend fun noteOn(trackId: Int, pitch: Int, velocity: Float)
    suspend fun noteOff(trackId: Int, pitch: Int)
    suspend fun allNotesOff(trackId: Int)
    suspend fun setTrackType(trackId: Int, trackType: Int): Boolean
    suspend fun loadMidiClip(trackId: Int, clipId: Int, name: String, startOffsetFrames: Long, durationFrames: Long): Boolean
    suspend fun setClipMidiNotes(trackId: Int, clipId: Int, ids: IntArray, pitches: IntArray, startFrames: LongArray, durations: LongArray, velocities: FloatArray): Boolean

    // DSP & Limiter
    suspend fun setTrackEqBand(trackId: Int, bandIndex: Int, filterType: Int, freqHz: Float, gainDb: Float, qFactor: Float): Boolean
    suspend fun setTrackDelay(trackId: Int, delayMs: Float, feedback: Float, wetDry: Float): Boolean
    suspend fun setMasterLimiter(thresholdDb: Float, ceilingDb: Float, releaseMs: Float): Boolean

    // Telemetry & Recording / Export
    fun getTelemetryData(): com.android.daw.domain.model.TelemetryData
    suspend fun startRecording(trackId: Int, destinationWavPath: String): Boolean
    suspend fun stopRecording(trackId: Int)
    suspend fun renderOffline(outputWavPath: String, bitDepth: Int, totalFrames: Long): Boolean
}
