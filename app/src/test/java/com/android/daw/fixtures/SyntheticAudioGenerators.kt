package com.android.daw.fixtures

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/**
 * Synthetic Audio Signal Generators for reproducible, mathematical test verification.
 *
 * Implements:
 * 1. Pure Sine wave generator with configurable frequency, amplitude, and sample rate.
 * 2. Frequency sweep (Linear and Logarithmic chirp).
 * 3. Dirac impulse and impulse train generator for filter/impulse response validation.
 * 4. White noise generator (uniform pseudo-random sequence).
 * 5. Pink noise generator (Voss-McCartney 1/f noise algorithm).
 * 6. Multi-tone harmonic probe (fundamental + harmonic partials).
 * 7. Digital silence and clipping test vectors.
 * 8. Standard RIFF/WAVE byte stream serialization and parsing (16-bit and 24-bit PCM).
 */
object SyntheticAudioGenerators {

    /**
     * Generates a pure sine wave vector: y[n] = A * sin(2 * pi * f * n / fs).
     *
     * @param freqHz Frequency of the sine wave in Hertz (e.g. 440.0 Hz).
     * @param durationSec Duration of the signal in seconds.
     * @param sampleRate Sampling rate in Hz (default 48000).
     * @param amplitude Peak amplitude in linear units (default 0.8f, range [0.0, 1.0]).
     */
    fun generateSine(
        freqHz: Float,
        durationSec: Float,
        sampleRate: Int = 48000,
        amplitude: Float = 0.8f
    ): FloatArray {
        require(sampleRate > 0) { "sampleRate must be positive: $sampleRate" }
        require(durationSec >= 0f) { "durationSec must be non-negative: $durationSec" }
        val totalSamples = (durationSec * sampleRate).toInt()
        val output = FloatArray(totalSamples)
        val angularFreq = 2.0 * PI * freqHz.toDouble()

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            output[i] = (amplitude.toDouble() * sin(angularFreq * t)).toFloat()
        }
        return output
    }

    /**
     * Generates a continuous frequency sweep (chirp) from [startFreqHz] to [endFreqHz].
     *
     * Supports both linear sweep:
     *   phi(t) = 2 * pi * (f0 * t + 0.5 * k * t^2) where k = (f1 - f0) / T
     * and logarithmic sweep:
     *   phi(t) = 2 * pi * f0 * T / ln(f1 / f0) * (exp(t / T * ln(f1 / f0)) - 1)
     *
     * @param startFreqHz Initial frequency at t = 0.
     * @param endFreqHz Final frequency at t = durationSec.
     * @param durationSec Duration of the chirp in seconds.
     * @param sampleRate Sampling rate in Hz (default 48000).
     * @param isLogarithmic If true, produces logarithmic frequency sweep; otherwise linear.
     * @param amplitude Peak amplitude.
     */
    fun generateChirp(
        startFreqHz: Float,
        endFreqHz: Float,
        durationSec: Float,
        sampleRate: Int = 48000,
        isLogarithmic: Boolean = false,
        amplitude: Float = 0.8f
    ): FloatArray {
        require(sampleRate > 0) { "sampleRate must be positive: $sampleRate" }
        require(durationSec > 0f) { "durationSec must be positive: $durationSec" }
        val totalSamples = (durationSec * sampleRate).toInt()
        if (totalSamples <= 0) return FloatArray(0)
        val output = FloatArray(totalSamples)
        val totalDuration = durationSec.toDouble()

        if (isLogarithmic && startFreqHz > 0f && endFreqHz > 0f && startFreqHz != endFreqHz) {
            val f0 = startFreqHz.toDouble()
            val f1 = endFreqHz.toDouble()
            val ratio = f1 / f0
            val lnRatio = ln(ratio)
            val scaleFactor = (2.0 * PI * f0 * totalDuration) / lnRatio

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val phase = scaleFactor * (exp((t / totalDuration) * lnRatio) - 1.0)
                output[i] = (amplitude.toDouble() * sin(phase)).toFloat()
            }
        } else {
            val f0 = startFreqHz.toDouble()
            val f1 = endFreqHz.toDouble()
            val chirpRate = (f1 - f0) / totalDuration

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val phase = 2.0 * PI * (f0 * t + 0.5 * chirpRate * t * t)
                output[i] = (amplitude.toDouble() * sin(phase)).toFloat()
            }
        }
        return output
    }

    /**
     * Generates a Dirac delta impulse train with impulses spaced every [periodSamples].
     *
     * @param periodSamples Number of samples between successive impulses.
     * @param totalSamples Total length of the generated buffer.
     * @param amplitude Peak value of each delta spike (default 1.0f).
     */
    fun generateImpulseTrain(
        periodSamples: Int,
        totalSamples: Int,
        amplitude: Float = 1.0f
    ): FloatArray {
        require(totalSamples >= 0) { "totalSamples must be non-negative: $totalSamples" }
        val output = FloatArray(totalSamples)
        if (periodSamples <= 0) return output

        var idx = 0
        while (idx < totalSamples) {
            output[idx] = amplitude
            idx += periodSamples
        }
        return output
    }

    /**
     * Generates a single unit impulse (Dirac delta) at index 0 followed by zeros.
     */
    fun generateSingleImpulse(totalSamples: Int, amplitude: Float = 1.0f): FloatArray {
        return generateImpulseTrain(periodSamples = totalSamples + 1, totalSamples = totalSamples, amplitude = amplitude)
    }

    /**
     * Generates pure digital silence (all zeros).
     */
    fun generateSilence(durationSec: Float, sampleRate: Int = 48000): FloatArray {
        val totalSamples = (durationSec * sampleRate).toInt()
        return FloatArray(totalSamples)
    }

    /**
     * Generates uniform white noise in range [-amplitude, amplitude].
     *
     * @param durationSec Duration in seconds.
     * @param sampleRate Sampling rate in Hz.
     * @param amplitude Peak amplitude limit.
     * @param seed PRNG seed for deterministic test repeatability.
     */
    fun generateWhiteNoise(
        durationSec: Float,
        sampleRate: Int = 48000,
        amplitude: Float = 0.5f,
        seed: Long = 42L
    ): FloatArray {
        val totalSamples = (durationSec * sampleRate).toInt()
        val output = FloatArray(totalSamples)
        val rng = java.util.Random(seed)

        for (i in 0 until totalSamples) {
            output[i] = (rng.nextFloat() * 2.0f - 1.0f) * amplitude
        }
        return output
    }

    /**
     * Generates genuine pink noise (1/f spectral density) using the Voss-McCartney algorithm.
     *
     * Maintains an octave bank of pseudo-random generators updated according to the trailing
     * zeros of a binary clock counter, producing equal energy per octave.
     *
     * @param durationSec Duration in seconds.
     * @param sampleRate Sampling rate in Hz.
     * @param amplitude Target peak/envelope amplitude.
     * @param seed PRNG seed for deterministic test repeatability.
     */
    fun generatePinkNoise(
        durationSec: Float,
        sampleRate: Int = 48000,
        amplitude: Float = 0.5f,
        seed: Long = 42L
    ): FloatArray {
        val totalSamples = (durationSec * sampleRate).toInt()
        if (totalSamples <= 0) return FloatArray(0)
        val output = FloatArray(totalSamples)
        val rng = java.util.Random(seed)

        val numRows = 16
        val rows = FloatArray(numRows)
        var runningSum = 0.0f

        for (r in 0 until numRows) {
            val initial = rng.nextFloat() * 2.0f - 1.0f
            rows[r] = initial
            runningSum += initial
        }

        val normalization = amplitude / (numRows + 1).toFloat()

        for (n in 0 until totalSamples) {
            val count = n + 1
            val rowToUpdate = Integer.numberOfTrailingZeros(count)
            if (rowToUpdate < numRows) {
                runningSum -= rows[rowToUpdate]
                val newRowVal = rng.nextFloat() * 2.0f - 1.0f
                rows[rowToUpdate] = newRowVal
                runningSum += newRowVal
            }

            val white = rng.nextFloat() * 2.0f - 1.0f
            val pinkVal = (runningSum + white) * normalization
            output[n] = pinkVal.coerceIn(-amplitude, amplitude)
        }
        return output
    }

    /**
     * Generates a multi-tone harmonic probe vector:
     *   y[n] = sum_{k=1..N} (w_k * sin(2 * pi * (k * f0) * n / fs))
     *
     * @param fundamentalHz Fundamental frequency in Hz (e.g. 200 Hz).
     * @param durationSec Duration in seconds.
     * @param sampleRate Sampling rate in Hz.
     * @param numHarmonics Total number of harmonic partials to generate.
     * @param oddOnly If true, generates only odd harmonics (1, 3, 5, 7...) for square-like testing.
     * @param amplitude Overall peak amplitude.
     */
    fun generateHarmonicProbe(
        fundamentalHz: Float,
        durationSec: Float,
        sampleRate: Int = 48000,
        numHarmonics: Int = 5,
        oddOnly: Boolean = false,
        amplitude: Float = 0.8f
    ): FloatArray {
        val totalSamples = (durationSec * sampleRate).toInt()
        if (totalSamples <= 0) return FloatArray(0)
        val output = FloatArray(totalSamples)
        val nyquist = sampleRate / 2.0

        val harmonicIndices = mutableListOf<Int>()
        var h = 1
        while (harmonicIndices.size < numHarmonics && (h * fundamentalHz) < nyquist) {
            harmonicIndices.add(h)
            h += if (oddOnly) 2 else 1
        }

        if (harmonicIndices.isEmpty()) return output

        val weights = harmonicIndices.map { 1.0 / it.toDouble() }
        val totalWeight = weights.sum()

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            var sum = 0.0
            for (idx in harmonicIndices.indices) {
                val harmonic = harmonicIndices[idx]
                val freq = harmonic * fundamentalHz.toDouble()
                val weight = weights[idx] / totalWeight
                sum += weight * sin(2.0 * PI * freq * t)
            }
            output[i] = (amplitude.toDouble() * sum).toFloat()
        }
        return output
    }

    /**
     * Generates a full-scale clipping square wave vector (+A / -A).
     */
    fun generateSquareWave(
        freqHz: Float,
        durationSec: Float,
        sampleRate: Int = 48000,
        amplitude: Float = 1.0f
    ): FloatArray {
        val sine = generateSine(freqHz, durationSec, sampleRate, 1.0f)
        val output = FloatArray(sine.size)
        for (i in sine.indices) {
            output[i] = if (sine[i] >= 0f) amplitude else -amplitude
        }
        return output
    }

    /**
     * Serializes a floating-point audio buffer into a valid standard RIFF/WAVE byte stream.
     *
     * @param audio Interleaved floating-point audio samples in range [-1.0, 1.0].
     * @param sampleRate Sampling rate in Hz (default 48000).
     * @param bitDepth PCM resolution: 16 (standard CD/DAW) or 24 (high-res studio master).
     * @param numChannels Channel count: 1 for Mono, 2 for Stereo.
     * @return Raw ByteArray containing canonical RIFF WAVE format header and PCM payload.
     */
    fun createWavBytes(
        audio: FloatArray,
        sampleRate: Int = 48000,
        bitDepth: Int = 16,
        numChannels: Int = 1
    ): ByteArray {
        require(bitDepth == 16 || bitDepth == 24) { "Supported bit depths are 16 and 24 (got $bitDepth)" }
        require(numChannels in 1..2) { "Supported channel counts are 1 and 2 (got $numChannels)" }
        require(sampleRate > 0) { "sampleRate must be positive: $sampleRate" }

        val bytesPerSample = bitDepth / 8
        val blockAlign = numChannels * bytesPerSample
        val byteRate = sampleRate * blockAlign
        val dataChunkSize = audio.size * bytesPerSample
        val riffChunkSize = 36 + dataChunkSize

        val totalFileBytes = 44 + dataChunkSize
        val buffer = ByteBuffer.allocate(totalFileBytes).order(ByteOrder.LITTLE_ENDIAN)

        // --- RIFF Chunk Descriptor ---
        buffer.put('R'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.putInt(riffChunkSize)
        buffer.put('W'.code.toByte())
        buffer.put('A'.code.toByte())
        buffer.put('V'.code.toByte())
        buffer.put('E'.code.toByte())

        // --- "fmt " Subchunk ---
        buffer.put('f'.code.toByte())
        buffer.put('m'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put(' '.code.toByte())
        buffer.putInt(16) // Subchunk1Size for uncompressed PCM
        buffer.putShort(1.toShort()) // AudioFormat = 1 (Linear PCM)
        buffer.putShort(numChannels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitDepth.toShort())

        // --- "data" Subchunk ---
        buffer.put('d'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.putInt(dataChunkSize)

        // PCM Sample Encoding
        when (bitDepth) {
            16 -> {
                for (sample in audio) {
                    val clamped = sample.coerceIn(-1.0f, 1.0f)
                    val pcm16 = (clamped * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
                    buffer.putShort(pcm16)
                }
            }
            24 -> {
                for (sample in audio) {
                    val clamped = sample.coerceIn(-1.0f, 1.0f)
                    val pcm24 = (clamped * 8388607.0f).toInt().coerceIn(-8388608, 8388607)
                    // Little-endian 3-byte packing
                    buffer.put((pcm24 and 0xFF).toByte())
                    buffer.put(((pcm24 shr 8) and 0xFF).toByte())
                    buffer.put(((pcm24 shr 16) and 0xFF).toByte())
                }
            }
        }

        return buffer.array()
    }

    /**
     * Decoded representation of a RIFF/WAVE file for test validation.
     */
    data class DecodedWav(
        val sampleRate: Int,
        val bitDepth: Int,
        val numChannels: Int,
        val audioData: FloatArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DecodedWav) return false
            if (sampleRate != other.sampleRate) return false
            if (bitDepth != other.bitDepth) return false
            if (numChannels != other.numChannels) return false
            return audioData.contentEquals(other.audioData)
        }

        override fun hashCode(): Int {
            var result = sampleRate
            result = 31 * result + bitDepth
            result = 31 * result + numChannels
            result = 31 * result + audioData.contentHashCode()
            return result
        }
    }

    /**
     * Parses a canonical RIFF/WAVE byte stream into floating-point audio data.
     */
    fun parseWavBytes(bytes: ByteArray): DecodedWav {
        require(bytes.size >= 44) { "WAV byte stream too short for RIFF header (length: ${bytes.size})" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val riffTag = String(bytes, 0, 4)
        require(riffTag == "RIFF") { "Invalid RIFF identifier: $riffTag" }

        val waveTag = String(bytes, 8, 4)
        require(waveTag == "WAVE") { "Invalid WAVE identifier: $waveTag" }

        var offset = 12
        var fmtChannels = 0
        var fmtSampleRate = 0
        var fmtBitDepth = 0
        var dataOffset = -1
        var dataSize = 0

        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4)
            buffer.position(offset + 4)
            val chunkSize = buffer.getInt()
            offset += 8

            when (chunkId) {
                "fmt " -> {
                    buffer.position(offset)
                    val formatCode = buffer.getShort()
                    require(formatCode.toInt() == 1) { "Only linear PCM format (1) supported, got $formatCode" }
                    fmtChannels = buffer.getShort().toInt()
                    fmtSampleRate = buffer.getInt()
                    buffer.position(offset + 14)
                    fmtBitDepth = buffer.getShort().toInt()
                }
                "data" -> {
                    dataOffset = offset
                    dataSize = chunkSize
                }
            }
            offset += chunkSize
        }

        require(dataOffset != -1) { "Missing data chunk in WAV stream" }
        require(fmtBitDepth == 16 || fmtBitDepth == 24) { "Unsupported bit depth in WAV: $fmtBitDepth" }

        val bytesPerSample = fmtBitDepth / 8
        val totalSamples = dataSize / bytesPerSample
        val audioData = FloatArray(totalSamples)

        buffer.position(dataOffset)
        when (fmtBitDepth) {
            16 -> {
                for (i in 0 until totalSamples) {
                    val s = buffer.getShort()
                    audioData[i] = s / 32767.0f
                }
            }
            24 -> {
                for (i in 0 until totalSamples) {
                    val b0 = buffer.get().toInt() and 0xFF
                    val b1 = buffer.get().toInt() and 0xFF
                    val b2 = buffer.get().toInt()
                    val raw24 = (b2 shl 16) or (b1 shl 8) or b0
                    audioData[i] = raw24 / 8388607.0f
                }
            }
        }

        return DecodedWav(
            sampleRate = fmtSampleRate,
            bitDepth = fmtBitDepth,
            numChannels = fmtChannels,
            audioData = audioData
        )
    }
}
