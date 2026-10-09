package com.android.daw

import com.android.daw.domain.model.BbtTimecode
import com.android.daw.domain.model.TimeSignature
import com.android.daw.domain.timecode.TimecodeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests for [TimecodeFormatter].
 * Covers BBT conversion, wall clock formatting, SMPTE frame math,
 * and boundary conditions across varied sample rates and time signatures.
 */
class TimecodeFormatterTest {

    @Test
    fun testFramesToBbtAt120Bpm44100() {
        val sampleRate = 44100
        val bpm = 120.0
        val timeSig = TimeSignature(4, 4)

        // At 120 BPM, 1 beat = 0.5s = 22050 frames
        // Frame 0 should be Bar 1, Beat 1, Tick 0
        val bbt0 = TimecodeFormatter.framesToBbt(0L, sampleRate, bpm, timeSig)
        assertEquals(BbtTimecode(1, 1, 0), bbt0)

        // Frame 22050 should be Bar 1, Beat 2, Tick 0
        val bbtBeat2 = TimecodeFormatter.framesToBbt(22050L, sampleRate, bpm, timeSig)
        assertEquals(BbtTimecode(1, 2, 0), bbtBeat2)

        // Frame 88200 (2.0s = 4 beats) should be Bar 2, Beat 1, Tick 0
        val bbtBar2 = TimecodeFormatter.framesToBbt(88200L, sampleRate, bpm, timeSig)
        assertEquals(BbtTimecode(2, 1, 0), bbtBar2)
    }

    @Test
    fun testFramesToBbtAt48000AndDifferentBpm() {
        val sampleRate = 48000
        val bpm = 140.0
        val timeSig = TimeSignature(4, 4)

        // 1 beat = 60 / 140 s = 0.4285714 s -> ~20571.428 frames
        // 1 bar (4 beats) = 240 / 140 s = 1.7142857 s -> ~82285.714 frames
        val bar1Frames = 0L
        val bar2Frames = 82286L

        val bbt1 = TimecodeFormatter.framesToBbt(bar1Frames, sampleRate, bpm, timeSig)
        assertEquals(1, bbt1.bar)
        assertEquals(1, bbt1.beat)

        val bbt2 = TimecodeFormatter.framesToBbt(bar2Frames, sampleRate, bpm, timeSig)
        assertEquals(2, bbt2.bar)
        assertEquals(1, bbt2.beat)
    }

    @Test
    fun testBbtRoundtripFidelity() {
        val sampleRates = listOf(44100, 48000, 96000)
        val bpms = listOf(60.0, 120.0, 140.0, 180.0)
        val timeSigs = listOf(TimeSignature(4, 4), TimeSignature(3, 4), TimeSignature(6, 8))

        for (sr in sampleRates) {
            for (bpm in bpms) {
                for (sig in timeSigs) {
                    for (frame in listOf(0L, 1000L, 22050L, 88200L, 441000L)) {
                        val bbt = TimecodeFormatter.framesToBbt(frame, sr, bpm, sig)
                        val reconstructedFrame = TimecodeFormatter.bbtToFrames(bbt, sr, bpm, sig)
                        // Should be accurate within 1 tick duration in frames
                        val framesPerBeat = (sr * 60.0) / bpm
                        val framesPerTick = framesPerBeat / TimecodeFormatter.DEFAULT_TICKS_PER_BEAT
                        val diff = abs(frame - reconstructedFrame)
                        assertTrue(
                            "Roundtrip diff ($diff) exceeded 2 tick frames for sr=$sr, bpm=$bpm, frame=$frame",
                            diff <= (framesPerTick * 2.0).toLong() + 1L
                        )
                    }
                }
            }
        }
    }

    @Test
    fun testFormatBbtStrings() {
        val sampleRate = 44100
        val bpm = 120.0

        val formattedStart = TimecodeFormatter.formatBbt(0L, sampleRate, bpm)
        assertEquals("001.01.000", formattedStart)

        val shortFormatted = TimecodeFormatter.formatShortBbt(0L, sampleRate, bpm)
        assertEquals("1.1.000", shortFormatted)

        val parsed = TimecodeFormatter.parseBbt("001.01.000", sampleRate, bpm)
        assertNotNull(parsed)
        assertEquals(0L, parsed)

        val parsedColon = TimecodeFormatter.parseBbt("002:01:000", sampleRate, bpm)
        assertNotNull(parsedColon)
        assertEquals(88200L, parsedColon)

        // Invalid format returns null
        assertNull(TimecodeFormatter.parseBbt("invalid", sampleRate, bpm))
        assertNull(TimecodeFormatter.parseBbt("1.2", sampleRate, bpm))
    }

    @Test
    fun testClockTimeFormattingAndParsing() {
        val sampleRate = 48000

        // 0 frames = 00:00.000
        assertEquals("00:00.000", TimecodeFormatter.formatTimeMs(0L, sampleRate))

        // 48000 frames = exactly 1 second
        assertEquals("00:01.000", TimecodeFormatter.formatTimeMs(48000L, sampleRate))

        // 72000 frames = 1.5 seconds
        assertEquals("00:01.500", TimecodeFormatter.formatTimeMs(72000L, sampleRate))

        // 1 hour: 48000 * 3600 = 172800000 frames
        val oneHourFrames = 172800000L
        assertEquals("01:00:00.000", TimecodeFormatter.formatTimeMs(oneHourFrames, sampleRate, includeHours = true))

        // Parse roundtrip
        val parsed1Sec = TimecodeFormatter.parseTimeMs("00:01.000", sampleRate)
        assertNotNull(parsed1Sec)
        assertEquals(48000L, parsed1Sec)

        val parsedHours = TimecodeFormatter.parseTimeMs("01:00:00.000", sampleRate)
        assertNotNull(parsedHours)
        assertEquals(oneHourFrames, parsedHours)

        assertNull(TimecodeFormatter.parseTimeMs("not:a:number", sampleRate))
    }

    @Test
    fun testSmpteFormattingAndParsing() {
        val sampleRate = 48000
        val fps = 30.0

        // 0 frames -> 00:00:00:00
        assertEquals("00:00:00:00", TimecodeFormatter.formatSmpte(0L, sampleRate, fps))

        // Exactly 1 second = 30 SMPTE video frames -> 00:00:01:00
        assertEquals("00:00:01:00", TimecodeFormatter.formatSmpte(48000L, sampleRate, fps))

        // Exactly 1 hour -> 01:00:00:00
        val hourAudioFrames = 48000L * 3600L
        assertEquals("01:00:00:00", TimecodeFormatter.formatSmpte(hourAudioFrames, sampleRate, fps))

        val parsedHour = TimecodeFormatter.parseSmpte("01:00:00:00", sampleRate, fps)
        assertNotNull(parsedHour)
        assertEquals(hourAudioFrames, parsedHour)

        assertNull(TimecodeFormatter.parseSmpte("malformed", sampleRate, fps))
    }

    @Test
    fun testBoundaryAndEdgeCases() {
        val sampleRate = 44100
        val bpm = 120.0

        // Negative frames coerced to 0
        val negativeBbt = TimecodeFormatter.framesToBbt(-500L, sampleRate, bpm)
        assertEquals(1, negativeBbt.bar)
        assertEquals(1, negativeBbt.beat)
        assertEquals(0, negativeBbt.tick)

        assertEquals("00:00.000", TimecodeFormatter.formatTimeMs(-100L, sampleRate))

        // Extreme large frame (10 hours)
        val tenHoursFrames = 44100L * 36000L
        val largeBbt = TimecodeFormatter.framesToBbt(tenHoursFrames, sampleRate, bpm)
        assertTrue(largeBbt.bar > 1000)

        // Invalid sample rates / BPMs fallback safely to defaults
        val fallbackBbt = TimecodeFormatter.framesToBbt(44100L, sampleRate = 0, bpm = -10.0)
        assertNotNull(fallbackBbt)
        assertTrue(fallbackBbt.bar >= 1)
    }
}
