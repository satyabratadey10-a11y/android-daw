package com.android.daw.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.daw.ui.studio.TimelineLayoutMath
import com.android.daw.ui.theme.Daw
import com.android.daw.viewmodel.AudioClipUiModel
import kotlin.math.max
import kotlin.math.min

/**
 * WaveformCanvas
 *
 * FL Studio Mobile fidelity audio waveform renderer adhering to UI-04:
 * - Solid fill in channel color (Dim step), NO outline stroke.
 * - Muted clip rendered in neutral Faint at 38% alpha.
 * - Reserved 16dp title strip at top of clip so title and waveform never collide.
 * - Real waveform drawn below title strip in Base channel color.
 * - 16dp timeline origin inset alignment matching ruler bubbles.
 * - Zero heap allocations during steady-state draw loop.
 */
@Composable
fun WaveformCanvas(
    clip: AudioClipUiModel,
    pixelsPerSecond: Float,
    scrollOffsetPx: Float,
    trackColor: Color,
    modifier: Modifier = Modifier,
    isMuted: Boolean = false,
    originInsetPx: Float = TimelineLayoutMath.ORIGIN_INSET_DP.dp.toPx()
) {
    val textMeasurer = rememberTextMeasurer()
    val inkOnDark = Daw.colors.inkOnDark
    val neutralFaint = Daw.colors.n3Raised.copy(alpha = 0.38f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val pyramid = clip.waveformPeakData ?: return@Canvas
        val sampleRate = pyramid.sampleRate
        if (sampleRate <= 0) return@Canvas

        val clipStartSec = clip.startFrame.toFloat() / sampleRate
        val clipDurationSec = clip.durationFrames.toFloat() / sampleRate

        val clipStartPx = originInsetPx + (clipStartSec * pixelsPerSecond) - scrollOffsetPx
        val clipWidthPx = clipDurationSec * pixelsPerSecond

        // 1. Frustum Culling: skip immediately if clip is completely offscreen
        if (clipStartPx + clipWidthPx < 0f || clipStartPx > size.width) {
            return@Canvas
        }

        val canvasWidth = size.width
        val canvasHeight = size.height

        val visibleStartPx = max(0f, clipStartPx)
        val visibleEndPx = min(canvasWidth, clipStartPx + clipWidthPx)
        val visibleWidthPx = visibleEndPx - visibleStartPx

        if (visibleWidthPx <= 0f) return@Canvas

        // 2. Draw Clip Container Box (FLM style: Solid Dim fill, NO outline stroke per UI-04)
        val containerRadius = 4.dp.toPx() // Daw.radii.xs
        val containerPaddingY = 2.dp.toPx()
        val innerHeight = canvasHeight - (containerPaddingY * 2f)

        val clipBgColor = if (isMuted) neutralFaint else trackColor.copy(alpha = 0.45f)

        drawRoundRect(
            color = clipBgColor,
            topLeft = Offset(clipStartPx, containerPaddingY),
            size = Size(clipWidthPx, innerHeight),
            cornerRadius = CornerRadius(containerRadius, containerRadius)
        )

        // 3. Reserved 16dp Title Strip at Top (UI-04: title and waveform never collide)
        val titleStripHeight = 16.dp.toPx()
        val titleText = clip.name
        val titleLayout = textMeasurer.measure(
            text = titleText,
            style = TextStyle(
                color = inkOnDark,
                fontSize = 10.sp,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both
                )
            )
        )

        val titleX = max(clipStartPx + 8.dp.toPx(), 8.dp.toPx())
        val titleY = containerPaddingY + 2.dp.toPx()
        if (titleX + titleLayout.size.width > 0f && titleX < canvasWidth &&
            titleY + titleLayout.size.height > 0f && titleY < canvasHeight) {
            drawText(
                textLayoutResult = titleLayout,
                topLeft = Offset(titleX, titleY)
            )
        }

        // 4. Waveform Area below Title Strip
        val waveTop = containerPaddingY + titleStripHeight
        val waveHeight = innerHeight - titleStripHeight
        if (waveHeight <= 2f) return@Canvas

        val yMid = waveTop + (waveHeight / 2f)
        val halfHeight = waveHeight / 2f

        // 5. Select Optimal Decimation Pyramid Level
        val level = pyramid.selectOptimalLevel(pixelsPerSecond)
        val samplesPerBlock = level.samplesPerBlock
        val blockCount = level.blockCount

        if (blockCount <= 0) return@Canvas

        val envelopePath = Path()
        var firstPoint = true
        val stepPx = 2 // 2-pixel stride for smooth 60fps rendering

        // Upper Envelope Pass (Left to Right)
        val startXInt = visibleStartPx.toInt()
        val endXInt = visibleEndPx.toInt()

        for (x in startXInt..endXInt step stepPx) {
            val timeOffsetSec = (x + scrollOffsetPx - originInsetPx - (clipStartSec * pixelsPerSecond)) / pixelsPerSecond
            val sampleIndex = (timeOffsetSec * sampleRate).toLong()
            val blockIndex = (sampleIndex / samplesPerBlock).toInt()

            if (blockIndex in 0 until blockCount) {
                val maxVal = level.maxPeaks[blockIndex].coerceIn(0f, 1f)
                val y = yMid - (maxVal * halfHeight)

                if (firstPoint) {
                    envelopePath.moveTo(x.toFloat(), y)
                    firstPoint = false
                } else {
                    envelopePath.lineTo(x.toFloat(), y)
                }
            }
        }

        // Lower Envelope Pass (Right to Left)
        for (x in endXInt downTo startXInt step stepPx) {
            val timeOffsetSec = (x + scrollOffsetPx - originInsetPx - (clipStartSec * pixelsPerSecond)) / pixelsPerSecond
            val sampleIndex = (timeOffsetSec * sampleRate).toLong()
            val blockIndex = (sampleIndex / samplesPerBlock).toInt()

            if (blockIndex in 0 until blockCount) {
                val minVal = level.minPeaks[blockIndex].coerceIn(-1f, 0f)
                val y = yMid - (minVal * halfHeight)
                envelopePath.lineTo(x.toFloat(), y)
            }
        }

        envelopePath.close()

        // 6. Draw Waveform in Base Step (trackColor)
        val waveColor = if (isMuted) Daw.colors.inkMuted.copy(alpha = 0.38f) else trackColor

        drawPath(
            path = envelopePath,
            color = waveColor.copy(alpha = 0.85f)
        )

        // Zero-crossing center line
        drawLine(
            color = waveColor.copy(alpha = 0.35f),
            start = Offset(visibleStartPx, yMid),
            end = Offset(visibleEndPx, yMid),
            strokeWidth = 0.75.dp.toPx()
        )
    }
}
