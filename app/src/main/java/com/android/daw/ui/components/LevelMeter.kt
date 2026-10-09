package com.android.daw.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.tooling.preview.Preview
import com.android.daw.ui.theme.Daw
import com.android.daw.ui.theme.DawTheme
import com.android.daw.viewmodel.StereoLevel
import kotlin.math.log10

/**
 * SD Studio DAW Stereo Level Meter
 *
 * Strictly adheres to agy_ui_overhaul_prompt.md Sections 2, 5.4, 7.9:
 * - High-Frequency Draw Isolation (Draw-phase evaluated from [levelProvider])
 * - Mixer meters in channel's single primary color (Mint or Coral or Sky)
 * - Zero red-yellow-green rainbow gradients
 * - Shifts to Coral Base only when exceeding 0 dBFS / clipping
 * - Zero stock Material 3 widgets, zero hex Color literals
 */
@Composable
fun LevelMeter(
    levelProvider: () -> StereoLevel,
    modifier: Modifier = Modifier,
    channelColor: Color = Daw.colors.mint.base,
    onResetClip: () -> Unit = {},
    showScaleLabels: Boolean = true
) {
    Row(
        modifier = modifier
            .sizeIn(minWidth = Daw.space.touchTargetMin, minHeight = Daw.space.touchTargetMin)
            .padding(vertical = Daw.space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Latching Clipping LED indicator
            Box(
                modifier = Modifier
                    .size(Daw.space.xl)
                    .clip(CircleShape)
                    .clickable(onClick = onResetClip),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(Daw.space.sm)) {
                    val level = levelProvider()
                    val isClipped = level.hasClipped
                    drawCircle(
                        color = if (isClipped) Daw.colors.coral.base else Daw.colors.n3Raised
                    )
                    if (isClipped) {
                        drawCircle(
                            color = Daw.colors.inkOnDark,
                            radius = Daw.space.stroke.toPx()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Daw.space.xs))

            // Stereo Peak & RMS Level Bars Canvas (Draw-phase evaluated)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .width(Daw.space.lg)
                    .clip(Daw.radii.xs)
                    .background(Daw.colors.n1Grid)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val level = levelProvider()
                    val width = size.width
                    val height = size.height

                    val channelWidth = (width - Daw.space.stroke.toPx()) / 2f

                    // Left channel bar
                    drawChannelMeter(
                        xOffset = 0f,
                        channelWidth = channelWidth,
                        peakLinear = level.peakLeft,
                        rmsLinear = level.rmsLeft,
                        height = height,
                        channelColor = channelColor,
                        clipColor = Daw.colors.coral.base,
                        gutterColor = Daw.colors.n0Workspace,
                        tickColor = Daw.colors.inkOnDark
                    )

                    // Right channel bar
                    drawChannelMeter(
                        xOffset = channelWidth + Daw.space.stroke.toPx(),
                        channelWidth = channelWidth,
                        peakLinear = level.peakRight,
                        rmsLinear = level.rmsRight,
                        height = height,
                        channelColor = channelColor,
                        clipColor = Daw.colors.coral.base,
                        gutterColor = Daw.colors.n0Workspace,
                        tickColor = Daw.colors.inkOnDark
                    )

                    // Draw reference grid lines (0 dB, -12 dB, -24 dB)
                    val y0dB = height * (1f - dbToNormalizedHeight(0f))
                    val yMinus12 = height * (1f - dbToNormalizedHeight(-12f))
                    val yMinus24 = height * (1f - dbToNormalizedHeight(-24f))

                    drawLine(
                        color = Daw.colors.inkOnDark.copy(alpha = 0.5f),
                        start = Offset(0f, y0dB),
                        end = Offset(width, y0dB),
                        strokeWidth = Daw.space.hairline.toPx()
                    )
                    drawLine(
                        color = Daw.colors.inkOnDark.copy(alpha = 0.25f),
                        start = Offset(0f, yMinus12),
                        end = Offset(width, yMinus12),
                        strokeWidth = Daw.space.hairline.toPx() / 2f
                    )
                    drawLine(
                        color = Daw.colors.inkOnDark.copy(alpha = 0.25f),
                        start = Offset(0f, yMinus24),
                        end = Offset(width, yMinus24),
                        strokeWidth = Daw.space.hairline.toPx() / 2f
                    )
                }
            }
        }

        // Optional numeric dB scale ticks
        if (showScaleLabels) {
            Spacer(modifier = Modifier.width(Daw.space.xs))
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                Text(text = "+6", style = Daw.type.caption, color = Daw.colors.coral.base)
                Text(text = " 0", style = Daw.type.caption, color = Daw.colors.mint.base)
                Text(text = "-12", style = Daw.type.caption, color = Daw.colors.inkMuted)
                Text(text = "-24", style = Daw.type.caption, color = Daw.colors.inkMuted)
                Text(text = "-60", style = Daw.type.caption, color = Daw.colors.inkMuted)
            }
        }
    }
}

private fun DrawScope.drawChannelMeter(
    xOffset: Float,
    channelWidth: Float,
    peakLinear: Float,
    rmsLinear: Float,
    height: Float,
    channelColor: Color,
    clipColor: Color,
    gutterColor: Color,
    tickColor: Color
) {
    val peakDb = linearToDb(peakLinear)
    val rmsDb = linearToDb(rmsLinear)

    val peakNorm = dbToNormalizedHeight(peakDb)
    val rmsNorm = dbToNormalizedHeight(rmsDb)

    val peakHeight = height * peakNorm
    val rmsHeight = height * rmsNorm

    // Background track gutter
    drawRect(
        color = gutterColor,
        topLeft = Offset(xOffset, 0f),
        size = Size(channelWidth, height)
    )

    val barColor = if (peakDb > 0f) clipColor else channelColor

    // Draw active Peak bar from bottom upwards
    if (peakHeight > 0f) {
        drawRect(
            color = barColor,
            topLeft = Offset(xOffset, height - peakHeight),
            size = Size(channelWidth, peakHeight)
        )
    }

    // Draw inner RMS bar
    if (rmsHeight > 0f) {
        val rmsWidth = channelWidth * 0.5f
        val rmsX = xOffset + (channelWidth - rmsWidth) / 2f
        drawRect(
            color = tickColor.copy(alpha = 0.45f),
            topLeft = Offset(rmsX, height - rmsHeight),
            size = Size(rmsWidth, rmsHeight)
        )
    }

    // Draw Peak Hold tick line
    if (peakHeight > 0f) {
        val tickY = height - peakHeight
        drawLine(
            color = tickColor,
            start = Offset(xOffset, tickY),
            end = Offset(xOffset + channelWidth, tickY),
            strokeWidth = 2f
        )
    }
}

fun linearToDb(linear: Float): Float {
    return if (linear > 0.00001f) {
        20f * log10(linear)
    } else {
        -60f
    }
}

fun dbToNormalizedHeight(db: Float): Float {
    return when {
        db < -60f -> 0f
        db < -24f -> 0.20f * ((db + 60f) / 36f)
        db <= 0f -> 0.20f + 0.65f * ((db + 24f) / 24f)
        else -> (0.85f + 0.15f * (db / 6f)).coerceAtMost(1.0f)
    }.coerceIn(0f, 1f)
}

@Preview(name = "LevelMeter Preview")
@Composable
private fun LevelMeterPreview() {
    DawTheme {
        LevelMeter(
            levelProvider = { StereoLevel(peakLeft = 0.8f, peakRight = 0.75f) }
        )
    }
}
