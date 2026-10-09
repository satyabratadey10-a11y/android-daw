package com.android.daw.viewmodel

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * StudioUiState
 *
 * Immutable, unidirectional data flow (UDF / MVI) state model.
 * Adheres strictly to android-ui-ux-architect Rule 9 (Anti-Drift State Hoisting).
 */

enum class TransportState {
    STOPPED,
    PLAYING,
    RECORDING,
    PAUSED
}

enum class FilterType {
    LOW_SHELF,
    PEAKING,
    HIGH_SHELF,
    LOW_PASS,
    HIGH_PASS
}

@Immutable
data class StereoLevel(
    val peakLeft: Float = 0f,
    val peakRight: Float = 0f,
    val rmsLeft: Float = 0f,
    val rmsRight: Float = 0f,
    val clipLeft: Boolean = false,
    val clipRight: Boolean = false
) {
    val peakMax: Float get() = maxOf(peakLeft, peakRight)
    val rmsMax: Float get() = maxOf(rmsLeft, rmsRight)
    val hasClipped: Boolean get() = clipLeft || clipRight
}

@Immutable
data class EqBandState(
    val bandIndex: Int,
    val filterType: FilterType = FilterType.PEAKING,
    val frequencyHz: Float = 1000f,
    val gainDb: Float = 0f,
    val q: Float = 1.0f,
    val isEnabled: Boolean = true,
    val colorHex: Long = 0xFF00E5FF
) {
    val color: Color get() = Color(colorHex)

    /**
     * Calculates the exact analytical magnitude response in decibels at frequency [f]
     * using the Robert Bristow-Johnson (RBJ) Audio EQ Cookbook biquad coefficients.
     */
    fun calculateGainAtFrequency(f: Float, sampleRate: Int = 44100): Float {
        if (!isEnabled || gainDb == 0f && filterType == FilterType.PEAKING) {
            return 0f
        }

        val w0 = 2.0 * PI * (frequencyHz.coerceIn(20f, sampleRate / 2.1f) / sampleRate)
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val effectiveQ = q.coerceIn(0.1f, 15f).toDouble()
        val alpha = sinW0 / (2.0 * effectiveQ)
        val a = 10.0.pow(gainDb.toDouble() / 40.0) // sqrt(10^(gainDb/20))

        val b0: Double
        val b1: Double
        val b2: Double
        val a0: Double
        val a1: Double
        val a2: Double

        when (filterType) {
            FilterType.PEAKING -> {
                b0 = 1.0 + alpha * a
                b1 = -2.0 * cosW0
                b2 = 1.0 - alpha * a
                a0 = 1.0 + alpha / a
                a1 = -2.0 * cosW0
                a2 = 1.0 - alpha / a
            }
            FilterType.LOW_SHELF -> {
                val sqrtA = sqrt(a)
                b0 = a * ((a + 1.0) - (a - 1.0) * cosW0 + 2.0 * sqrtA * alpha)
                b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)
                b2 = a * ((a + 1.0) - (a - 1.0) * cosW0 - 2.0 * sqrtA * alpha)
                a0 = (a + 1.0) + (a - 1.0) * cosW0 + 2.0 * sqrtA * alpha
                a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0)
                a2 = (a + 1.0) + (a - 1.0) * cosW0 - 2.0 * sqrtA * alpha
            }
            FilterType.HIGH_SHELF -> {
                val sqrtA = sqrt(a)
                b0 = a * ((a + 1.0) + (a - 1.0) * cosW0 + 2.0 * sqrtA * alpha)
                b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)
                b2 = a * ((a + 1.0) + (a - 1.0) * cosW0 - 2.0 * sqrtA * alpha)
                a0 = (a + 1.0) - (a - 1.0) * cosW0 + 2.0 * sqrtA * alpha
                a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0)
                a2 = (a + 1.0) - (a - 1.0) * cosW0 - 2.0 * sqrtA * alpha
            }
            FilterType.LOW_PASS -> {
                b0 = (1.0 - cosW0) / 2.0
                b1 = 1.0 - cosW0
                b2 = (1.0 - cosW0) / 2.0
                a0 = 1.0 + alpha
                a1 = -2.0 * cosW0
                a2 = 1.0 - alpha
            }
            FilterType.HIGH_PASS -> {
                b0 = (1.0 + cosW0) / 2.0
                b1 = -(1.0 + cosW0)
                b2 = (1.0 + cosW0) / 2.0
                a0 = 1.0 + alpha
                a1 = -2.0 * cosW0
                a2 = 1.0 - alpha
            }
        }

        // Evaluate |H(e^(j*w))|^2 at input frequency f
        val w = 2.0 * PI * (f.coerceIn(10f, sampleRate / 2.05f) / sampleRate)
        val cosW = cos(w)
        val cos2W = cos(2.0 * w)
        val sinW = sin(w)
        val sin2W = sin(2.0 * w)

        val numReal = b0 + b1 * cosW + b2 * cos2W
        val numImag = -(b1 * sinW + b2 * sin2W)
        val denReal = a0 + a1 * cosW + a2 * cos2W
        val denImag = -(a1 * sinW + a2 * sin2W)

        val numMagSq = numReal * numReal + numImag * numImag
        val denMagSq = denReal * denReal + denImag * denImag

        if (denMagSq <= 1e-12) return 0f
        val magSq = numMagSq / denMagSq
        val gain = 10.0 * log10(maxOf(1e-9, magSq))
        return gain.toFloat().coerceIn(-48f, 48f)
    }
}

@Immutable
data class DelayParamsState(
    val timeMs: Float = 250f,
    val feedback: Float = 0.35f,
    val wetDry: Float = 0.30f,
    val isPingPong: Boolean = true,
    val isTempoSynced: Boolean = false,
    val isEnabled: Boolean = true
)

@Immutable
data class LimiterParamsState(
    val thresholdDb: Float = -1.0f,
    val ceilingDb: Float = -0.1f,
    val releaseMs: Float = 50.0f,
    val gainReductionDb: Float = 0.0f,
    val isEnabled: Boolean = true
)

/**
 * Multi-resolution peak decimation block hierarchy (Audio Mipmapping)
 */
@Immutable
class DecimationLevel(
    val samplesPerBlock: Int,
    val minPeaks: FloatArray,
    val maxPeaks: FloatArray,
    val rmsPeaks: FloatArray
) {
    val blockCount: Int get() = minPeaks.size
}

@Immutable
class WaveformDecimationPyramid(
    val sampleRate: Int = 44100,
    val totalSamples: Long = 0L,
    val levels: List<DecimationLevel> = emptyList()
) {
    fun selectOptimalLevel(pixelsPerSecond: Float): DecimationLevel {
        if (levels.isEmpty()) {
            return EMPTY_LEVEL
        }
        val samplesPerPixel = sampleRate / maxOf(1f, pixelsPerSecond)
        return levels.minByOrNull { level ->
            kotlin.math.abs(level.samplesPerBlock - samplesPerPixel)
        } ?: levels[0]
    }

    companion object {
        val EMPTY_LEVEL = DecimationLevel(
            samplesPerBlock = 256,
            minPeaks = FloatArray(0),
            maxPeaks = FloatArray(0),
            rmsPeaks = FloatArray(0)
        )
    }
}

@Immutable
data class AudioClipUiModel(
    val clipId: Int,
    val trackId: Int,
    val name: String,
    val startFrame: Long,
    val durationFrames: Long,
    val waveformPeakData: WaveformDecimationPyramid? = null
)

@Immutable
data class TrackUiModel(
    val trackId: Int,
    val name: String,
    val colorHex: Long = 0xFF00E5FF,
    val volumeLinear: Float = 1.0f,
    val pan: Float = 0.0f, // -1.0f (L) to +1.0f (R)
    val isMuted: Boolean = false,
    val isSoloed: Boolean = false,
    val isArmed: Boolean = false,
    val clips: List<AudioClipUiModel> = emptyList(),
    val levels: StereoLevel = StereoLevel(),
    val eqBands: List<EqBandState> = defaultEqBands(),
    val delayParams: DelayParamsState = DelayParamsState()
) {
    val color: Color get() = Color(colorHex)

    // Volume in dBFS
    val volumeDb: Float
        get() {
            return if (volumeLinear <= 0.00001f) {
                -60.0f
            } else if (volumeLinear <= 1.0f) {
                // Logarithmic mapping: 0dB is at volumeLinear = 1.0
                20.0f * log10(volumeLinear)
            } else {
                20.0f * log10(volumeLinear)
            }
        }

    // Pan readout format: L50, C, R50
    val panReadout: String
        get() {
            val rounded = (pan * 100f).toInt()
            return when {
                rounded < -2 -> "L${-rounded}"
                rounded > 2 -> "R$rounded"
                else -> "C"
            }
        }

    companion object {
        fun defaultEqBands(): List<EqBandState> = listOf(
            EqBandState(bandIndex = 0, filterType = FilterType.LOW_SHELF, frequencyHz = 100f, gainDb = 0f, q = 0.7f, colorHex = 0xFFFF4081),
            EqBandState(bandIndex = 1, filterType = FilterType.PEAKING, frequencyHz = 500f, gainDb = 0f, q = 1.4f, colorHex = 0xFFFFD740),
            EqBandState(bandIndex = 2, filterType = FilterType.PEAKING, frequencyHz = 2500f, gainDb = 0f, q = 1.4f, colorHex = 0xFF00E5FF),
            EqBandState(bandIndex = 3, filterType = FilterType.HIGH_SHELF, frequencyHz = 8000f, gainDb = 0f, q = 0.7f, colorHex = 0xFF69F0AE)
        )
    }
}

@Immutable
data class StudioUiState(
    val transportState: TransportState = TransportState.STOPPED,
    val currentFrame: Long = 0L,
    val totalFrames: Long = 44100L * 30L, // 30 seconds default timeline
    val sampleRate: Int = 44100,
    val bpm: Double = 120.0,
    val beatsPerBar: Int = 4,
    val beatUnit: Int = 4,
    val isLooping: Boolean = false,
    val loopStartFrame: Long = 0L,
    val loopEndFrame: Long = 44100L * 16L, // 4 bars at 120 BPM
    val tracks: List<TrackUiModel> = emptyList(),
    val masterLevels: StereoLevel = StereoLevel(),
    val masterVolumeLinear: Float = 1.0f,
    val masterPan: Float = 0.0f,
    val masterLimiter: LimiterParamsState = LimiterParamsState(),
    val selectedTrackId: Int? = null,
    val isMixerOpen: Boolean = false,
    val isInstrumentPanelOpen: Boolean = false,
    val isPianoRollOpen: Boolean = false,
    val isProjectDrawerOpen: Boolean = false,
    val isModulesDrawerOpen: Boolean = false,
    val isBpmDialOpen: Boolean = false,
    val selectedClipId: Int? = null,
    val isClipMenuOpen: Boolean = false,
    val cpuUsagePercent: Float = 14.5f,
    val ramUsageMb: Int = 186,
    val isMixerViewActive: Boolean = false,
    val isDspInspectorOpen: Boolean = false,
    val dspTargetTrackId: Int? = null, // null = Master bus
    val zoomPixelsPerSecond: Float = 100f,
    val horizontalScrollOffsetPx: Float = 0f,
    val isExporting: Boolean = false,
    val exportProgressPercent: Int = 0,
    val statusMessage: String? = null
) {
    val isPlaying: Boolean get() = transportState == TransportState.PLAYING
    val isRecording: Boolean get() = transportState == TransportState.RECORDING

    val selectedTrack: TrackUiModel?
        get() = tracks.firstOrNull { it.trackId == selectedTrackId } ?: tracks.firstOrNull()

    // Formatted musical time BBT (Bar:Beat:Tick)
    val musicalTimecode: String
        get() {
            val seconds = currentFrame.toDouble() / sampleRate
            val beatsTotal = seconds * (bpm / 60.0)
            val bar = (beatsTotal / beatsPerBar).toInt() + 1
            val beat = (beatsTotal % beatsPerBar).toInt() + 1
            val ticksPerBeat = 960
            val tick = ((beatsTotal - beatsTotal.toLong()) * ticksPerBeat).toInt()
            return String.format("%03d : %02d : %03d", bar, beat, tick)
        }

    // Formatted wall clock time MM:SS.mmm
    val wallClockTimecode: String
        get() {
            val totalMillis = (currentFrame * 1000L) / sampleRate
            val minutes = (totalMillis / 60000L).toInt()
            val seconds = ((totalMillis % 60000L) / 1000L).toInt()
            val millis = (totalMillis % 1000L).toInt()
            return String.format("%02d:%02d.%03d", minutes, seconds, millis)
        }
}
