package com.android.daw.domain.model

import kotlin.math.log10
import kotlin.math.max

/**
 * Global transport states for the DAW audio engine.
 */
enum class TransportState {
    STOPPED,
    PLAYING,
    PAUSED,
    RECORDING
}

/**
 * Stereo audio level representation for peak and RMS metering.
 *
 * All linear values are within range [0.0, 2.0] where 1.0 represents 0 dBFS (digital full scale).
 */
data class StereoLevel(
    val leftPeak: Float = 0f,
    val rightPeak: Float = 0f,
    val leftRms: Float = 0f,
    val rightRms: Float = 0f
) {
    val leftPeakDb: Float get() = linearToDb(leftPeak)
    val rightPeakDb: Float get() = linearToDb(rightPeak)
    val leftRmsDb: Float get() = linearToDb(leftRms)
    val rightRmsDb: Float get() = linearToDb(rightRms)

    companion object {
        const val MIN_DB = -60f

        fun linearToDb(linear: Float): Float {
            if (linear <= 0.000001f) return MIN_DB
            val db = 20f * log10(linear)
            return max(MIN_DB, db)
        }
    }
}

/**
 * Equalizer filter types supported by the 5-band biquad parametric EQ.
 */
enum class EqFilterType(val typeId: Int) {
    LOW_SHELF(0),
    PEAKING(1),
    HIGH_SHELF(2),
    LOW_PASS(3),
    HIGH_PASS(4);

    companion object {
        fun fromId(id: Int): EqFilterType = entries.firstOrNull { it.typeId == id } ?: PEAKING
    }
}

/**
 * Single band parameter set for the parametric EQ.
 */
data class EqBandModel(
    val bandIndex: Int,
    val filterType: EqFilterType,
    val frequencyHz: Float,
    val gainDb: Float,
    val qFactor: Float,
    val isBypassed: Boolean = false
)

/**
 * Returns the standard 5-band studio parametric EQ defaults.
 */
fun defaultEqBands(): List<EqBandModel> = listOf(
    EqBandModel(
        bandIndex = 0,
        filterType = EqFilterType.LOW_SHELF,
        frequencyHz = 80f,
        gainDb = 0f,
        qFactor = 0.707f
    ),
    EqBandModel(
        bandIndex = 1,
        filterType = EqFilterType.PEAKING,
        frequencyHz = 250f,
        gainDb = 0f,
        qFactor = 1.0f
    ),
    EqBandModel(
        bandIndex = 2,
        filterType = EqFilterType.PEAKING,
        frequencyHz = 1000f,
        gainDb = 0f,
        qFactor = 1.0f
    ),
    EqBandModel(
        bandIndex = 3,
        filterType = EqFilterType.PEAKING,
        frequencyHz = 4000f,
        gainDb = 0f,
        qFactor = 1.0f
    ),
    EqBandModel(
        bandIndex = 4,
        filterType = EqFilterType.HIGH_SHELF,
        frequencyHz = 12000f,
        gainDb = 0f,
        qFactor = 0.707f
    )
)

/**
 * Stereo delay effect parameter configuration.
 */
data class DelayModel(
    val delayMs: Float = 250f,
    val feedback: Float = 0.3f,
    val wetDry: Float = 0.2f,
    val isBypassed: Boolean = false
)

/**
 * Master lookahead limiter parameters.
 */
data class MasterLimiterModel(
    val thresholdDb: Float = -0.5f,
    val ceilingDb: Float = -0.1f,
    val releaseMs: Float = 50f,
    val isBypassed: Boolean = false
)

/**
 * Multi-resolution waveform decimation pyramid for responsive canvas rendering across zoom scales.
 *
 * Each level contains interleaved [min, max, rms] triplets.
 */
data class WaveformPyramid(
    val level1: FloatArray, // 4x decimation (4 audio frames per peak triplet)
    val level2: FloatArray, // 16x decimation (16 audio frames per peak triplet)
    val level3: FloatArray, // 64x decimation (64 audio frames per peak triplet)
    val level4: FloatArray, // 256x decimation (256 audio frames per peak triplet)
    val level0: FloatArray = FloatArray(0) // 1x raw peak triplets (optional)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WaveformPyramid) return false
        if (!level1.contentEquals(other.level1)) return false
        if (!level2.contentEquals(other.level2)) return false
        if (!level3.contentEquals(other.level3)) return false
        if (!level4.contentEquals(other.level4)) return false
        return level0.contentEquals(other.level0)
    }

    override fun hashCode(): Int {
        var result = level1.contentHashCode()
        result = 31 * result + level2.contentHashCode()
        result = 31 * result + level3.contentHashCode()
        result = 31 * result + level4.contentHashCode()
        result = 31 * result + level0.contentHashCode()
        return result
    }

    /**
     * Selects the most appropriate decimation level based on the current horizontal zoom level
     * (number of audio frames per visual screen pixel).
     */
    fun getLevelForZoom(framesPerPixel: Float): FloatArray = when {
        framesPerPixel <= 2f && level0.isNotEmpty() -> level0
        framesPerPixel <= 8f -> level1
        framesPerPixel <= 32f -> level2
        framesPerPixel <= 128f -> level3
        else -> level4
    }
}

/**
 * Timeline audio clip model representing an audio recording or imported WAV asset.
 */
data class AudioClipModel(
    val id: Int,
    val trackId: Int,
    val name: String,
    val startOffsetFrames: Long = 0L,
    val durationFrames: Long = 0L,
    val audioData: FloatArray? = null,
    val filePath: String? = null,
    val sampleRate: Int = 44100,
    val channels: Int = 2,
    val decimationPyramid: WaveformPyramid? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioClipModel) return false
        if (id != other.id) return false
        if (trackId != other.trackId) return false
        if (name != other.name) return false
        if (startOffsetFrames != other.startOffsetFrames) return false
        if (durationFrames != other.durationFrames) return false
        if (filePath != other.filePath) return false
        if (sampleRate != other.sampleRate) return false
        if (channels != other.channels) return false
        if (decimationPyramid != other.decimationPyramid) return false
        if (audioData != null) {
            if (other.audioData == null) return false
            if (!audioData.contentEquals(other.audioData)) return false
        } else if (other.audioData != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + trackId
        result = 31 * result + name.hashCode()
        result = 31 * result + startOffsetFrames.hashCode()
        result = 31 * result + durationFrames.hashCode()
        result = 31 * result + (filePath?.hashCode() ?: 0)
        result = 31 * result + sampleRate
        result = 31 * result + channels
        result = 31 * result + (decimationPyramid?.hashCode() ?: 0)
        result = 31 * result + (audioData?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Immutable UI model for an audio track lane and mixer channel strip.
 */
data class TrackUiModel(
    val id: Int,
    val name: String,
    val volume: Float = 1.0f,
    val pan: Float = 0.0f, // -1.0 (Left) to +1.0 (Right)
    val isMuted: Boolean = false,
    val isSoloed: Boolean = false,
    val isArmed: Boolean = false,
    val peakLeft: Float = 0f,
    val peakRight: Float = 0f,
    val rmsLeft: Float = 0f,
    val rmsRight: Float = 0f,
    val clips: List<AudioClipModel> = emptyList(),
    val eqBands: List<EqBandModel> = defaultEqBands(),
    val delay: DelayModel = DelayModel()
)

/**
 * Mutable container for high-frequency (60 Hz) telemetry polling into DirectByteBuffer without allocations.
 */
class TelemetryData(
    var playheadFrame: Long = 0L,
    var masterPeakLeft: Float = 0f,
    var masterPeakRight: Float = 0f,
    var masterRmsLeft: Float = 0f,
    var masterRmsRight: Float = 0f,
    var activeTrackCount: Int = 0,
    val trackPeaks: FloatArray = FloatArray(32),
    var isPlaying: Boolean = false,
    var isRecording: Boolean = false,
    var underrunCount: Int = 0
) {
    fun toSnapshot(): TelemetrySnapshot = TelemetrySnapshot(
        playheadFrame = playheadFrame,
        masterPeakLeft = masterPeakLeft,
        masterPeakRight = masterPeakRight,
        masterRmsLeft = masterRmsLeft,
        masterRmsRight = masterRmsRight,
        activeTrackCount = activeTrackCount,
        trackPeaks = trackPeaks.clone(),
        isPlaying = isPlaying,
        isRecording = isRecording,
        underrunCount = underrunCount
    )
}

/**
 * Immutable telemetry snapshot emitted via StateFlow for UI consumers.
 */
data class TelemetrySnapshot(
    val playheadFrame: Long = 0L,
    val masterPeakLeft: Float = 0f,
    val masterPeakRight: Float = 0f,
    val masterRmsLeft: Float = 0f,
    val masterRmsRight: Float = 0f,
    val activeTrackCount: Int = 0,
    val trackPeaks: FloatArray = FloatArray(32),
    val isPlaying: Boolean = false,
    val isRecording: Boolean = false,
    val underrunCount: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TelemetrySnapshot) return false
        if (playheadFrame != other.playheadFrame) return false
        if (masterPeakLeft != other.masterPeakLeft) return false
        if (masterPeakRight != other.masterPeakRight) return false
        if (masterRmsLeft != other.masterRmsLeft) return false
        if (masterRmsRight != other.masterRmsRight) return false
        if (activeTrackCount != other.activeTrackCount) return false
        if (!trackPeaks.contentEquals(other.trackPeaks)) return false
        if (isPlaying != other.isPlaying) return false
        if (isRecording != other.isRecording) return false
        return underrunCount == other.underrunCount
    }

    override fun hashCode(): Int {
        var result = playheadFrame.hashCode()
        result = 31 * result + masterPeakLeft.hashCode()
        result = 31 * result + masterPeakRight.hashCode()
        result = 31 * result + masterRmsLeft.hashCode()
        result = 31 * result + masterRmsRight.hashCode()
        result = 31 * result + activeTrackCount
        result = 31 * result + trackPeaks.contentHashCode()
        result = 31 * result + isPlaying.hashCode()
        result = 31 * result + isRecording.hashCode()
        result = 31 * result + underrunCount
        return result
    }
}

/**
 * Time signature representation.
 */
data class TimeSignature(
    val numerator: Int = 4,
    val denominator: Int = 4
)

/**
 * Musical timecode position in Bars, Beats, and Ticks.
 */
data class BbtTimecode(
    val bar: Int,
    val beat: Int,
    val tick: Int
)
