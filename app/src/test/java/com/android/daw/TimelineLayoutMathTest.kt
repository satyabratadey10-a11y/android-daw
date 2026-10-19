package com.android.daw

import com.android.daw.ui.studio.TimelineLayoutMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite for [TimelineLayoutMath] verifying UI-01:
 * - Bubble center x == bar line x ± 0.5px (centered on bar line per FL Studio Mobile)
 * - Timeline origin inset by 16dp so bar-1 bubble is not clipped at the left edge
 * - Sub-pixel precision across zoom levels, scroll offsets, and multi-digit bar numbers
 */
class TimelineLayoutMathTest {

    private val density = 2.5f // Typical ~400dpi Android device density
    private val originInsetPx = TimelineLayoutMath.ORIGIN_INSET_DP * density // 16dp * 2.5 = 40px
    private val bubbleRadiusPx = TimelineLayoutMath.BUBBLE_RADIUS_DP * density // 12dp * 2.5 = 30px

    @Test
    fun testOriginInsetPreventsBar1Clipping() {
        assertEquals(16f, TimelineLayoutMath.ORIGIN_INSET_DP, 1e-4f)
        assertEquals(24f, TimelineLayoutMath.BUBBLE_DIAMETER_DP, 1e-4f)
        assertEquals(12f, TimelineLayoutMath.BUBBLE_RADIUS_DP, 1e-4f)

        // At scrollOffset = 0, Bar 0 (Bar 1 in UI) center is at originInsetPx
        val bar1CenterX = TimelineLayoutMath.calculateBubbleCenterX(
            barIndex = 0,
            pixelsPerBar = 200f,
            scrollOffsetPx = 0f,
            originInsetPx = originInsetPx
        )

        // The left edge of the bubble: center - radius
        val bubbleLeftEdge = bar1CenterX - bubbleRadiusPx

        // Left edge must be >= 0 (strictly inside the timeline viewable area)
        assertTrue(
            "Bar 1 bubble left edge ($bubbleLeftEdge px) must be >= 0 so it is not clipped",
            bubbleLeftEdge >= 0f
        )
        assertEquals(originInsetPx, bar1CenterX, 0.001f)
    }

    @Test
    fun testBubbleCenterMatchesBarLineExact() {
        val testBars = listOf(0, 1, 2, 4, 8, 15, 32, 64, 99)
        val testScrollOffsets = listOf(0f, 25.5f, 120.75f, 500f, 1024f)
        val testPixelsPerBar = listOf(80f, 120f, 192f, 256.5f, 400f)

        for (bar in testBars) {
            for (scroll in testScrollOffsets) {
                for (ppb in testPixelsPerBar) {
                    val barX = TimelineLayoutMath.calculateBarX(
                        barIndex = bar,
                        pixelsPerBar = ppb,
                        scrollOffsetPx = scroll,
                        originInsetPx = originInsetPx
                    )
                    val bubbleCenterX = TimelineLayoutMath.calculateBubbleCenterX(
                        barIndex = bar,
                        pixelsPerBar = ppb,
                        scrollOffsetPx = scroll,
                        originInsetPx = originInsetPx
                    )

                    // Must match within ±0.5px tolerance
                    val diff = kotlin.math.abs(bubbleCenterX - barX)
                    assertTrue(
                        "Bubble center ($bubbleCenterX) must match bar line ($barX) within 0.5px for bar $bar, diff=$diff",
                        diff <= 0.5f
                    )
                }
            }
        }
    }

    @Test
    fun testPlayheadAndTapFrameReciprocalMath() {
        val sampleRate = 48000
        val zoom = 120f // 120 px/sec
        val scroll = 50f
        val testFrame = 96000L // 2.0 seconds

        val playheadX = TimelineLayoutMath.calculatePlayheadX(
            frame = testFrame,
            sampleRate = sampleRate,
            zoomPixelsPerSecond = zoom,
            scrollOffsetPx = scroll,
            originInsetPx = originInsetPx
        )

        // Expected playheadX: originInsetPx + (2.0 * 120) - 50 = 40 + 240 - 50 = 230 px
        assertEquals(230f, playheadX, 0.01f)

        // Inverse calculation from tap at playheadX
        val tappedFrame = TimelineLayoutMath.calculateFrameFromTap(
            tapX = playheadX,
            scrollOffsetPx = scroll,
            originInsetPx = originInsetPx,
            zoomPixelsPerSecond = zoom,
            sampleRate = sampleRate
        )

        assertEquals(testFrame, tappedFrame)
    }

    @Test
    fun testClipStartPxCalculation() {
        val sampleRate = 48000
        val zoom = 100f
        val scroll = 0f
        val clipStartFrame = 48000L // 1.0 second

        val clipPx = TimelineLayoutMath.calculateClipStartPx(
            startFrame = clipStartFrame,
            sampleRate = sampleRate,
            zoomPixelsPerSecond = zoom,
            scrollOffsetPx = scroll,
            originInsetPx = originInsetPx
        )

        // 40 + (1.0 * 100) - 0 = 140 px
        assertEquals(140f, clipPx, 0.01f)
    }
}
