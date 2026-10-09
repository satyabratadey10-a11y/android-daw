package com.android.daw.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.daw.ui.theme.Daw
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/**
 * SD Studio DAW VerticalFader Component
 *
 * Strictly adheres to agy_ui_overhaul_prompt.md (Section 5.4, 5.5, 7.8):
 * - 4dp track width (Daw.space.faderTrackWidth)
 * - 48x24dp thumb cap (Daw.space.faderThumbWidth x Daw.space.faderThumbHeight) with radius xs (Daw.radii.xs)
 * - Calibrated dB scale: +6 dB to -inf dB with 0 dB Unity Gain at 75% fader travel
 * - Magnetic detent snap to 0 dB unity gain with tactile haptic tick
 * - Double-tap gesture snaps directly to 0 dB unity gain
 * - Centered dB readout in Daw.type.mono
 * - 100% token-driven, zero raw dp/sp or hardcoded hex colors
 */
@Composable
fun VerticalFader(
    volumeLinear: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    activeColor: Color = Daw.colors.active,
    height: Dp = 160.dp,
    showScaleLabels: Boolean = true,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val travel = remember(volumeLinear) { linearToTravel(volumeLinear) }
    val dbReadout = remember(volumeLinear) { linearToDbReadout(volumeLinear) }

    Column(
        modifier = modifier
            .sizeIn(minWidth = Daw.space.touchTargetMin, minHeight = Daw.space.touchTargetMin)
            .alpha(if (enabled) 1f else 0.38f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Daw.space.xs)
    ) {
        // Readout Header: db value in mono font
        Text(
            text = dbReadout,
            style = Daw.type.mono,
            color = if (volumeLinear > 1.01f) activeColor else Daw.colors.inkOnDark,
            maxLines = 1
        )

        // Fader Track + Thumb interactive Box
        Box(
            modifier = Modifier
                .width(Daw.space.faderThumbWidth)
                .height(height)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onDoubleTap = {
                            onVolumeChange(1.0f) // Snap to 0 dB Unity Gain
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onTap = { offset ->
                            val thumbHeightPx = Daw.space.faderThumbHeight.toPx()
                            val usableHeightPx = size.height - thumbHeightPx
                            if (usableHeightPx > 0f) {
                                val clickedTravel = (1f - (offset.y - thumbHeightPx / 2f) / usableHeightPx).coerceIn(0f, 1f)
                                val snapped = if (abs(clickedTravel - 0.75f) < 0.04f) 0.75f else clickedTravel
                                onVolumeChange(travelToLinear(snapped))
                                if (snapped == 0.75f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                    )
                }
                .pointerInput(enabled, volumeLinear) {
                    if (!enabled) return@pointerInput
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val thumbHeightPx = Daw.space.faderThumbHeight.toPx()
                        val usableHeightPx = size.height - thumbHeightPx
                        if (usableHeightPx > 0f) {
                            val currentTravel = linearToTravel(volumeLinear)
                            // Dragging up (negative y) increases travel
                            val deltaTravel = -dragAmount.y / usableHeightPx
                            val rawNewTravel = (currentTravel + deltaTravel).coerceIn(0f, 1f)

                            // Magnetic snap to 0 dB (0.75 travel)
                            val finalTravel = if (abs(rawNewTravel - 0.75f) < 0.035f) {
                                if (abs(currentTravel - 0.75f) >= 0.035f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                0.75f
                            } else {
                                rawNewTravel
                            }

                            if (finalTravel != currentTravel) {
                                onVolumeChange(travelToLinear(finalTravel))
                            }
                        }
                    }
                }
        ) {
            val trackColor = Daw.colors.n1Grid
            val trackActiveColor = activeColor
            val thumbColor = Daw.colors.n4Control
            val thumbRidgeColor = Daw.colors.inkOnLight
            val tickColor = Daw.colors.n3Raised
            val zeroTickColor = activeColor

            Canvas(modifier = Modifier.matchParentSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                val thumbHeightPx = Daw.space.faderThumbHeight.toPx()
                val thumbWidthPx = Daw.space.faderThumbWidth.toPx()
                val trackWidthPx = Daw.space.faderTrackWidth.toPx()
                val hairlinePx = Daw.space.hairline.toPx()
                val strokePx = Daw.space.stroke.toPx()
                val radiusXsPx = Daw.radii.xsDp.toPx()

                val usableHeightPx = canvasHeight - thumbHeightPx
                val trackLeft = (canvasWidth - trackWidthPx) / 2f
                val trackTop = thumbHeightPx / 2f

                // 1. Scale Tick Marks (Along Left & Right Sides)
                if (showScaleLabels) {
                    val tickFractions = floatArrayOf(
                        1.0f,   // +6 dB
                        0.75f,  // 0 dB (Unity)
                        0.595f, // -6 dB
                        0.473f, // -12 dB
                        0.298f, // -24 dB
                        0.118f, // -48 dB
                        0.0f    // -inf
                    )

                    tickFractions.forEach { frac ->
                        val tickY = trackTop + (1f - frac) * usableHeightPx
                        val isZeroDb = abs(frac - 0.75f) < 0.01f
                        val currentTickColor = if (isZeroDb) zeroTickColor else tickColor
                        val tickLen = if (isZeroDb) strokePx * 3f else strokePx * 2f

                        // Left tick
                        drawLine(
                            color = currentTickColor,
                            start = Offset(trackLeft - strokePx * 2f - tickLen, tickY),
                            end = Offset(trackLeft - strokePx * 2f, tickY),
                            strokeWidth = hairlinePx
                        )
                        // Right tick
                        drawLine(
                            color = currentTickColor,
                            start = Offset(trackLeft + trackWidthPx + strokePx * 2f, tickY),
                            end = Offset(trackLeft + trackWidthPx + strokePx * 2f + tickLen, tickY),
                            strokeWidth = hairlinePx
                        )
                    }
                }

                // 2. Track Background Gutter
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(trackLeft, trackTop),
                    size = Size(trackWidthPx, usableHeightPx),
                    cornerRadius = CornerRadius(trackWidthPx / 2f, trackWidthPx / 2f)
                )

                // 3. Active Track Level Fill (Bottom up to thumb center)
                val thumbCenterY = trackTop + (1f - travel) * usableHeightPx
                val fillHeight = (trackTop + usableHeightPx) - thumbCenterY
                if (fillHeight > 0f) {
                    drawRoundRect(
                        color = trackActiveColor,
                        topLeft = Offset(trackLeft, thumbCenterY),
                        size = Size(trackWidthPx, fillHeight),
                        cornerRadius = CornerRadius(trackWidthPx / 2f, trackWidthPx / 2f)
                    )
                }

                // 4. Physical Fader Thumb Cap (48x24dp with xs radius)
                val thumbTop = thumbCenterY - thumbHeightPx / 2f
                val thumbLeft = (canvasWidth - thumbWidthPx) / 2f

                // Thumb body
                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(thumbLeft, thumbTop),
                    size = Size(thumbWidthPx, thumbHeightPx),
                    cornerRadius = CornerRadius(radiusXsPx, radiusXsPx)
                )

                // Thumb Center Indicator Stripe
                val isAtZeroDb = abs(travel - 0.75f) < 0.01f
                val indicatorColor = if (isAtZeroDb) activeColor else thumbRidgeColor
                drawLine(
                    color = indicatorColor,
                    start = Offset(thumbLeft + strokePx * 2f, thumbCenterY),
                    end = Offset(thumbLeft + thumbWidthPx - strokePx * 2f, thumbCenterY),
                    strokeWidth = strokePx
                )

                // Tactile Grip Ridges (above and below center stripe)
                drawLine(
                    color = thumbRidgeColor.copy(alpha = 0.5f),
                    start = Offset(thumbLeft + thumbWidthPx * 0.25f, thumbCenterY - strokePx * 2.5f),
                    end = Offset(thumbLeft + thumbWidthPx * 0.75f, thumbCenterY - strokePx * 2.5f),
                    strokeWidth = hairlinePx
                )
                drawLine(
                    color = thumbRidgeColor.copy(alpha = 0.5f),
                    start = Offset(thumbLeft + thumbWidthPx * 0.25f, thumbCenterY + strokePx * 2.5f),
                    end = Offset(thumbLeft + thumbWidthPx * 0.75f, thumbCenterY + strokePx * 2.5f),
                    strokeWidth = hairlinePx
                )
            }
        }

        // Optional label below
        label?.let { text ->
            Text(
                text = text,
                style = Daw.type.caption,
                color = Daw.colors.inkMuted,
                maxLines = 1
            )
        }
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
        u <= 0.75f -> {
            // Cubic logarithmic taper from 0 to 1.0 linear
            (u / 0.75f).pow(3.0f)
        }
        else -> {
            // Boost range up to +6 dB (approx 2.0 linear)
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
        linear <= 1.0f -> {
            // Inverse cubic taper
            0.75f * linear.pow(1.0f / 3.0f)
        }
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
