package com.android.daw

import com.android.daw.domain.decimation.WaveformDecimator
import com.android.daw.fixtures.SyntheticAudioGenerators
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Unit test suite for [WaveformDecimator].
 * Validates 5-level multi-resolution decimation pyramid construction,
 * interleaved [min, max, rms] triplet accuracy, boundary cases, and stereo downmixing.
 */
class WaveformDecimatorTest {

    @Test
    fun testEmptyAndZeroChannelInputs() = runTest {
        val emptyPyramid = WaveformDecimator.computeDecimation(FloatArray(0))
        assertEquals(0, emptyPyramid.level1.size)
        assertEquals(0, emptyPyramid.level2.size)
        assertEquals(0, emptyPyramid.level3.size)
        assertEquals(0, emptyPyramid.level4.size)
        assertEquals(0, emptyPyramid.level0.size)

        val invalidChannelPyramid = WaveformDecimator.computeDecimation(FloatArray(100), channels = 0)
        assertEquals(0, invalidChannelPyramid.level1.size)
    }

    @Test
    fun testSingleSampleBuffer() = runTest {
        val singleSample = floatArrayOf(0.5f)
        val pyramid = WaveformDecimator.computeDecimation(singleSample, channels = 1, includeLevel0 = true)

        // Level 0: 1 sample = 1 bucket = 3 floats [min, max, rms]
        assertEquals(3, pyramid.level0.size)
        assertEquals(0.0f, pyramid.level0[0], 1e-6f) // min for positive sample clamped to 0
        assertEquals(0.5f, pyramid.level0[1], 1e-6f) // max
        assertEquals(0.5f, pyramid.level0[2], 1e-6f) // rms

        // Level 1: 1 bucket = 3 floats
        assertEquals(3, pyramid.level1.size)
        // Level 2, 3, 4: 1 bucket each
        assertEquals(3, pyramid.level2.size)
        assertEquals(3, pyramid.level3.size)
        assertEquals(3, pyramid.level4.size)
    }

    @Test
    fun testPyramidBucketLengths() = runTest {
        val totalFrames = 1024
        val audio = SyntheticAudioGenerators.generateSine(440f, durationSec = totalFrames / 48000f, sampleRate = 48000)
        val pyramid = WaveformDecimator.computeDecimation(audio, channels = 1)

        val expectedL1Buckets = (audio.size + 4 - 1) / 4
        val expectedL2Buckets = (expectedL1Buckets + 4 - 1) / 4
        val expectedL3Buckets = (expectedL2Buckets + 4 - 1) / 4
        val expectedL4Buckets = (expectedL3Buckets + 4 - 1) / 4

        assertEquals(expectedL1Buckets * 3, pyramid.level1.size)
        assertEquals(expectedL2Buckets * 3, pyramid.level2.size)
        assertEquals(expectedL3Buckets * 3, pyramid.level3.size)
        assertEquals(expectedL4Buckets * 3, pyramid.level4.size)
    }

    @Test
    fun testSineWavePeakAndRmsAccuracy() = runTest {
        val duration = 0.5f
        val sampleRate = 48000
        val amplitude = 0.8f
        val audio = SyntheticAudioGenerators.generateSine(440f, duration, sampleRate, amplitude)
        val pyramid = WaveformDecimator.computeDecimation(audio, channels = 1)

        // Find max and min in Level 1
        var l1Max = 0f
        var l1Min = 0f
        for (i in 0 until (pyramid.level1.size / 3)) {
            val minVal = pyramid.level1[i * 3]
            val maxVal = pyramid.level1[i * 3 + 1]
            if (minVal < l1Min) l1Min = minVal
            if (maxVal > l1Max) l1Max = maxVal
        }

        assertTrue("Level 1 max ($l1Max) should reach ~0.8f", abs(l1Max - amplitude) < 0.05f)
        assertTrue("Level 1 min ($l1Min) should reach ~ -0.8f", abs(l1Min + amplitude) < 0.05f)

        // Coarser levels must maintain envelope hierarchy
        var l4Max = 0f
        var l4Min = 0f
        for (i in 0 until (pyramid.level4.size / 3)) {
            val minVal = pyramid.level4[i * 3]
            val maxVal = pyramid.level4[i * 3 + 1]
            if (minVal < l4Min) l4Min = minVal
            if (maxVal > l4Max) l4Max = maxVal
        }

        assertTrue("Level 4 max envelope should preserve peak", abs(l4Max - amplitude) < 0.05f)
        assertTrue("Level 4 min envelope should preserve trough", abs(l4Min + amplitude) < 0.05f)
    }

    @Test
    fun testDigitalSilenceDecimation() = runTest {
        val silence = FloatArray(4800) // 100ms at 48k
        val pyramid = WaveformDecimator.computeDecimation(silence, channels = 1)

        for (v in pyramid.level1) {
            assertEquals(0f, v, 1e-6f)
        }
        for (v in pyramid.level4) {
            assertEquals(0f, v, 1e-6f)
        }
    }

    @Test
    fun testSquareWaveDecimation() = runTest {
        val square = SyntheticAudioGenerators.generateSquareWave(100f, 0.1f, 48000, 1.0f)
        val pyramid = WaveformDecimator.computeDecimation(square, channels = 1)

        // In a unit square wave, max peak = 1.0, min peak = -1.0, rms = 1.0
        var maxPeak = 0f
        var minPeak = 0f
        for (i in 0 until (pyramid.level1.size / 3)) {
            if (pyramid.level1[i * 3 + 1] > maxPeak) maxPeak = pyramid.level1[i * 3 + 1]
            if (pyramid.level1[i * 3] < minPeak) minPeak = pyramid.level1[i * 3]
        }
        assertEquals(1.0f, maxPeak, 1e-6f)
        assertEquals(-1.0f, minPeak, 1e-6f)
    }

    @Test
    fun testStereoChannelDownmixing() = runTest {
        // Channel 0 = +1.0, Channel 1 = -1.0 -> average = 0.0
        val stereoData = FloatArray(200)
        for (i in 0 until 100) {
            stereoData[i * 2] = 1.0f
            stereoData[i * 2 + 1] = -1.0f
        }

        val pyramid = WaveformDecimator.computeDecimation(stereoData, channels = 2)
        for (i in 0 until (pyramid.level1.size / 3)) {
            val maxVal = pyramid.level1[i * 3 + 1]
            val minVal = pyramid.level1[i * 3]
            val rmsVal = pyramid.level1[i * 3 + 2]
            assertEquals(0f, maxVal, 1e-6f)
            assertEquals(0f, minVal, 1e-6f)
            assertEquals(0f, rmsVal, 1e-6f)
        }
    }

    @Test
    fun testLargeAudioBufferDecimation() = runTest {
        // 96,000 samples (2 seconds at 48kHz)
        val largeBuffer = SyntheticAudioGenerators.generateSine(1000f, 2.0f, 48000, 0.5f)
        val pyramid = WaveformDecimator.computeDecimation(largeBuffer, channels = 1)

        assertTrue(pyramid.level1.isNotEmpty())
        assertTrue(pyramid.level2.isNotEmpty())
        assertTrue(pyramid.level3.isNotEmpty())
        assertTrue(pyramid.level4.isNotEmpty())

        // Verify Level 4 has ceil(96000 / 256) buckets = 375 buckets = 1125 floats
        val expectedL4Buckets = (96000 + 256 - 1) / 256
        assertEquals(expectedL4Buckets * 3, pyramid.level4.size)
    }
}
