package com.android.daw.fixtures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Verification suite for [SyntheticAudioGenerators].
 * Ensures signal generation formulas, noise algorithms, and WAV serialization
 * operate with genuine mathematical correctness.
 */
class SyntheticAudioGeneratorsTest {

    @Test
    fun testSineGenerationCharacteristics() {
        val sampleRate = 48000
        val freq = 440f
        val duration = 0.5f // 24000 samples
        val targetAmp = 0.8f

        val sine = SyntheticAudioGenerators.generateSine(
            freqHz = freq,
            durationSec = duration,
            sampleRate = sampleRate,
            amplitude = targetAmp
        )

        assertEquals(24000, sine.size)

        var maxVal = Float.MIN_VALUE
        var minVal = Float.MAX_VALUE
        var sumSquares = 0.0

        for (sample in sine) {
            if (sample > maxVal) maxVal = sample
            if (sample < minVal) minVal = sample
            sumSquares += (sample * sample).toDouble()
        }

        // Peak values should match amplitude within 1%
        assertTrue("Max peak should be close to 0.8f, got $maxVal", abs(maxVal - targetAmp) < 0.01f)
        assertTrue("Min peak should be close to -0.8f, got $minVal", abs(minVal + targetAmp) < 0.01f)

        // RMS of sine wave = A / sqrt(2)
        val expectedRms = (targetAmp / sqrt(2.0)).toFloat()
        val actualRms = sqrt(sumSquares / sine.size).toFloat()
        assertTrue("Sine RMS should be ~$expectedRms, got $actualRms", abs(actualRms - expectedRms) < 0.02f)
    }

    @Test
    fun testLinearChirpSweep() {
        val sampleRate = 48000
        val duration = 1.0f
        val chirp = SyntheticAudioGenerators.generateChirp(
            startFreqHz = 100f,
            endFreqHz = 10000f,
            durationSec = duration,
            sampleRate = sampleRate,
            isLogarithmic = false,
            amplitude = 0.75f
        )

        assertEquals(48000, chirp.size)

        // Check bounds
        for (sample in chirp) {
            assertTrue("Sample out of bounds: $sample", abs(sample) <= 0.751f)
        }

        // Early window (near 100Hz) should have fewer zero-crossings than late window (near 10kHz)
        val earlyCrossings = countZeroCrossings(chirp, 0, 4800)
        val lateCrossings = countZeroCrossings(chirp, 43200, 48000)
        assertTrue(
            "Late window should have significantly more zero crossings than early window (early: $earlyCrossings, late: $lateCrossings)",
            lateCrossings > earlyCrossings * 5
        )
    }

    @Test
    fun testLogarithmicChirpSweep() {
        val sampleRate = 48000
        val duration = 0.5f
        val chirp = SyntheticAudioGenerators.generateChirp(
            startFreqHz = 50f,
            endFreqHz = 8000f,
            durationSec = duration,
            sampleRate = sampleRate,
            isLogarithmic = true,
            amplitude = 0.9f
        )

        assertEquals(24000, chirp.size)
        for (sample in chirp) {
            assertTrue("Sample out of bounds: $sample", abs(sample) <= 0.901f)
        }
    }

    @Test
    fun testImpulseTrainSpacing() {
        val period = 100
        val total = 1000
        val train = SyntheticAudioGenerators.generateImpulseTrain(periodSamples = period, totalSamples = total, amplitude = 1.0f)

        assertEquals(total, train.size)
        for (i in 0 until total) {
            if (i % period == 0) {
                assertEquals(1.0f, train[i], 1e-6f)
            } else {
                assertEquals(0.0f, train[i], 1e-6f)
            }
        }
    }

    @Test
    fun testWhiteNoiseDistribution() {
        val duration = 0.5f
        val sampleRate = 48000
        val amp = 0.6f
        val noise = SyntheticAudioGenerators.generateWhiteNoise(duration, sampleRate, amp, seed = 12345L)

        assertEquals(24000, noise.size)

        var sum = 0.0
        for (sample in noise) {
            assertTrue("Sample exceeded amplitude: $sample", abs(sample) <= amp)
            sum += sample.toDouble()
        }

        // Expected mean near zero
        val mean = sum / noise.size
        assertTrue("White noise mean should be near 0, got $mean", abs(mean) < 0.05)
    }

    @Test
    fun testPinkNoiseCharacteristics() {
        val duration = 0.5f
        val sampleRate = 48000
        val amp = 0.7f
        val pink = SyntheticAudioGenerators.generatePinkNoise(duration, sampleRate, amp, seed = 999L)

        assertEquals(24000, pink.size)

        var hasNonZero = false
        for (sample in pink) {
            assertTrue("Pink noise sample exceeded amplitude: $sample", abs(sample) <= amp)
            if (abs(sample) > 0.01f) hasNonZero = true
        }
        assertTrue("Pink noise must contain audible energy", hasNonZero)

        // Repeatability with same seed
        val pink2 = SyntheticAudioGenerators.generatePinkNoise(duration, sampleRate, amp, seed = 999L)
        for (i in 0 until 100) {
            assertEquals(pink[i], pink2[i], 1e-6f)
        }
    }

    @Test
    fun testHarmonicProbeGeneration() {
        val fundamental = 200f
        val probe = SyntheticAudioGenerators.generateHarmonicProbe(
            fundamentalHz = fundamental,
            durationSec = 0.1f,
            sampleRate = 48000,
            numHarmonics = 4,
            oddOnly = true,
            amplitude = 0.8f
        )

        assertEquals(4800, probe.size)
        for (sample in probe) {
            assertTrue("Probe exceeded amplitude: $sample", abs(sample) <= 0.801f)
        }
    }

    @Test
    fun testWav16BitRoundtrip() {
        val sampleRate = 44100
        val sine = SyntheticAudioGenerators.generateSine(440f, 0.2f, sampleRate, 0.7f)
        val wavBytes = SyntheticAudioGenerators.createWavBytes(sine, sampleRate = sampleRate, bitDepth = 16, numChannels = 1)

        val expectedByteCount = 44 + (sine.size * 2)
        assertEquals(expectedByteCount, wavBytes.size)

        val decoded = SyntheticAudioGenerators.parseWavBytes(wavBytes)
        assertEquals(sampleRate, decoded.sampleRate)
        assertEquals(16, decoded.bitDepth)
        assertEquals(1, decoded.numChannels)
        assertEquals(sine.size, decoded.audioData.size)

        // Verify PCM accuracy (16-bit quantization noise is <= 1 / 32767 ~ 0.00003)
        for (i in sine.indices) {
            assertEquals(sine[i], decoded.audioData[i], 0.0001f)
        }
    }

    @Test
    fun testWav24BitRoundtrip() {
        val sampleRate = 48000
        val sine = SyntheticAudioGenerators.generateSine(1000f, 0.1f, sampleRate, 0.85f)
        val wavBytes = SyntheticAudioGenerators.createWavBytes(sine, sampleRate = sampleRate, bitDepth = 24, numChannels = 1)

        val expectedByteCount = 44 + (sine.size * 3)
        assertEquals(expectedByteCount, wavBytes.size)

        val decoded = SyntheticAudioGenerators.parseWavBytes(wavBytes)
        assertEquals(sampleRate, decoded.sampleRate)
        assertEquals(24, decoded.bitDepth)
        assertEquals(1, decoded.numChannels)
        assertEquals(sine.size, decoded.audioData.size)

        // 24-bit quantization error is ~1 / 8388607
        for (i in sine.indices) {
            assertEquals(sine[i], decoded.audioData[i], 0.00001f)
        }
    }

    private fun countZeroCrossings(audio: FloatArray, start: Int, end: Int): Int {
        var count = 0
        for (i in start until (end - 1)) {
            if ((audio[i] >= 0f && audio[i + 1] < 0f) || (audio[i] < 0f && audio[i + 1] >= 0f)) {
                count++
            }
        }
        return count
    }
}
