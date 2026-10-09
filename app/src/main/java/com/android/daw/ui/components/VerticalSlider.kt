package com.android.daw.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.android.daw.ui.theme.Daw
import com.android.daw.ui.theme.DawTheme

/**
 * SD Studio DAW Vertical Slider
 *
 * Strictly adheres to agy_ui_overhaul_prompt.md Section 5.5:
 * - 4dp track (Daw.space.faderTrackWidth)
 * - 24dp circular thumb (Daw.space.iconMin)
 * - Guaranteed >= 48dp interactive touch target
 * - Pure Foundation Canvas + pointerInput
 * - Zero stock Material 3 widgets
 */
@Composable
fun VerticalSlider(
    value: Float, // Normalized 0f..1f
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    height: Dp = Daw.space.dial,
    activeColor: Color = Daw.colors.mint.base,
    trackColor: Color = Daw.colors.n3Raised,
    thumbColor: Color = Daw.colors.n4Control,
    isEnabled: Boolean = true
) {
    Column(
        modifier = modifier.defaultMinSize(minWidth = Daw.space.touchTargetMin, minHeight = Daw.space.touchTargetMin),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(width = Daw.space.touchTargetMin, height = height)
                .graphicsLayer { alpha = if (isEnabled) 1f else 0.38f }
                .pointerInput(isEnabled) {
                    if (!isEnabled) return@pointerInput
                    detectTapGestures { offset ->
                        val thumbRadiusPx = Daw.space.iconMin.toPx() / 2f
                        val trackHeightPx = size.height - 2 * thumbRadiusPx
                        if (trackHeightPx > 0f) {
                            val travelY = offset.y - thumbRadiusPx
                            val fraction = 1f - (travelY / trackHeightPx).coerceIn(0f, 1f)
                            onValueChange(fraction)
                        }
                    }
                }
                .pointerInput(isEnabled, value) {
                    if (!isEnabled) return@pointerInput
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val thumbRadiusPx = Daw.space.iconMin.toPx() / 2f
                        val trackHeightPx = size.height - 2 * thumbRadiusPx
                        if (trackHeightPx > 0f) {
                            val delta = -dragAmount.y / trackHeightPx
                            val newVal = (value + delta).coerceIn(0f, 1f)
                            onValueChange(newVal)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val trackWidthPx = Daw.space.faderTrackWidth.toPx()
                val thumbRadiusPx = Daw.space.iconMin.toPx() / 2f
                val trackHeightPx = canvasHeight - (thumbRadiusPx * 2f)

                val trackLeft = (canvasWidth - trackWidthPx) / 2f
                val trackTop = thumbRadiusPx

                // 1. Inactive Track background
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(trackLeft, trackTop),
                    size = Size(trackWidthPx, trackHeightPx),
                    cornerRadius = CornerRadius(trackWidthPx / 2f, trackWidthPx / 2f)
                )

                // 2. Active Track
                val activeHeight = trackHeightPx * value.coerceIn(0f, 1f)
                val activeTop = trackTop + trackHeightPx - activeHeight
                if (activeHeight > 0f) {
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(trackLeft, activeTop),
                        size = Size(trackWidthPx, activeHeight),
                        cornerRadius = CornerRadius(trackWidthPx / 2f, trackWidthPx / 2f)
                    )
                }

                // 3. Thumb Cap (24dp circle)
                val thumbCenterY = trackTop + trackHeightPx * (1f - value.coerceIn(0f, 1f))
                val thumbCenterX = canvasWidth / 2f

                // Thumb shadow
                drawCircle(
                    color = Daw.colors.n0Workspace.copy(alpha = 0.5f),
                    radius = thumbRadiusPx,
                    center = Offset(thumbCenterX, thumbCenterY + 2f)
                )
                // Thumb body
                drawCircle(
                    color = thumbColor,
                    radius = thumbRadiusPx,
                    center = Offset(thumbCenterX, thumbCenterY)
                )
                // Thumb inner center dot
                drawCircle(
                    color = Daw.colors.inkOnLight,
                    radius = 3f,
                    center = Offset(thumbCenterX, thumbCenterY)
                )
            }
        }

        if (label != null) {
            Spacer(modifier = Modifier.height(Daw.space.xs))
            Text(
                text = label,
                style = Daw.type.caption,
                color = Daw.colors.inkMuted,
                maxLines = 1
            )
        }
    }
}

@Preview(name = "VerticalSlider Preview")
@Composable
private fun VerticalSliderPreview() {
    DawTheme {
        VerticalSlider(
            value = 0.7f,
            onValueChange = {},
            label = "ATTACK"
        )
    }
}
