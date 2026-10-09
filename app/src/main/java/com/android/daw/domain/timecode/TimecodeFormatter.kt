package com.android.daw.domain.timecode

import com.android.daw.domain.model.BbtTimecode
import com.android.daw.domain.model.TimeSignature
import kotlin.math.roundToLong

/**
 * Utility for formatting and parsing audio frames between musical timecode (BBT: Bars, Beats, Ticks),
 * real-time duration (Minutes, Seconds, Milliseconds), and SMPTE film timecodes.
 */
object TimecodeFormatter {

    const val DEFAULT_TICKS_PER_BEAT = 960 // Standard MIDI PPQ (Pulses Per Quarter Note)
    const val DEFAULT_SAMPLE_RATE = 44100
    const val DEFAULT_BPM = 120.0
    const val DEFAULT_FPS = 30.0

    /**
     * Converts a raw audio frame position to a musical [BbtTimecode] (1-based bar, 1-based beat, 0-based tick).
     */
    fun framesToBbt(
        frame: Long,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        bpm: Double = DEFAULT_BPM,
        timeSignature: TimeSignature = TimeSignature(4, 4),
        ticksPerBeat: Int = DEFAULT_TICKS_PER_BEAT
    ): BbtTimecode {
        val nonNegativeFrame = frame.coerceAtLeast(0L)
        val validSampleRate = if (sampleRate > 0) sampleRate else DEFAULT_SAMPLE_RATE
        val validBpm = if (bpm > 0.0) bpm else DEFAULT_BPM
        val numerator = if (timeSignature.numerator > 0) timeSignature.numerator else 4

        // Audio duration in seconds
        val seconds = nonNegativeFrame.toDouble() / validSampleRate
        // Quarter note beats per second = BPM / 60.0
        val beatsPassed = seconds * (validBpm / 60.0)
        val totalTicks = (beatsPassed * ticksPerBeat).toLong()

        val totalBeats = totalTicks / ticksPerBeat
        val tick = (totalTicks % ticksPerBeat).toInt()

        val bar = ((totalBeats / numerator) + 1).toInt()
        val beat = ((totalBeats % numerator) + 1).toInt()

        return BbtTimecode(bar = bar, beat = beat, tick = tick)
    }

    /**
     * Converts a [BbtTimecode] back to an audio frame offset.
     */
    fun bbtToFrames(
        bbt: BbtTimecode,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        bpm: Double = DEFAULT_BPM,
        timeSignature: TimeSignature = TimeSignature(4, 4),
        ticksPerBeat: Int = DEFAULT_TICKS_PER_BEAT
    ): Long {
        val validSampleRate = if (sampleRate > 0) sampleRate else DEFAULT_SAMPLE_RATE
        val validBpm = if (bpm > 0.0) bpm else DEFAULT_BPM
        val numerator = if (timeSignature.numerator > 0) timeSignature.numerator else 4

        val zeroBasedBar = (bbt.bar - 1).coerceAtLeast(0)
        val zeroBasedBeat = (bbt.beat - 1).coerceAtLeast(0)
        val clampedTick = bbt.tick.coerceIn(0, ticksPerBeat - 1)

        val totalBeats = (zeroBasedBar * numerator) + zeroBasedBeat + (clampedTick.toDouble() / ticksPerBeat)
        val seconds = totalBeats * (60.0 / validBpm)
        return (seconds * validSampleRate).roundToLong().coerceAtLeast(0L)
    }

    /**
     * Formats an audio frame position into a padded standard DAW musical display: "001.01.000".
     */
    fun formatBbt(
        frame: Long,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        bpm: Double = DEFAULT_BPM,
        timeSignature: TimeSignature = TimeSignature(4, 4),
        ticksPerBeat: Int = DEFAULT_TICKS_PER_BEAT
    ): String {
        val bbt = framesToBbt(frame, sampleRate, bpm, timeSignature, ticksPerBeat)
        return String.format("%03d.%02d.%03d", bbt.bar, bbt.beat, bbt.tick)
    }

    /**
     * Formats an audio frame position into a compact musical display: "1.1.000".
     */
    fun formatShortBbt(
        frame: Long,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        bpm: Double = DEFAULT_BPM,
        timeSignature: TimeSignature = TimeSignature(4, 4),
        ticksPerBeat: Int = DEFAULT_TICKS_PER_BEAT
    ): String {
        val bbt = framesToBbt(frame, sampleRate, bpm, timeSignature, ticksPerBeat)
        return "${bbt.bar}.${bbt.beat}.${String.format("%03d", bbt.tick)}"
    }

    /**
     * Parses a BBT string formatted as "BAR.BEAT.TICK" or "BAR:BEAT:TICK" into audio frames.
     */
    fun parseBbt(
        bbtString: String,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        bpm: Double = DEFAULT_BPM,
        timeSignature: TimeSignature = TimeSignature(4, 4),
        ticksPerBeat: Int = DEFAULT_TICKS_PER_BEAT
    ): Long? {
        val parts = bbtString.trim().split('.', ':', ' ')
        if (parts.size < 3) return null
        return try {
            val bar = parts[0].toInt()
            val beat = parts[1].toInt()
            val tick = parts[2].toInt()
            bbtToFrames(BbtTimecode(bar, beat, tick), sampleRate, bpm, timeSignature, ticksPerBeat)
        } catch (_: NumberFormatException) {
            null
        }
    }

    /**
     * Formats an audio frame position into standard clock time with milliseconds: "MM:SS.mmm" or "HH:MM:SS.mmm".
     */
    fun formatTimeMs(
        frame: Long,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        includeHours: Boolean = false
    ): String {
        val nonNegativeFrame = frame.coerceAtLeast(0L)
        val validSampleRate = if (sampleRate > 0) sampleRate else DEFAULT_SAMPLE_RATE
        val totalMs = (nonNegativeFrame * 1000L) / validSampleRate

        val hours = totalMs / 3600000L
        val minutes = (totalMs / 60000L) % 60L
        val seconds = (totalMs / 1000L) % 60L
        val millis = totalMs % 1000L

        return if (includeHours || hours > 0) {
            String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
        } else {
            String.format("%02d:%02d.%03d", minutes, seconds, millis)
        }
    }

    /**
     * Parses clock time "MM:SS.mmm" or "HH:MM:SS.mmm" into audio frames.
     */
    fun parseTimeMs(
        timeString: String,
        sampleRate: Int = DEFAULT_SAMPLE_RATE
    ): Long? {
        val clean = timeString.trim()
        val validSampleRate = if (sampleRate > 0) sampleRate else DEFAULT_SAMPLE_RATE
        return try {
            val parts = clean.split(':')
            val (hours, minutes, secondsPart) = when (parts.size) {
                3 -> Triple(parts[0].toLong(), parts[1].toLong(), parts[2])
                2 -> Triple(0L, parts[0].toLong(), parts[1])
                1 -> Triple(0L, 0L, parts[0])
                else -> return null
            }

            val secSplit = secondsPart.split('.')
            val seconds = secSplit[0].toLong()
            val millis = if (secSplit.size > 1) {
                secSplit[1].padEnd(3, '0').take(3).toLong()
            } else 0L

            val totalMs = (hours * 3600000L) + (minutes * 60000L) + (seconds * 1000L) + millis
            (totalMs * validSampleRate) / 1000L
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Formats an audio frame position into SMPTE timecode string: "HH:MM:SS:FF".
     */
    fun formatSmpte(
        frame: Long,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        fps: Double = DEFAULT_FPS
    ): String {
        val nonNegativeFrame = frame.coerceAtLeast(0L)
        val validSampleRate = if (sampleRate > 0) sampleRate else DEFAULT_SAMPLE_RATE
        val validFps = if (fps > 0.0) fps else DEFAULT_FPS

        val totalSeconds = nonNegativeFrame.toDouble() / validSampleRate
        val totalVideoFrames = (totalSeconds * validFps).toLong()

        val framesPerHour = (3600.0 * validFps).toLong()
        val framesPerMinute = (60.0 * validFps).toLong()

        val hours = totalVideoFrames / framesPerHour
        val minutes = (totalVideoFrames % framesPerHour) / framesPerMinute
        val seconds = ((totalVideoFrames % framesPerHour) % framesPerMinute) / validFps.toLong()
        val subFrames = totalVideoFrames % validFps.toLong()

        return String.format("%02d:%02d:%02d:%02d", hours, minutes, seconds, subFrames)
    }

    /**
     * Parses an SMPTE timecode string ("HH:MM:SS:FF") into audio frames.
     */
    fun parseSmpte(
        smpteString: String,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        fps: Double = DEFAULT_FPS
    ): Long? {
        val parts = smpteString.trim().split(':', ';', '.')
        if (parts.size < 4) return null
        val validSampleRate = if (sampleRate > 0) sampleRate else DEFAULT_SAMPLE_RATE
        val validFps = if (fps > 0.0) fps else DEFAULT_FPS

        return try {
            val hours = parts[0].toLong()
            val minutes = parts[1].toLong()
            val seconds = parts[2].toLong()
            val subFrames = parts[3].toLong()

            val totalVideoFrames = (hours * 3600.0 * validFps) +
                    (minutes * 60.0 * validFps) +
                    (seconds * validFps) +
                    subFrames
            val totalAudioSeconds = totalVideoFrames / validFps
            (totalAudioSeconds * validSampleRate).roundToLong()
        } catch (_: Exception) {
            null
        }
    }
}
