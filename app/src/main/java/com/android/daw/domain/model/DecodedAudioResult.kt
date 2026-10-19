package com.android.daw.domain.model

import com.android.daw.viewmodel.DecimationLevel
import com.android.daw.viewmodel.WaveformDecimationPyramid

/**
 * Result data class produced by native C++ AudioDecoder via JNI.
 * Contains audio stream properties and the 3-level min/max peak decimation pyramid.
 */
class DecodedAudioResult(
    val success: Boolean,
    val errorMessage: String,
    val sampleRate: Int,
    val channels: Int,
    val totalFrames: Long,
    val minPeaks64: FloatArray,
    val maxPeaks64: FloatArray,
    val rmsPeaks64: FloatArray,
    val minPeaks512: FloatArray,
    val maxPeaks512: FloatArray,
    val rmsPeaks512: FloatArray,
    val minPeaks4096: FloatArray,
    val maxPeaks4096: FloatArray,
    val rmsPeaks4096: FloatArray
) {
    /**
     * Convert to high-performance WaveformDecimationPyramid for timeline rendering.
     */
    fun toPyramid(): WaveformDecimationPyramid {
        val level64 = DecimationLevel(64, minPeaks64, maxPeaks64, rmsPeaks64)
        val level512 = DecimationLevel(512, minPeaks512, maxPeaks512, rmsPeaks512)
        val level4096 = DecimationLevel(4096, minPeaks4096, maxPeaks4096, rmsPeaks4096)
        return WaveformDecimationPyramid(
            sampleRate = sampleRate,
            totalSamples = totalFrames,
            levels = listOf(level64, level512, level4096)
        )
    }
}
