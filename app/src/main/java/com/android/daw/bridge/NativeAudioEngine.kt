package com.android.daw.bridge

import com.android.daw.domain.model.DecodedAudioResult
import com.android.daw.domain.model.TelemetryData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Thread-safe JNI Bridge Singleton connecting Kotlin with the native C++ DAW audio engine.
 *
 * Implements [EngineApi] contract matching engine_api.h.
 * Strict Compliance with android-ui-ux-architect Rule 8:
 * All JNI methods execute exclusively on background dispatchers (Dispatchers.Default or
 * Dispatchers.IO), guaranteeing zero main-thread blocking and frame drops in Jetpack Compose.
 */
class NativeAudioEngine(
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AutoCloseable, EngineApi {

    companion object {
        @Volatile
        var isLibraryLoaded: Boolean = false
            private set

        init {
            try {
                System.loadLibrary("daw_audio_engine")
                isLibraryLoaded = true
            } catch (_: Throwable) {
                isLibraryLoaded = false
            }
        }

        @Volatile
        private var instance: NativeAudioEngine? = null

        fun getInstance(): NativeAudioEngine =
            instance ?: synchronized(this) {
                instance ?: NativeAudioEngine().also { instance = it }
            }

        fun setInstanceForTesting(mockEngine: NativeAudioEngine?) {
            instance = mockEngine
        }

        const val TELEMETRY_BYTE_SIZE = 1024
        const val MAX_TRACKS = 32
    }

    // Pre-allocated DirectByteBuffer for zero-copy, zero-allocation 60 Hz telemetry polling
    private val telemetryBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(TELEMETRY_BYTE_SIZE)
        .order(ByteOrder.nativeOrder())

    @Volatile
    private var isInitialized: Boolean = false

    @Volatile
    private var isPlaybackActive: Boolean = false

    private val reusableTelemetry = TelemetryData()

    // ========================================================================
    // Lifecycle Management (Dispatchers.Default)
    // ========================================================================

    override suspend fun initialize(sampleRate: Int, framesPerBurst: Int): Boolean {
        if (!isLibraryLoaded) return false
        return withContext(defaultDispatcher) {
            try {
                nativeInit(sampleRate, framesPerBurst)
                isInitialized = true
                true
            } catch (_: Throwable) {
                isInitialized = false
                false
            }
        }
    }

    suspend fun start(): Boolean {
        if (!isLibraryLoaded || !isInitialized) return false
        return withContext(defaultDispatcher) {
            try {
                nativePlay()
                isPlaybackActive = true
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    override suspend fun stop(): Boolean {
        if (!isLibraryLoaded || !isInitialized) return true
        return withContext(defaultDispatcher) {
            try {
                nativeStop()
                isPlaybackActive = false
                true
            } catch (_: Throwable) {
                true
            }
        }
    }

    override fun close() {
        if (isLibraryLoaded && isInitialized) {
            try {
                nativeRelease()
            } catch (_: Throwable) {
                // Ignore cleanup errors
            }
        }
        isInitialized = false
        isPlaybackActive = false
    }

    override suspend fun release() {
        close()
    }

    override fun isInitialized(): Boolean = isLibraryLoaded && isInitialized

    fun isEngineInitialized(): Boolean = isLibraryLoaded && isInitialized

    // ========================================================================
    // Transport Controls (Dispatchers.Default)
    // ========================================================================

    override suspend fun play(): Boolean {
        if (!isLibraryLoaded || !isInitialized) return false
        return withContext(defaultDispatcher) {
            try {
                nativePlay()
                isPlaybackActive = true
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    override suspend fun pause(): Boolean {
        if (!isLibraryLoaded || !isInitialized) return false
        return withContext(defaultDispatcher) {
            try {
                nativePause()
                isPlaybackActive = false
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    suspend fun stopTransport(): Boolean = stop()

    override suspend fun seek(framePosition: Long): Boolean {
        if (!isLibraryLoaded || !isInitialized) return false
        return withContext(defaultDispatcher) {
            try {
                nativeSeek(framePosition.coerceAtLeast(0L))
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    suspend fun seekTo(framePosition: Long) {
        seek(framePosition)
    }

    override suspend fun setLoop(enabled: Boolean, startFrame: Long, endFrame: Long): Boolean {
        if (!isLibraryLoaded || !isInitialized) return false
        return withContext(defaultDispatcher) {
            try {
                nativeSetLoop(enabled, startFrame.coerceAtLeast(0L), endFrame.coerceAtLeast(0L))
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    override suspend fun setTempo(bpm: Double): Boolean {
        if (!isLibraryLoaded || !isInitialized) return false
        return withContext(defaultDispatcher) {
            try {
                nativeSetTempo(bpm.coerceIn(20.0, 300.0))
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    override suspend fun getPlaybackPosition(): Long = getPlaybackPositionFrames()

    override fun isPlaying(): Boolean = isPlaybackActive

    // ========================================================================
    // Track Management (Dispatchers.Default)
    // ========================================================================

    override suspend fun addTrack(name: String): Int {
        if (!isLibraryLoaded || !isInitialized) return -1
        return withContext(defaultDispatcher) {
            try {
                nativeAddTrack(name)
            } catch (_: Throwable) {
                -1
            }
        }
    }

    suspend fun addTrack(trackId: Int): Boolean {
        if (!isLibraryLoaded || !isInitialized) return true
        return withContext(defaultDispatcher) {
            try {
                nativeAddTrack("Track $trackId") >= 0
            } catch (_: Throwable) {
                false
            }
        }
    }

    override suspend fun removeTrack(trackId: Int): Boolean {
        if (!isLibraryLoaded || !isInitialized) return true
        return withContext(defaultDispatcher) {
            try {
                nativeRemoveTrack(trackId)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    override suspend fun setTrackGain(trackId: Int, linearGain: Float): Boolean {
        if (!isLibraryLoaded || !isInitialized) return false
        return withContext(defaultDispatcher) {
            try {
                nativeSetTrackVolume(trackId, linearGain.coerceIn(0f, 4f))
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    suspend fun setTrackVolume(trackId: Int, linearGain: Float) {
        setTrackGain(trackId, linearGain)
    }

    override suspend fun setTrackPan(trackId: Int, pan: Float): Boolean {
        if (!isLibraryLoaded || !isInitialized) return false
        return withContext(defaultDispatcher) {
            try {
                nativeSetTrackPan(trackId, pan.coerceIn(-1f, 1f))
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    override suspend fun setTrackMute(trackId: Int, muted: Boolean): Boolean {
        if (!isLibraryLoaded || !isInitialized) return false
        return withContext(defaultDispatcher) {
            try {
                nativeSetTrackMute(trackId, muted)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    override suspend fun setTrackSolo(trackId: Int, soloed: Boolean): Boolean {
        if (!isLibraryLoaded || !isInitialized) return false
        return withContext(defaultDispatcher) {
            try {
                nativeSetTrackSolo(trackId, soloed)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    override suspend fun setTrackArmed(trackId: Int, armed: Boolean): Boolean {
        if (!isLibraryLoaded || !isInitialized) return false
        return withContext(defaultDispatcher) {
            try {
                nativeSetTrackArmed(trackId, armed)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    override suspend fun setTrackType(trackId: Int, trackType: Int): Boolean {
        if (!isLibraryLoaded || !isInitialized) return false
        return withContext(defaultDispatcher) {
            try {
                nativeSetTrackType(trackId, trackType)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    override suspend fun noteOn(trackId: Int, pitch: Int, velocity: Float) {
        if (!isLibraryLoaded || !isInitialized) return
        withContext(defaultDispatcher) {
            try {
                nativeSendNoteOn(trackId, pitch, velocity)
            } catch (_: Throwable) {}
        }
    }

    override suspend fun noteOff(trackId: Int, pitch: Int) {
        if (!isLibraryLoaded || !isInitialized) return
        withContext(defaultDispatcher) {
            try {
                nativeSendNoteOff(trackId, pitch)
            } catch (_: Throwable) {}
        }
    }

    override suspend fun allNotesOff(trackId: Int) {
        if (!isLibraryLoaded || !isInitialized) return
        withContext(defaultDispatcher) {
            for (p in 0..127) {
                try {
                    nativeSendNoteOff(trackId, p)
                } catch (_: Throwable) {}
            }
        }
    }

    override suspend fun loadMidiClip(
        trackId: Int,
        clipId: Int,
        name: String,
        startOffsetFrames: Long,
        durationFrames: Long
    ): Boolean {
        if (!isLibraryLoaded || !isInitialized) return false
        return withContext(defaultDispatcher) {
            try {
                nativeLoadMidiClip(trackId, clipId, name, startOffsetFrames, durationFrames)
            } catch (_: Throwable) {
                false
            }
        }
    }

    override suspend fun setClipMidiNotes(
        trackId: Int,
        clipId: Int,
        ids: IntArray,
        pitches: IntArray,
        startFrames: LongArray,
        durations: LongArray,
        velocities: FloatArray
    ): Boolean {
        if (!isLibraryLoaded || !isInitialized) return false
        return withContext(defaultDispatcher) {
            try {
                nativeSetClipMidiNotes(trackId, clipId, ids, pitches, startFrames, durations, velocities)
            } catch (_: Throwable) {
                false
            }
        }
    }

    // ========================================================================
    // DSP Controls (Dispatchers.Default)
    // ========================================================================

    override suspend fun setTrackEqBand(
        trackId: Int,
        bandIndex: Int,
        filterType: Int,
        freqHz: Float,
        gainDb: Float,
        qFactor: Float
    ): Boolean {
        if (!isLibraryLoaded || !isInitialized) return false
        return withContext(defaultDispatcher) {
            try {
                nativeSetTrackEqBand(trackId, bandIndex, filterType, freqHz, gainDb, qFactor)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    suspend fun setTrackEq(
        trackId: Int,
        bandIndex: Int,
        filterType: Int,
        frequencyHz: Float,
        qFactor: Float,
        gainDb: Float
    ) {
        setTrackEqBand(trackId, bandIndex, filterType, frequencyHz, gainDb, qFactor)
    }

    override suspend fun setTrackDelay(
        trackId: Int,
        delayMs: Float,
        feedback: Float,
        wetDry: Float
    ): Boolean {
        if (!isLibraryLoaded || !isInitialized) return false
        return withContext(defaultDispatcher) {
            try {
                nativeSetTrackDelay(trackId, delayMs, feedback, wetDry)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    override suspend fun setMasterLimiter(
        thresholdDb: Float,
        ceilingDb: Float,
        releaseMs: Float
    ): Boolean {
        if (!isLibraryLoaded || !isInitialized) return false
        return withContext(defaultDispatcher) {
            try {
                nativeSetMasterLimiter(thresholdDb, ceilingDb, releaseMs)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    // ========================================================================
    // Clip Management (Dispatchers.IO)
    // ========================================================================

    override suspend fun loadClip(
        trackId: Int,
        clipId: Int,
        filePath: String,
        startOffsetFrames: Long
    ): Boolean {
        if (!isLibraryLoaded || !isInitialized) return false
        return withContext(ioDispatcher) {
            try {
                nativeLoadClip(trackId, clipId, filePath, startOffsetFrames)
            } catch (_: Throwable) {
                false
            }
        }
    }

    override suspend fun moveClip(trackId: Int, clipId: Int, newStartFrame: Long): Boolean {
        if (!isLibraryLoaded || !isInitialized) return false
        return withContext(defaultDispatcher) {
            try {
                nativeMoveClip(trackId, clipId, newStartFrame)
            } catch (_: Throwable) {
                false
            }
        }
    }

    override suspend fun removeClip(trackId: Int, clipId: Int): Boolean {
        if (!isLibraryLoaded || !isInitialized) return false
        return withContext(defaultDispatcher) {
            try {
                nativeRemoveClip(trackId, clipId)
            } catch (_: Throwable) {
                false
            }
        }
    }

    // ========================================================================
    // Native Multi-Format Audio Decoding (WAV, MP3, FLAC)
    // ========================================================================

    suspend fun decodeAudioFile(filePath: String, targetSampleRate: Int = 48000): DecodedAudioResult? {
        if (!isLibraryLoaded) return null
        return withContext(ioDispatcher) {
            try {
                nativeDecodeAudioFile(filePath, targetSampleRate)
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun getCpuUsage(): Float {
        if (!isLibraryLoaded) return 0f
        return try {
            nativeGetCpuUsage()
        } catch (_: Throwable) {
            0f
        }
    }

    // ========================================================================
    // Recording & Offline Mixdown (Dispatchers.IO / Dispatchers.Default)
    // ========================================================================

    override suspend fun startRecording(trackId: Int, destinationWavPath: String): Boolean {
        if (!isLibraryLoaded || !isInitialized) return false
        return withContext(defaultDispatcher) {
            try {
                nativeStartRecording(trackId, destinationWavPath)
            } catch (_: Throwable) {
                false
            }
        }
    }

    suspend fun startRecording(trackId: Int): Boolean {
        return startRecording(trackId, "")
    }

    override suspend fun stopRecording(trackId: Int) {
        if (!isLibraryLoaded || !isInitialized) return
        withContext(defaultDispatcher) {
            try {
                nativeStopRecording(trackId)
            } catch (_: Throwable) {}
        }
    }

    suspend fun stopRecording(): ByteBuffer? {
        stopRecording(0)
        return null
    }

    override suspend fun renderOffline(outputWavPath: String, bitDepth: Int, totalFrames: Long): Boolean {
        if (!isLibraryLoaded || !isInitialized) return false
        return withContext(ioDispatcher) {
            try {
                nativeRenderMixdown(outputWavPath, bitDepth, totalFrames)
            } catch (_: Throwable) {
                false
            }
        }
    }

    suspend fun renderOffline(outputPath: String, totalFrames: Long): Boolean =
        renderOffline(outputPath, 16, totalFrames)

    suspend fun renderMixdown(outputPath: String, bitDepth: Int = 16, totalFrames: Long): Boolean =
        renderOffline(outputPath, bitDepth, totalFrames)

    // ========================================================================
    // Telemetry & Hardware Clock (Called on Background Polling Coroutine)
    // ========================================================================

    suspend fun getPlayheadFrame(): Long {
        if (!isLibraryLoaded || !isInitialized) return 0L
        return withContext(defaultDispatcher) {
            try {
                nativeGetPlaybackPositionFrames()
            } catch (_: Throwable) {
                0L
            }
        }
    }

    fun getPlaybackPositionFrames(): Long {
        if (!isLibraryLoaded || !isInitialized) return 0L
        return try {
            nativeGetPlaybackPositionFrames()
        } catch (_: Throwable) {
            0L
        }
    }

    override fun getTelemetryData(): TelemetryData {
        pollTelemetry(reusableTelemetry)
        return reusableTelemetry
    }

    /**
     * Polls the atomic C++ telemetry registers directly into [telemetryBuffer] with zero allocations.
     * Must be called from a background polling worker.
     */
    fun pollTelemetry(outTelemetry: TelemetryData): Boolean {
        if (!isLibraryLoaded || !isInitialized) return false
        return synchronized(telemetryBuffer) {
            try {
                telemetryBuffer.clear()
                val numTracks = kotlin.math.min(outTelemetry.trackPeaks.size, MAX_TRACKS)
                nativeGetMeteringData(telemetryBuffer, numTracks)
                telemetryBuffer.position(0)
                outTelemetry.masterPeakLeft = telemetryBuffer.getFloat()
                outTelemetry.masterPeakRight = telemetryBuffer.getFloat()
                outTelemetry.masterRmsLeft = telemetryBuffer.getFloat()
                outTelemetry.masterRmsRight = telemetryBuffer.getFloat()
                val limiterGr = telemetryBuffer.getFloat()

                for (i in 0 until numTracks) {
                    val pl = telemetryBuffer.getFloat()
                    val pr = telemetryBuffer.getFloat()
                    val rl = telemetryBuffer.getFloat()
                    val rr = telemetryBuffer.getFloat()
                    outTelemetry.trackPeaks[i] = kotlin.math.max(pl, pr)
                }

                outTelemetry.playheadFrame = nativeGetPlaybackPositionFrames()
                outTelemetry.isPlaying = isPlaybackActive
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    // ========================================================================
    // JNI Native Methods (Bound dynamically via RegisterNatives in JNI_OnLoad)
    // ========================================================================

    private external fun nativeInit(sampleRate: Int, framesPerBurst: Int)
    private external fun nativeRelease()
    private external fun nativePlay()
    private external fun nativePause()
    private external fun nativeStop()
    private external fun nativeSeek(framePosition: Long)
    private external fun nativeSetLoop(enabled: Boolean, startFrame: Long, endFrame: Long)
    private external fun nativeSetTempo(bpm: Double)

    private external fun nativeAddTrack(name: String): Int
    private external fun nativeRemoveTrack(trackId: Int)
    private external fun nativeSetTrackVolume(trackId: Int, linearGain: Float)
    private external fun nativeSetTrackPan(trackId: Int, panPosition: Float)
    private external fun nativeSetTrackMute(trackId: Int, muted: Boolean)
    private external fun nativeSetTrackSolo(trackId: Int, soloed: Boolean)
    private external fun nativeSetTrackArmed(trackId: Int, armed: Boolean)
    private external fun nativeSetTrackType(trackId: Int, trackType: Int)
    private external fun nativeSendNoteOn(trackId: Int, note: Int, velocity: Float)
    private external fun nativeSendNoteOff(trackId: Int, note: Int)

    private external fun nativeSetTrackEqBand(
        trackId: Int,
        bandIndex: Int,
        filterType: Int,
        freqHz: Float,
        gainDb: Float,
        qFactor: Float
    )

    private external fun nativeSetTrackDelay(
        trackId: Int,
        delayMs: Float,
        feedback: Float,
        wetDry: Float
    )

    private external fun nativeSetMasterLimiter(
        thresholdDb: Float,
        ceilingDb: Float,
        releaseMs: Float
    )

    private external fun nativeGetPlaybackPositionFrames(): Long
    private external fun nativeGetMeteringData(directFloatBuffer: ByteBuffer, numTracks: Int)

    private external fun nativeLoadClip(
        trackId: Int,
        clipId: Int,
        filePath: String,
        startOffsetFrames: Long
    ): Boolean

    private external fun nativeLoadMidiClip(
        trackId: Int,
        clipId: Int,
        name: String,
        startOffsetFrames: Long,
        durationFrames: Long
    ): Boolean

    private external fun nativeSetClipMidiNotes(
        trackId: Int,
        clipId: Int,
        ids: IntArray,
        notes: IntArray,
        startFrames: LongArray,
        durations: LongArray,
        velocities: FloatArray
    ): Boolean

    private external fun nativeMoveClip(trackId: Int, clipId: Int, newStartFrame: Long): Boolean
    private external fun nativeRemoveClip(trackId: Int, clipId: Int): Boolean

    private external fun nativeStartRecording(trackId: Int, destinationWavPath: String): Boolean
    private external fun nativeStopRecording(trackId: Int)
    private external fun nativeRenderMixdown(outputWavPath: String, bitDepth: Int, totalFrames: Long): Boolean

    private external fun nativeDecodeAudioFile(filePath: String, targetSampleRate: Int): DecodedAudioResult?
    private external fun nativeGetCpuUsage(): Float
}
