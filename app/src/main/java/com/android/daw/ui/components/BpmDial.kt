package com.android.daw.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.zIndex
import com.android.daw.ui.theme.Daw
import com.android.daw.ui.theme.DawIcons
import com.android.daw.ui.theme.DawTheme
import com.android.daw.ui.theme.Layer

/**
 * SD Studio DAW BPM Dial Overlay
 *
 * Strictly adheres to agy_ui_overhaul_prompt.md Section 5.7 & 7.7:
 * - Centered circular overlay on Layer.Popup (5f)
 * - 160dp dial ring (Daw.space.dial)
 * - Big mono BPM, time signature below
 * - ±1 and ±10 buttons
 * - TAP and metronome buttons
 * - Close button in top-right gutter
 * - Pure Foundation Canvas + custom controls
 */
@Composable
fun BpmDial(
    bpm: Double,
    onBpmChange: (Double) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var tapCount by remember { mutableIntStateOf(0) }
    var isMetronomeActive by remember { androidx.compose.runtime.mutableStateOf(false) }

    // Layer.Popup container with animated 60% scrim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(Layer.Popup)
            .background(Daw.colors.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismissRequest
            ),
        contentAlignment = Alignment.Center
    ) {
        // Modal Surface
        Box(
            modifier = modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // Consume tap
                )
                .shadow(
                    elevation = Daw.space.lg,
                    shape = Daw.radii.md,
                    ambientColor = Daw.colors.n0Workspace,
                    spotColor = Daw.colors.n0Workspace
                )
                .background(
                    color = Daw.colors.n2Surface,
                    shape = Daw.radii.md
                )
                .padding(Daw.space.lg)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Daw.space.md)
            ) {
                // Top Header Row with Close button
                Row(
                    modifier = Modifier.size(width = Daw.space.dial + Daw.space.xxl * 2, height = Daw.space.touchTargetMin),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "TEMPO / BPM",
                        style = Daw.type.title,
                        color = Daw.colors.inkOnDark
                    )
                    DawIconButton(
                        icon = DawIcons.Close,
                        onClick = onDismissRequest,
                        tint = Daw.colors.inkMuted
                    )
                }

                // 160dp Circular Dial Ring Canvas
                Box(
                    modifier = Modifier.size(Daw.space.dial),
                    contentAlignment = Alignment.Center
                ) {
                    val ringColor = Daw.colors.mint.base
                    val trackColor = Daw.colors.n3Raised
                    val indicatorDp = Daw.space.indicator

                    Canvas(modifier = Modifier.size(Daw.space.dial)) {
                        val strokeWidth = indicatorDp.toPx()
                        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                        val arcTopLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                        // Background ring
                        drawArc(
                            color = trackColor,
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // Active tempo arc (40 to 240 BPM mapped to 270 degrees)
                        val minBpm = 40.0
                        val maxBpm = 240.0
                        val fraction = ((bpm - minBpm) / (maxBpm - minBpm)).coerceIn(0.0, 1.0).toFloat()
                        val sweep = fraction * 270f

                        drawArc(
                            color = ringColor,
                            startAngle = 135f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    // Center Numeric Readout & Time Signature
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format("%.1f", bpm),
                            style = Daw.type.display,
                            color = Daw.colors.inkOnDark
                        )
                        Spacer(modifier = Modifier.height(Daw.space.xs))
                        Text(
                            text = "4 / 4",
                            style = Daw.type.caption,
                            color = Daw.colors.inkMuted
                        )
                    }
                }

                // ±10 and ±1 Stepper Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Daw.space.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FloatingCircle(
                        size = FloatingCircleSize.Small,
                        onClick = { onBpmChange((bpm - 10.0).coerceIn(40.0, 240.0)) },
                        content = {
                            Text("-10", style = Daw.type.label, color = Daw.colors.inkOnLight)
                        }
                    )
                    FloatingCircle(
                        size = FloatingCircleSize.Small,
                        onClick = { onBpmChange((bpm - 1.0).coerceIn(40.0, 240.0)) },
                        content = {
                            Text("-1", style = Daw.type.label, color = Daw.colors.inkOnLight)
                        }
                    )
                    FloatingCircle(
                        size = FloatingCircleSize.Small,
                        onClick = { onBpmChange((bpm + 1.0).coerceIn(40.0, 240.0)) },
                        content = {
                            Text("+1", style = Daw.type.label, color = Daw.colors.inkOnLight)
                        }
                    )
                    FloatingCircle(
                        size = FloatingCircleSize.Small,
                        onClick = { onBpmChange((bpm + 10.0).coerceIn(40.0, 240.0)) },
                        content = {
                            Text("+10", style = Daw.type.label, color = Daw.colors.inkOnLight)
                        }
                    )
                }

                // TAP & Metronome Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Daw.space.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FloatingCircle(
                        size = FloatingCircleSize.Small,
                        icon = DawIcons.Tap,
                        contentDescription = "Tap Tempo",
                        onClick = {
                            val now = System.currentTimeMillis()
                            if (lastTapTime != 0L) {
                                val delta = now - lastTapTime
                                if (delta in 200..3000) {
                                    val tappedBpm = (60000.0 / delta).coerceIn(40.0, 240.0)
                                    onBpmChange(tappedBpm)
                                }
                            }
                            lastTapTime = now
                        }
                    )
                    FloatingCircle(
                        size = FloatingCircleSize.Small,
                        icon = DawIcons.Metronome,
                        tint = if (isMetronomeActive) Daw.colors.mint.base else Daw.colors.inkOnLight,
                        contentDescription = "Metronome",
                        onClick = { isMetronomeActive = !isMetronomeActive }
                    )
                }
            }
        }
    }
}

@Preview(name = "BpmDial Preview")
@Composable
private fun BpmDialPreview() {
    DawTheme {
        BpmDial(
            bpm = 128.0,
            onBpmChange = {},
            onDismissRequest = {}
        )
    }
}
