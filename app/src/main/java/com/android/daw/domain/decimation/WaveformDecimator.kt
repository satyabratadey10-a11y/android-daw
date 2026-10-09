package com.android.daw.domain.decimation

import com.android.daw.domain.model.WaveformPyramid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * High-performance multi-resolution waveform decimation engine.
 *
 * Computes a 5-level peak decimation pyramid (factors 1x, 4x, 16x, 64x, 256x)
 * containing interleaved [min, max, rms] triplets. Executed asynchronously on
 * [Dispatchers.Default] ensuring zero UI thread blocking.
 */
object WaveformDecimator {

    const val DECIMATION_FACTOR_LEVEL_1 = 4
    const val DECIMATION_FACTOR_LEVEL_2 = 16
    const val DECIMATION_FACTOR_LEVEL_3 = 64
    const val DECIMATION_FACTOR_LEVEL_4 = 256

    // Number of floats per decimation bucket: min, max, rms
    const val FLOATS_PER_BUCKET = 3

    /**
     * Asynchronously computes a [WaveformPyramid] from raw PCM floating-point audio data.
     *
     * @param audioData Interleaved 32-bit floating-point audio samples.
     * @param channels Number of audio channels (1 for Mono, 2 for Stereo).
     * @param includeLevel0 If true and audio data length <= 131072, includes 1x raw peak triplets.
     * @param dispatcher Coroutine dispatcher (defaults to Dispatchers.Default).
     */
    suspend fun computeDecimation(
        audioData: FloatArray,
        channels: Int = 1,
        includeLevel0: Boolean = false,
        dispatcher: CoroutineDispatcher = Dispatchers.Default
    ): WaveformPyramid = withContext(dispatcher) {
        if (audioData.isEmpty() || channels <= 0) {
            return@withContext WaveformPyramid(
                level1 = FloatArray(0),
                level2 = FloatArray(0),
                level3 = FloatArray(0),
                level4 = FloatArray(0),
                level0 = FloatArray(0)
            )
        }

        val totalFrames = audioData.size / channels
        if (totalFrames == 0) {
            return@withContext WaveformPyramid(
                level1 = FloatArray(0),
                level2 = FloatArray(0),
                level3 = FloatArray(0),
                level4 = FloatArray(0),
                level0 = FloatArray(0)
            )
        }

        // Level 0 (optional 1:1 raw triplets for micro-level sample editing)
        val level0 = if (includeLevel0 && totalFrames <= 131072) {
            computeLevel0Triplets(audioData, channels, totalFrames)
        } else {
            FloatArray(0)
        }

        // Level 1: 4x decimation directly from raw audio frames
        val level1 = computeLevel1Triplets(audioData, channels, totalFrames, DECIMATION_FACTOR_LEVEL_1)

        // Level 2: 16x decimation (hierarchically reduced 4x from Level 1)
        val level2 = reduceLevelHierarchical(level1, 4)

        // Level 3: 64x decimation (hierarchically reduced 4x from Level 2)
        val level3 = reduceLevelHierarchical(level2, 4)

        // Level 4: 256x decimation (hierarchically reduced 4x from Level 3)
        val level4 = reduceLevelHierarchical(level3, 4)

        WaveformPyramid(
            level1 = level1,
            level2 = level2,
            level3 = level3,
            level4 = level4,
            level0 = level0
        )
    }

    /**
     * Computes 1:1 peak triplets [min, max, rms] for raw audio frames.
     */
    private fun computeLevel0Triplets(audioData: FloatArray, channels: Int, totalFrames: Int): FloatArray {
        val result = FloatArray(totalFrames * FLOATS_PER_BUCKET)
        var outIdx = 0

        for (frame in 0 until totalFrames) {
            val sample = if (channels == 1) {
                audioData[frame]
            } else {
                val base = frame * channels
                (audioData[base] + audioData[base + 1]) * 0.5f
            }

            val clampedSample = sample.coerceIn(-1.5f, 1.5f)
            val minVal = if (clampedSample < 0f) clampedSample else 0f
            val maxVal = if (clampedSample > 0f) clampedSample else 0f
            val rmsVal = kotlin.math.abs(clampedSample)

            result[outIdx++] = minVal
            result[outIdx++] = maxVal
            result[outIdx++] = rmsVal
        }

        return result
    }

    /**
     * Computes Level 1 decimation [min, max, rms] triplets directly from raw interleaved PCM frames.
     */
    private fun computeLevel1Triplets(
        audioData: FloatArray,
        channels: Int,
        totalFrames: Int,
        factor: Int
    ): FloatArray {
        val numBuckets = (totalFrames + factor - 1) / factor
        val result = FloatArray(numBuckets * FLOATS_PER_BUCKET)
        var outIdx = 0

        var frame = 0
        while (frame < totalFrames) {
            val bucketEnd = min(frame + factor, totalFrames)
            val bucketSize = bucketEnd - frame

            var bMin = 0.0f
            var bMax = 0.0f
            var sumSquares = 0.0

            for (f in frame until bucketEnd) {
                val sample = if (channels == 1) {
                    audioData[f]
                } else {
                    val base = f * channels
                    (audioData[base] + audioData[base + 1]) * 0.5f
                }

                if (sample < bMin) bMin = sample
                if (sample > bMax) bMax = sample
                sumSquares += (sample * sample).toDouble()
            }

            val rms = if (bucketSize > 0) sqrt(sumSquares / bucketSize).toFloat() else 0.0f

            result[outIdx++] = bMin.coerceIn(-1.5f, 0.0f)
            result[outIdx++] = bMax.coerceIn(0.0f, 1.5f)
            result[outIdx++] = rms.coerceIn(0.0f, 1.5f)

            frame = bucketEnd
        }

        return result
    }

    /**
     * Hierarchically reduces a source decimation level by a reduction factor [groupSize] (typically 4).
     *
     * Source level format: [min0, max0, rms0, min1, max1, rms1, ...]
     * Reduction logic:
     * - New min = min(min0, min1, min2, min3)
     * - New max = max(max0, max1, max2, max3)
     * - New rms = sqrt((rms0^2 + rms1^2 + rms2^2 + rms3^2) / count)
     */
    private fun reduceLevelHierarchical(source: FloatArray, groupSize: Int): FloatArray {
        val sourceBuckets = source.size / FLOATS_PER_BUCKET
        if (sourceBuckets == 0) return FloatArray(0)

        val targetBuckets = (sourceBuckets + groupSize - 1) / groupSize
        val result = FloatArray(targetBuckets * FLOATS_PER_BUCKET)
        var outIdx = 0

        var bIdx = 0
        while (bIdx < sourceBuckets) {
            val groupEnd = min(bIdx + groupSize, sourceBuckets)
            val count = groupEnd - bIdx

            var gMin = 0.0f
            var gMax = 0.0f
            var sumRmsSq = 0.0

            for (i in bIdx until groupEnd) {
                val srcBase = i * FLOATS_PER_BUCKET
                val sMin = source[srcBase]
                val sMax = source[srcBase + 1]
                val sRms = source[srcBase + 2]

                if (sMin < gMin) gMin = sMin
                if (sMax > gMax) gMax = sMax
                sumRmsSq += (sRms * sRms).toDouble()
            }

            val gRms = if (count > 0) sqrt(sumRmsSq / count).toFloat() else 0.0f

            result[outIdx++] = gMin
            result[outIdx++] = gMax
            result[outIdx++] = gRms

            bIdx = groupEnd
        }

        return result
    }
}
