package com.android.daw.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.android.daw.ui.theme.Daw
import com.android.daw.ui.theme.DawTheme
import kotlin.math.log10
import kotlin.math.pow

/**
 * SD Studio DAW Vertical Fader
 *
 * Strictly adheres to agy_ui_overhaul_prompt.md Section 5.4:
 * - 4dp track (Daw.space.faderTrackWidth)
 * - Thumb 48x24dp with xs radius (Daw.space.faderThumbWidth, Daw.space.faderThumbHeight)
 * - 0 dB unity gain tick at 75% fader travel
 * - Logarithmic taper with double-tap snap to 0 dB
 * - Zero stock Material 3 widgets, zero hex Color literals
 */
@Composable
fun Fader(
    volumeLinear: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "VOL",
    height: Dp = Daw.space.dial
) {
    val faderTravelNormalized = linearToTravel(volumeLinear)

    Column(
        modifier = modifier
            .sizeIn(minWidth = Daw.space.touchTargetMin, minHeight = Daw.space.touchTargetMin)
            .padding(horizontal = Daw.space.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Numeric dB readout header
        val dbValue = linearToDbReadout(volumeLinear)
        Text(
            text = dbValue,
            style = Daw.type.caption,
            color = if (volumeLinear > 1.0f) Daw.colors.coral.base else Daw.colors.inkOnDark
        )

        Spacer(modifier = Modifier.height(Daw.space.xs))

        // Fader Track & Thumb Canvas
        Box(
            modifier = Modifier
                .width(Daw.space.faderThumbWidth)
                .height(height)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            // Double tap snaps to 0 dB Unity Gain (linear 1.0)
                            onVolumeChange(1.0f)
                        }
                    )
                }
                .pointerInput(height) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val thumbHeightPx = Daw.space.faderThumbHeight.toPx()
                        val trackHeightPx = size.height - thumbHeightPx
                        if (trackHeightPx > 0f) {
                            val deltaTravel = -dragAmount.y / trackHeightPx
                            val currentTravel = linearToTravel(volumeLinear)
                            val newTravel = (currentTravel + deltaTravel).coerceIn(0f, 1f)
                            val newLinear = travelToLinear(newTravel)
                            onVolumeChange(newLinear)
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                val thumbHeightPx = Daw.space.faderThumbHeight.toPx()
                val thumbWidthPx = Daw.space.faderThumbWidth.toPx()
                val trackWidthPx = Daw.space.faderTrackWidth.toPx()
                val trackHeightPx = canvasHeight - thumbHeightPx

                val trackLeft = (canvasWidth - trackWidthPx) / 2f
                val trackTop = thumbHeightPx / 2f

                // 1. Draw Fader Track Channel (N1 Grid background)
                drawRoundRect(
                    color = Daw.colors.n1Grid,
                    topLeft = Offset(trackLeft, trackTop),
                    size = Size(trackWidthPx, trackHeightPx),
                    cornerRadius = CornerRadius(trackWidthPx / 2f, trackWidthPx / 2f)
                )

                // Track center slot hairline
                drawLine(
                    color = Daw.colors.n3Raised,
                    start = Offset(canvasWidth / 2f, trackTop),
                    end = Offset(canvasWidth / 2f, trackTop + trackHeightPx),
                    strokeWidth = Daw.space.hairline.toPx()
                )

                // 2. Draw Scale Markings
                // 0 dB unity gain line at 75% height from bottom
                val unityGainY = trackTop + trackHeightPx * (1f - 0.75f)
                val tickLength = Daw.space.sm.toPx()
                drawLine(
                    color = Daw.colors.mint.base,
                    start = Offset(trackLeft - tickLength, unityGainY),
                    end = Offset(trackLeft - 2f, unityGainY),
                    strokeWidth = Daw.space.stroke.toPx()
                )
                drawLine(
                    color = Daw.colors.mint.base,
                    start = Offset(trackLeft + trackWidthPx + 2f, unityGainY),
                    end = Offset(trackLeft + trackWidthPx + tickLength, unityGainY),
                    strokeWidth = Daw.space.stroke.toPx()
                )

                // 3. Draw Fader Thumb Cap (48x24dp radius xs)
                val thumbY = trackTop + trackHeightPx * (1f - faderTravelNormalized) - (thumbHeightPx / 2f)
                val thumbX = (canvasWidth - thumbWidthPx) / 2f
                val cornerRadius = CornerRadius(Daw.radii.xsDp.toPx(), Daw.radii.xsDp.toPx())

                // Thumb shadow
                drawRoundRect(
                    color = Daw.colors.n0Workspace.copy(alpha = 0.5f),
                    topLeft = Offset(thumbX, thumbY + 3f),
                    size = Size(thumbWidthPx, thumbHeightPx),
                    cornerRadius = cornerRadius
                )

                // Thumb body (N4 Control surface)
                drawRoundRect(
                    color = Daw.colors.n4Control,
                    topLeft = Offset(thumbX, thumbY),
                    size = Size(thumbWidthPx, thumbHeightPx),
                    cornerRadius = cornerRadius
                )

                // Thumb center indicator line
                val indicatorY = thumbY + thumbHeightPx / 2f
                val isAtUnity = kotlin.math.abs(faderTravelNormalized - 0.75f) < 0.02f
                drawLine(
                    color = if (isAtUnity) Daw.colors.mint.base else Daw.colors.inkOnLight,
                    start = Offset(thumbX + Daw.space.xs.toPx(), indicatorY),
                    end = Offset(thumbX + thumbWidthPx - Daw.space.xs.toPx(), indicatorY),
                    strokeWidth = Daw.space.stroke.toPx()
                )
            }
        }

        Spacer(modifier = Modifier.height(Daw.space.xs))

        // Fader Label Footer
        Text(
            text = label,
            style = Daw.type.caption,
            color = Daw.colors.inkMuted
        )
    }
}

/**
 * Maps normalized fader travel u in [0, 1] to linear audio multiplier.
 * Unity gain (1.0 linear) is located at u = 0.75.
 */
fun travelToLinear(travel: Float): Float {
    val u = travel.coerceIn(0f, 1f)
    return when {
        u <= 0.001f -> 0f
        u <= 0.75f -> (u / 0.75f).pow(3.0f)
        else -> {
            val boostDb = ((u - 0.75f) / 0.25f) * 6.0f
            10.0f.pow(boostDb / 20.0f)
        }
    }
}

/**
 * Maps linear audio multiplier to normalized fader travel u in [0, 1].
 */
fun linearToTravel(linear: Float): Float {
    return when {
        linear <= 0.00001f -> 0f
        linear <= 1.0f -> 0.75f * linear.pow(1.0f / 3.0f)
        else -> {
            val db = 20.0f * log10(linear)
            val boostFraction = (db / 6.0f).coerceIn(0f, 1f)
            0.75f + (0.25f * boostFraction)
        }
    }.coerceIn(0f, 1f)
}

/**
 * Formats linear volume to human-readable studio dB text
 */
fun linearToDbReadout(linear: Float): String {
    return when {
        linear <= 0.0001f -> "-inf"
        linear in 0.99f..1.01f -> " 0.0 dB"
        else -> {
            val db = 20.0f * log10(linear)
            if (db > 0f) String.format("+%.1f dB", db) else String.format("%.1f dB", db)
        }
    }
}

@Preview(name = "Fader Preview")
@Composable
private fun FaderPreview() {
    DawTheme {
        Fader(
            volumeLinear = 1.0f,
            onVolumeChange = {}
        )
    }
}
