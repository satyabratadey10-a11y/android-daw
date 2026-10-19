package com.android.daw.ui.studio

/**
 * Pure layout math helpers for Timeline and Ruler (UI-01).
 * Ensures bar-number bubble centers match bar lines with sub-pixel precision:
 * bubbleCenter.x == barLine.x ± 0.5px
 * Timeline origin is inset by 16dp so the Bar 1 bubble is not clipped.
 */
object TimelineLayoutMath {
    const val ORIGIN_INSET_DP = 16f
    const val BUBBLE_DIAMETER_DP = 24f
    const val BUBBLE_RADIUS_DP = 12f
    const val BUBBLE_TEXT_SIZE_SP = 12f

    fun calculateBarX(
        barIndex: Int,
        pixelsPerBar: Float,
        scrollOffsetPx: Float,
        originInsetPx: Float
    ): Float = originInsetPx + (barIndex * pixelsPerBar) - scrollOffsetPx

    fun calculateBubbleCenterX(
        barIndex: Int,
        pixelsPerBar: Float,
        scrollOffsetPx: Float,
        originInsetPx: Float
    ): Float = calculateBarX(barIndex, pixelsPerBar, scrollOffsetPx, originInsetPx)

    fun calculatePlayheadX(
        frame: Long,
        sampleRate: Int,
        zoomPixelsPerSecond: Float,
        scrollOffsetPx: Float,
        originInsetPx: Float
    ): Float {
        if (sampleRate <= 0) return originInsetPx - scrollOffsetPx
        val timeSec = frame.toFloat() / sampleRate
        return originInsetPx + (timeSec * zoomPixelsPerSecond) - scrollOffsetPx
    }

    fun calculateClipStartPx(
        startFrame: Long,
        sampleRate: Int,
        zoomPixelsPerSecond: Float,
        scrollOffsetPx: Float,
        originInsetPx: Float
    ): Float {
        if (sampleRate <= 0) return originInsetPx - scrollOffsetPx
        val timeSec = startFrame.toFloat() / sampleRate
        return originInsetPx + (timeSec * zoomPixelsPerSecond) - scrollOffsetPx
    }

    fun calculateFrameFromTap(
        tapX: Float,
        scrollOffsetPx: Float,
        originInsetPx: Float,
        zoomPixelsPerSecond: Float,
        sampleRate: Int
    ): Long {
        if (zoomPixelsPerSecond <= 0f || sampleRate <= 0) return 0L
        val timeSec = (tapX + scrollOffsetPx - originInsetPx).coerceAtLeast(0f) / zoomPixelsPerSecond
        return (timeSec * sampleRate).toLong()
    }
}
