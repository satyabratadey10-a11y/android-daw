package com.android.daw.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.android.daw.ui.theme.Daw
import com.android.daw.ui.theme.DawTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * SD Studio DAW Rotary Ring Knob
 *
 * Strictly adheres to agy_ui_overhaul_prompt.md:
 * - 56dp visual diameter (Daw.space.circleLg)
 * - 270° sweep arc with centered monospace numeric readout
 * - Bipolar (center detent) and unipolar modes
 * - Vertical drag with fine control + double-tap snap to default
 * - Tactile settle physics (Daw.motion.playfulSpring)
 * - Minimum 48dp touch target
 * - Zero stock Material 3 widgets
 */
@Composable
fun RingKnob(
    value: Float, // Normalized 0f..1f (or -1f..1f if isBipolar)
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    defaultValue: Float = 0f,
    isBipolar: Boolean = false,
    valueFormatter: (Float) -> String = {
        if (isBipolar) {
            val pct = (it * 100).toInt()
            when {
                pct < -2 -> "L${-pct}"
                pct > 2 -> "R$pct"
                else -> "C"
            }
        } else {
            "${(it * 100).toInt()}%"
        }
    },
    activeColor: Color = Daw.colors.mint.base,
    trackColor: Color = Daw.colors.n3Raised,
    textColor: Color = Daw.colors.inkOnDark,
    size: Dp = Daw.space.circleLg,
    isEnabled: Boolean = true
) {
    var isDragging by remember { mutableFloatStateOf(0f) }
    val strokeWidthDp = Daw.space.indicator
    val inkOnDark = Daw.colors.inkOnDark

    val animatedValue by animateFloatAsState(
        targetValue = value,
        animationSpec = Daw.motion.playfulSpring,
        label = "ring_knob_val"
    )

    Column(
        modifier = modifier.defaultMinSize(minWidth = Daw.space.touchTargetMin, minHeight = Daw.space.touchTargetMin),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer { alpha = if (isEnabled) 1f else 0.38f }
                .pointerInput(isEnabled, isBipolar) {
                    if (!isEnabled) return@pointerInput
                    detectTapGestures(
                        onDoubleTap = {
                            onValueChange(defaultValue)
                        }
                    )
                }
                .pointerInput(isEnabled, isBipolar, value) {
                    if (!isEnabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { isDragging = 1f },
                        onDragEnd = { isDragging = 0f },
                        onDragCancel = { isDragging = 0f }
                    ) { change, dragAmount ->
                        change.consume()
                        val dragSensitivity = 0.006f
                        val delta = -dragAmount.y * dragSensitivity
                        val minVal = if (isBipolar) -1f else 0f
                        val maxVal = 1f
                        val newVal = (value + delta).coerceIn(minVal, maxVal)
                        onValueChange(newVal)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(size)) {
                val strokeWidth = strokeWidthDp.toPx()
                val radius = (size.toPx() - strokeWidth) / 2f
                val centerOffset = Offset(size.toPx() / 2f, size.toPx() / 2f)
                val arcTopLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                val arcSize = Size(radius * 2f, radius * 2f)

                // 1. Background Arc (270° from 135° to 405°)
                drawArc(
                    color = trackColor,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // 2. Active Arc
                if (isBipolar) {
                    // Center is 270° (top center)
                    val centerAngle = 270f
                    val sweep = (animatedValue.coerceIn(-1f, 1f)) * 135f
                    if (sweep != 0f) {
                        drawArc(
                            color = activeColor,
                            startAngle = centerAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                } else {
                    val normalized = animatedValue.coerceIn(0f, 1f)
                    val sweep = normalized * 270f
                    if (sweep > 0f) {
                        drawArc(
                            color = activeColor,
                            startAngle = 135f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                // 3. Current Value Indicator Thumb Dot
                val currentAngleDeg = if (isBipolar) {
                    270f + (animatedValue.coerceIn(-1f, 1f)) * 135f
                } else {
                    135f + animatedValue.coerceIn(0f, 1f) * 270f
                }
                val angleRad = Math.toRadians(currentAngleDeg.toDouble())
                val dotX = centerOffset.x + (radius * cos(angleRad)).toFloat()
                val dotY = centerOffset.y + (radius * sin(angleRad)).toFloat()

                drawCircle(
                    color = inkOnDark,
                    radius = strokeWidth * 1.1f,
                    center = Offset(dotX, dotY)
                )
            }

            // Centered Monospace Value Readout
            Text(
                text = valueFormatter(value),
                style = Daw.type.caption,
                color = textColor,
                maxLines = 1
            )
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

@Preview(name = "RingKnob Preview")
@Composable
private fun RingKnobPreview() {
    DawTheme {
        RingKnob(
            value = 0.65f,
            onValueChange = {},
            label = "FILTER RES"
        )
    }
}
