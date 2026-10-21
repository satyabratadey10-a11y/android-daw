package com.android.daw

import com.android.daw.ui.studio.PianoRollSnapMath
import com.android.daw.viewmodel.PianoRollSnap
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit test suite verifying [PianoRollSnapMath]:
 * - Grid quantization at 1/4 (Quarter), 1/8 (Eighth), 1/16 (Sixteenth), 1/32 (Thirty-second)
 * - Off mode non-quantization
 * - Duration quantization and default note durations
 */
class PianoRollSnapMathTest {

    private val sampleRate = 48000
    private val bpm = 120.0
    // At 120 BPM: 1 beat = 0.5s = 24,000 frames
    // 1/4 (1 beat) = 24,000 frames
    // 1/8 (0.5 beat) = 12,000 frames
    // 1/16 (0.25 beat) = 6,000 frames
    // 1/32 (0.125 beat) = 3,000 frames

    @Test
    fun testFramesPerSnapAt120Bpm() {
        assertEquals(24000L, PianoRollSnapMath.framesPerSnap(PianoRollSnap.QUARTER, bpm, sampleRate))
        assertEquals(12000L, PianoRollSnapMath.framesPerSnap(PianoRollSnap.EIGHTH, bpm, sampleRate))
        assertEquals(6000L, PianoRollSnapMath.framesPerSnap(PianoRollSnap.SIXTEENTH, bpm, sampleRate))
        assertEquals(3000L, PianoRollSnapMath.framesPerSnap(PianoRollSnap.THIRTY_SECOND, bpm, sampleRate))
    }

    @Test
    fun testQuarterNoteSnapQuantization() {
        val snap = PianoRollSnap.QUARTER

        // 0 frames -> 0
        assertEquals(0L, PianoRollSnapMath.quantizeFrame(0L, snap, bpm, sampleRate))

        // 11,000 frames (< half snap) -> snaps to 0
        assertEquals(0L, PianoRollSnapMath.quantizeFrame(11000L, snap, bpm, sampleRate))

        // 12,000 frames (>= half snap) -> snaps to 24,000
        assertEquals(24000L, PianoRollSnapMath.quantizeFrame(12000L, snap, bpm, sampleRate))

        // 25,000 frames -> snaps to 24,000
        assertEquals(24000L, PianoRollSnapMath.quantizeFrame(25000L, snap, bpm, sampleRate))

        // 35,999 frames -> snaps to 24,000
        assertEquals(24000L, PianoRollSnapMath.quantizeFrame(35999L, snap, bpm, sampleRate))

        // 36,000 frames -> snaps to 48,000
        assertEquals(48000L, PianoRollSnapMath.quantizeFrame(36000L, snap, bpm, sampleRate))
    }

    @Test
    fun testSixteenthNoteSnapQuantization() {
        val snap = PianoRollSnap.SIXTEENTH

        // Snap unit = 6,000 frames
        assertEquals(0L, PianoRollSnapMath.quantizeFrame(2999L, snap, bpm, sampleRate))
        assertEquals(6000L, PianoRollSnapMath.quantizeFrame(3000L, snap, bpm, sampleRate))
        assertEquals(6000L, PianoRollSnapMath.quantizeFrame(7000L, snap, bpm, sampleRate))
        assertEquals(12000L, PianoRollSnapMath.quantizeFrame(9000L, snap, bpm, sampleRate))
    }

    @Test
    fun testOffSnapLeavesFrameUnchanged() {
        val snap = PianoRollSnap.OFF
        val rawFrame = 12345L
        assertEquals(rawFrame, PianoRollSnapMath.quantizeFrame(rawFrame, snap, bpm, sampleRate))
    }

    @Test
    fun testDurationQuantizationMinimumUnit() {
        val snap = PianoRollSnap.SIXTEENTH
        // Zero or tiny duration quantizes to at least 1 unit (6,000 frames)
        val snapped = PianoRollSnapMath.quantizeDuration(100L, snap, bpm, sampleRate)
        assertEquals(6000L, snapped)
    }

    @Test
    fun testDefaultNoteDurationCalculation() {
        // Sixteenth = 6,000 frames at 120 BPM
        val sixteenthDur = PianoRollSnapMath.defaultNoteDuration(PianoRollSnap.SIXTEENTH, bpm, sampleRate)
        assertEquals(6000L, sixteenthDur)

        // Quarter = 24,000 frames at 120 BPM
        val quarterDur = PianoRollSnapMath.defaultNoteDuration(PianoRollSnap.QUARTER, bpm, sampleRate)
        assertEquals(24000L, quarterDur)
    }
}
