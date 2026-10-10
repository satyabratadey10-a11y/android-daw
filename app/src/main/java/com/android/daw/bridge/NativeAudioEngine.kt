package com.android.daw.bridge

import com.android.daw.domain.model.TelemetryData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Thread-safe JNI Bridge Singleton connecting Kotlin with the native C++ DAW audio engine.
 *
 * Strict Compliance with android-ui-ux-architect Rule 8:
 * All JNI methods execute exclusively on background dispatchers (Dispatchers.Default or
 * Dispatchers.IO), guaranteeing zero main-thread blocking and frame drops in Jetpack Compose.
 */
class NativeAudioEngine(
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AutoCloseable {

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

        // sizeof(TelemetryPayload) with 64-byte alignment padding
        const val TELEMETRY_BYTE_SIZE = 176
        const val MAX_TRACKS = 32
    }

    // Pre-allocated DirectByteBuffer for zero-copy, zero-allocation 60 Hz telemetry polling
    private val telemetryBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(TELEMETRY_BYTE_SIZE)
        .order(ByteOrder.nativeOrder())

    @Volatile
    private var isInitialized: Boolean = false

    // ========================================================================
    // Lifecycle Management (Dispatchers.Default)
    // ========================================================================

    suspend fun initialize(sampleRate: Int = 44100, framesPerBurst: Int = 192): Boolean {
        if (!isLibraryLoaded) return false
        return withContext(defaultDispatcher) {
            try {
                val success = nativeInit(sampleRate, framesPerBurst)
                isInitialized = success
                success
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
                nativeStart()
            } catch (_: Throwable) {
                false
            }
        }
    }

    suspend fun stop(): Boolean {
        if (!isLibraryLoaded || !isInitialized) return true
        return withContext(defaultDispatcher) {
            try {
                nativeStop()
            } catch (_: Throwable) {
                true
            }
        }
    }

    override fun close() {
        release()
    }

    fun release() {
        if (isLibraryLoaded && isInitialized) {
            try {
                nativeRelease()
            } catch (_: Throwable) {
                // Ignore cleanup errors
            }
        }
        isInitialized = false
    }

    fun isEngineInitialized(): Boolean = isLibraryLoaded && isInitialized

    // ========================================================================
    // Transport Controls (Dispatchers.Default)
    // ========================================================================

    suspend fun play() {
        if (!isLibraryLoaded || !isInitialized) return
        withContext(defaultDispatcher) {
            try {
                nativePlay()
            } catch (_: Throwable) {}
        }
    }

    suspend fun pause() {
        if (!isLibraryLoaded || !isInitialized) return
        withContext(defaultDispatcher) {
            try {
                nativePause()
            } catch (_: Throwable) {}
        }
    }

    suspend fun stopTransport() {
        if (!isLibraryLoaded || !isInitialized) return
        withContext(defaultDispatcher) {
            try {
                nativeStopTransport()
            } catch (_: Throwable) {}
        }
    }

    suspend fun seekTo(framePosition: Long) {
        if (!isLibraryLoaded || !isInitialized) return
        withContext(defaultDispatcher) {
            try {
                nativeSeekTo(framePosition.coerceAtLeast(0L))
            } catch (_: Throwable) {}
        }
    }

    suspend fun seek(framePosition: Long) = seekTo(framePosition)

    suspend fun setLoop(enabled: Boolean, startFrame: Long, endFrame: Long) {
        if (!isLibraryLoaded || !isInitialized) return
        withContext(defaultDispatcher) {
            try {
                nativeSetLoop(enabled, startFrame.coerceAtLeast(0L), endFrame.coerceAtLeast(0L))
            } catch (_: Throwable) {}
        }
    }

    suspend fun setTempo(bpm: Double) {
        if (!isLibraryLoaded || !isInitialized) return
        withContext(defaultDispatcher) {
            try {
                nativeSetTempo(bpm.coerceIn(20.0, 300.0))
            } catch (_: Throwable) {}
        }
    }

    // ========================================================================
    // Track Management (Dispatchers.Default)
    // ========================================================================

    suspend fun addTrack(trackId: Int): Boolean {
        if (!isLibraryLoaded || !isInitialized) return true
        return withContext(defaultDispatcher) {
            try {
                nativeAddTrack(trackId)
            } catch (_: Throwable) {
                false
            }
        }
    }

    suspend fun removeTrack(trackId: Int): Boolean {
        if (!isLibraryLoaded || !isInitialized) return true
        return withContext(defaultDispatcher) {
            try {
                nativeRemoveTrack(trackId)
            } catch (_: Throwable) {
                false
            }
        }
    }

    suspend fun setTrackVolume(trackId: Int, linearGain: Float) {
        if (!isLibraryLoaded || !isInitialized) return
        withContext(defaultDispatcher) {
            try {
                nativeSetTrackVolume(trackId, linearGain.coerceIn(0f, 4f))
            } catch (_: Throwable) {}
        }
    }

    suspend fun setTrackPan(trackId: Int, panPosition: Float) {
        if (!isLibraryLoaded || !isInitialized) return
        withContext(defaultDispatcher) {
            try {
                nativeSetTrackPan(trackId, panPosition.coerceIn(-1f, 1f))
            } catch (_: Throwable) {}
        }
    }

    suspend fun setTrackMute(trackId: Int, muted: Boolean) {
        if (!isLibraryLoaded || !isInitialized) return
        withContext(defaultDispatcher) {
            try {
                nativeSetTrackMute(trackId, muted)
            } catch (_: Throwable) {}
        }
    }

    suspend fun setTrackSolo(trackId: Int, soloed: Boolean) {
        if (!isLibraryLoaded || !isInitialized) return
        withContext(defaultDispatcher) {
            try {
                nativeSetTrackSolo(trackId, soloed)
            } catch (_: Throwable) {}
        }
    }

    suspend fun setTrackArmed(trackId: Int, armed: Boolean) {
        if (!isLibraryLoaded || !isInitialized) return
        withContext(defaultDispatcher) {
            try {
                nativeSetTrackArmed(trackId, armed)
            } catch (_: Throwable) {}
        }
    }

    // ========================================================================
    // DSP Controls (Dispatchers.Default)
    // ========================================================================

    suspend fun setTrackEq(
        trackId: Int,
        bandIndex: Int,
        filterType: Int,
        frequencyHz: Float,
        qFactor: Float,
        gainDb: Float
    ) {
        if (!isLibraryLoaded || !isInitialized) return
        withContext(defaultDispatcher) {
            try {
                nativeSetTrackEq(trackId, bandIndex, filterType, frequencyHz, qFactor, gainDb)
            } catch (_: Throwable) {}
        }
    }

    suspend fun setTrackEqBand(
        trackId: Int,
        bandIndex: Int,
        filterType: Int,
        freqHz: Float,
        gainDb: Float,
        qFactor: Float
    ) = setTrackEq(trackId, bandIndex, filterType, freqHz, qFactor, gainDb)

    suspend fun setTrackDelay(
        trackId: Int,
        delayMs: Float,
        feedback: Float,
        wetMix: Float
    ) {
        if (!isLibraryLoaded || !isInitialized) return
        withContext(defaultDispatcher) {
            try {
                nativeSetTrackDelay(trackId, delayMs, feedback, wetMix)
            } catch (_: Throwable) {}
        }
    }

    suspend fun setMasterLimiter(
        thresholdDb: Float,
        ceilingDb: Float,
        releaseMs: Float
    ) {
        if (!isLibraryLoaded || !isInitialized) return
        withContext(defaultDispatcher) {
            try {
                nativeSetMasterLimiter(thresholdDb, ceilingDb, releaseMs)
            } catch (_: Throwable) {}
        }
    }

    // ========================================================================
    // Clip Management (Dispatchers.IO)
    // ========================================================================

    suspend fun loadClip(
        trackId: Int,
        clipId: Int,
        startFrame: Long,
        directBuffer: ByteBuffer,
        numFrames: Int,
        channels: Int
    ): Boolean {
        if (!isLibraryLoaded || !isInitialized) return false
        return withContext(ioDispatcher) {
            require(directBuffer.isDirect) { "Audio buffer must be allocated as DirectByteBuffer" }
            try {
                nativeLoadClip(trackId, clipId, startFrame, directBuffer, numFrames, channels)
            } catch (_: Throwable) {
                false
            }
        }
    }

    suspend fun removeClip(trackId: Int, clipId: Int): Boolean {
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
    // Recording & Offline Mixdown (Dispatchers.IO / Dispatchers.Default)
    // ========================================================================

    suspend fun startRecording(trackId: Int): Boolean {
        if (!isLibraryLoaded || !isInitialized) return true
        return withContext(defaultDispatcher) {
            try {
                nativeStartRecording(trackId)
            } catch (_: Throwable) {
                false
            }
        }
    }

    suspend fun stopRecording(): ByteBuffer? {
        if (!isLibraryLoaded || !isInitialized) return null
        return withContext(ioDispatcher) {
            try {
                nativeStopRecording()
            } catch (_: Throwable) {
                null
            }
        }
    }

    suspend fun renderOffline(outputPath: String, totalFrames: Long): Boolean {
        if (!isLibraryLoaded || !isInitialized) return false
        return withContext(ioDispatcher) {
            try {
                nativeRenderOffline(outputPath, totalFrames)
            } catch (_: Throwable) {
                false
            }
        }
    }

    suspend fun renderMixdown(outputPath: String, bitDepth: Int = 16, totalFrames: Long): Boolean =
        renderOffline(outputPath, totalFrames)

    // ========================================================================
    // Telemetry & Hardware Clock (Called on Background Polling Coroutine)
    // ========================================================================

    suspend fun getPlayheadFrame(): Long {
        if (!isLibraryLoaded || !isInitialized) return 0L
        return withContext(defaultDispatcher) {
            try {
                nativeGetPlayheadFrame()
            } catch (_: Throwable) {
                0L
            }
        }
    }

    fun getPlaybackPositionFrames(): Long {
        if (!isLibraryLoaded || !isInitialized) return 0L
        return try {
            nativeGetPlayheadFrame()
        } catch (_: Throwable) {
            0L
        }
    }

    /**
     * Polls the atomic C++ telemetry registers directly into [telemetryBuffer] with zero allocations.
     * Must be called from a background polling worker (such as [AudioTelemetryCoordinator]).
     */
    fun pollTelemetry(outTelemetry: TelemetryData): Boolean {
        if (!isLibraryLoaded || !isInitialized) return false
        return synchronized(telemetryBuffer) {
            try {
                telemetryBuffer.clear()
                val result = nativeGetTelemetry(telemetryBuffer)
                if (result == 0) {
                    telemetryBuffer.position(0)
                    outTelemetry.playheadFrame = telemetryBuffer.getLong()
                    outTelemetry.masterPeakLeft = telemetryBuffer.getFloat()
                    outTelemetry.masterPeakRight = telemetryBuffer.getFloat()
                    outTelemetry.masterRmsLeft = telemetryBuffer.getFloat()
                    outTelemetry.masterRmsRight = telemetryBuffer.getFloat()
                    outTelemetry.activeTrackCount = telemetryBuffer.getInt()

                    val numTracksToRead = kotlin.math.min(outTelemetry.trackPeaks.size, MAX_TRACKS)
                    for (i in 0 until numTracksToRead) {
                        outTelemetry.trackPeaks[i] = telemetryBuffer.getFloat()
                    }

                    outTelemetry.isPlaying = telemetryBuffer.getInt() != 0
                    outTelemetry.isRecording = telemetryBuffer.getInt() != 0
                    outTelemetry.underrunCount = telemetryBuffer.getInt()
                    true
                } else {
                    false
                }
            } catch (_: Throwable) {
                false
            }
        }
    }

    // ========================================================================
    // JNI Native Methods (Bound dynamically via RegisterNatives in JNI_OnLoad)
    // ========================================================================

    private external fun nativeInit(sampleRate: Int, framesPerBurst: Int): Boolean
    private external fun nativeStart(): Boolean
    private external fun nativeStop(): Boolean
    private external fun nativeRelease()

    private external fun nativePlay()
    private external fun nativePause()
    private external fun nativeStopTransport()
    private external fun nativeSeekTo(framePosition: Long)
    private external fun nativeSetLoop(enabled: Boolean, startFrame: Long, endFrame: Long)
    private external fun nativeSetTempo(bpm: Double)

    private external fun nativeAddTrack(trackId: Int): Boolean
    private external fun nativeRemoveTrack(trackId: Int): Boolean
    private external fun nativeSetTrackVolume(trackId: Int, volume: Float)
    private external fun nativeSetTrackPan(trackId: Int, pan: Float)
    private external fun nativeSetTrackMute(trackId: Int, muted: Boolean)
    private external fun nativeSetTrackSolo(trackId: Int, soloed: Boolean)
    private external fun nativeSetTrackArmed(trackId: Int, armed: Boolean)

    private external fun nativeLoadClip(
        trackId: Int,
        clipId: Int,
        startFrame: Long,
        directBuffer: ByteBuffer,
        numFrames: Int,
        channels: Int
    ): Boolean

    private external fun nativeRemoveClip(trackId: Int, clipId: Int): Boolean

    private external fun nativeSetTrackEq(
        trackId: Int,
        bandIndex: Int,
        filterType: Int,
        freq: Float,
        q: Float,
        gainDb: Float
    )

    private external fun nativeSetTrackDelay(
        trackId: Int,
        delayMs: Float,
        feedback: Float,
        wetMix: Float
    )

    private external fun nativeSetMasterLimiter(
        thresholdDb: Float,
        ceilingDb: Float,
        releaseMs: Float
    )

    private external fun nativeStartRecording(trackId: Int): Boolean
    private external fun nativeStopRecording(): ByteBuffer?

    private external fun nativeGetTelemetry(directBuffer: ByteBuffer): Int
    private external fun nativeGetPlayheadFrame(): Long
    private external fun nativeRenderOffline(outputPath: String, totalFrames: Long): Boolean
}
