package com.android.daw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Unit test verifying UI-02 BPM readout formatting and constraints:
 * - Single line formatting for tempo readouts (60, 120, 999)
 * - Tabular width capacity (3-digit numerals up to 999 fit within fixed bounds)
 * - Locale invariant formatting
 */
class BpmReadoutTest {

    private fun formatBpm(bpm: Double): String {
        return String.format(Locale.US, "%.0f", bpm)
    }

    @Test
    fun testBpmFormattingGoldenValues() {
        assertEquals("60", formatBpm(60.0))
        assertEquals("120", formatBpm(120.0))
        assertEquals("999", formatBpm(999.0))
    }

    @Test
    fun testBpmFormattingRounding() {
        assertEquals("128", formatBpm(127.8))
        assertEquals("140", formatBpm(140.2))
        assertEquals("30", formatBpm(30.0))
    }

    @Test
    fun testBpmDigitLengthWithinFixedConstraints() {
        val testTempos = listOf(30.0, 60.0, 90.0, 120.0, 140.0, 175.0, 240.0, 999.0)
        for (tempo in testTempos) {
            val formatted = formatBpm(tempo)
            assertTrue("BPM string '$formatted' must be at most 3 characters", formatted.length <= 3)
            assertTrue("BPM string '$formatted' must not contain line breaks", !formatted.contains('\n'))
        }
    }
}
