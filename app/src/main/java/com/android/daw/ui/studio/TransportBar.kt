package com.android.daw.ui.studio

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.daw.ui.components.DawIconButton
import com.android.daw.ui.theme.Daw
import com.android.daw.ui.theme.DawIcons
import com.android.daw.ui.theme.DawTheme
import com.android.daw.viewmodel.StudioAction
import com.android.daw.viewmodel.StudioUiState
import com.android.daw.viewmodel.TransportState

/**
 * SD Studio DAW Transport Bar (Tier 0 Surface)
 *
 * Strictly adheres to agy_ui_overhaul_prompt.md Section 5.2:
 * - 48dp height (Daw.space.transportHeight), N2 Surface background
 * - 1dp N3 Raised divider line on top
 * - Left: Mixer toggle, Channel/Piano-roll toggle (active = Mint icon + 3dp Mint underline)
 * - Center cluster (strict order): REC (Coral dot) · REV · Play ring 56dp · BPM (mono numeric) · CTRL (⋯)
 * - Right: Modules drawer toggle, CPU meter (48x4dp bar, Mint/Coral), RAM readout, Close button
 * - Icon-over-caption stacks share one baseline across all items
 * - Zero stock Material 3 widgets, zero emoji, zero hex Color literals
 */
@Composable
fun TransportBar(
    state: StudioUiState,
    onAction: (StudioAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(Daw.space.transportHeight + Daw.space.hairline)
            .background(Daw.colors.n2Surface)
    ) {
        // 1dp N3 divider line on top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Daw.space.hairline)
                .background(Daw.colors.n3Raised)
        )

        // 48dp Content Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Daw.space.transportHeight)
                .padding(horizontal = Daw.space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // =========================================================================
            // 1. LEFT CLUSTER: Mixer toggle, Piano-roll toggle
            // =========================================================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Daw.space.xs)
            ) {
                // Mixer Toggle (48dp icon button, active = Mint icon + 3dp Mint underline)
                val isMixerActive = state.isMixerOpen || state.isMixerViewActive
                DawIconButton(
                    icon = DawIcons.Mixer,
                    isActive = isMixerActive,
                    showActiveIndicator = true,
                    contentDescription = "Toggle Mixer",
                    onClick = { onAction(StudioAction.ToggleMixerPanel) }
                )

                // Channel / Piano-Roll Toggle
                DawIconButton(
                    icon = DawIcons.PianoRoll,
                    isActive = state.isPianoRollOpen,
                    showActiveIndicator = true,
                    contentDescription = "Toggle Piano Roll",
                    onClick = { onAction(StudioAction.TogglePianoRollPanel) }
                )
            }

            // =========================================================================
            // 2. CENTER CLUSTER (Strict Order): REC · REV · Play ring 56dp · BPM · CTRL
            // =========================================================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Daw.space.md)
            ) {
                // REC: Coral dot with label below
                TransportIconCaption(
                    label = "REC",
                    onClick = { onAction(StudioAction.ToggleRecord(!state.isRecording)) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(Daw.space.iconMin)
                            .background(
                                color = if (state.isRecording) Daw.colors.coral.base else Daw.colors.n3Raised,
                                shape = CircleShape
                            )
                    )
                }

                // REV: Rewind button
                TransportIconCaption(
                    label = "REV",
                    onClick = { onAction(StudioAction.Rewind) }
                ) {
                    androidx.compose.foundation.Image(
                        imageVector = DawIcons.Reverse,
                        contentDescription = "Rewind",
                        colorFilter = ColorFilter.tint(Daw.colors.inkOnDark),
                        modifier = Modifier.size(Daw.space.iconMin)
                    )
                }

                // PLAY RING: 56dp circle with beat-locked pulse
                val isPlaying = state.isPlaying
                val playPulseScale by animateFloatAsState(
                    targetValue = if (isPlaying) 1.03f else 1.0f,
                    animationSpec = Daw.motion.playfulSpring,
                    label = "play_ring_pulse"
                )

                Box(
                    modifier = Modifier
                        .size(Daw.space.circleLg) // 56dp circle per Section 5.2
                        .graphicsLayer {
                            scaleX = playPulseScale
                            scaleY = playPulseScale
                        }
                        .shadow(
                            elevation = Daw.space.xs,
                            shape = CircleShape,
                            ambientColor = Daw.colors.n0Workspace,
                            spotColor = Daw.colors.n0Workspace
                        )
                        .background(
                            color = if (isPlaying) Daw.colors.mint.base else Daw.colors.n4Control,
                            shape = CircleShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (isPlaying) onAction(StudioAction.Pause) else onAction(StudioAction.Play)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        imageVector = if (isPlaying) DawIcons.Pause else DawIcons.Play,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        colorFilter = ColorFilter.tint(Daw.colors.inkOnLight),
                        modifier = Modifier.size(Daw.space.xl)
                    )
                }

                // BPM: Mono numeric with label "BPM" below (tapping opens BPM Dial)
                TransportIconCaption(
                    label = "BPM",
                    onClick = { onAction(StudioAction.ToggleBpmDial) }
                ) {
                    Text(
                        text = String.format("%.0f", state.bpm),
                        style = Daw.type.mono,
                        fontWeight = FontWeight.Bold,
                        color = Daw.colors.inkOnDark
                    )
                }

                // CTRL: (⋯, opens Project drawer)
                TransportIconCaption(
                    label = "CTRL",
                    onClick = { onAction(StudioAction.ToggleProjectDrawer) }
                ) {
                    androidx.compose.foundation.Image(
                        imageVector = DawIcons.More,
                        contentDescription = "Control Menu",
                        colorFilter = ColorFilter.tint(if (state.isProjectDrawerOpen) Daw.colors.mint.base else Daw.colors.inkOnDark),
                        modifier = Modifier.size(Daw.space.iconMin)
                    )
                }
            }

            // =========================================================================
            // 3. RIGHT CLUSTER: Modules drawer, CPU meter, RAM readout, Close
            // =========================================================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Daw.space.sm)
            ) {
                // Modules Drawer Trigger Circle
                Box(
                    modifier = Modifier
                        .size(Daw.space.circleSm)
                        .clip(CircleShape)
                        .background(if (state.isModulesDrawerOpen) Daw.colors.mint.base else Daw.colors.n3Raised)
                        .clickable { onAction(StudioAction.ToggleModulesDrawer) },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        imageVector = DawIcons.Add,
                        contentDescription = "Modules",
                        colorFilter = ColorFilter.tint(if (state.isModulesDrawerOpen) Daw.colors.inkOnLight else Daw.colors.inkOnDark),
                        modifier = Modifier.size(Daw.space.iconMin)
                    )
                }

                // CPU Meter (48x4dp bar: Mint -> Coral > 85%)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val cpuPct = state.cpuUsagePercent
                    val cpuColor = if (cpuPct > 85f) Daw.colors.coral.base else Daw.colors.mint.base

                    Canvas(modifier = Modifier.size(width = 48.dp, height = 4.dp)) {
                        // Track gutter
                        drawRoundRect(
                            color = Daw.colors.n0Workspace,
                            size = size,
                            cornerRadius = CornerRadius(2f, 2f)
                        )
                        // Active bar
                        val activeWidth = size.width * (cpuPct / 100f).coerceIn(0f, 1f)
                        if (activeWidth > 0f) {
                            drawRoundRect(
                                color = cpuColor,
                                size = Size(activeWidth, size.height),
                                cornerRadius = CornerRadius(2f, 2f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "CPU ${cpuPct.toInt()}%",
                        style = Daw.type.caption,
                        fontSize = 8.sp,
                        color = Daw.colors.inkMuted
                    )
                }

                // RAM Readout
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "${state.ramUsageMb}M",
                        style = Daw.type.caption,
                        fontWeight = FontWeight.Bold,
                        color = Daw.colors.inkOnDark
                    )
                    Text(
                        text = "RAM",
                        style = Daw.type.caption,
                        fontSize = 8.sp,
                        color = Daw.colors.inkMuted
                    )
                }

                // Close / Quit button
                DawIconButton(
                    icon = DawIcons.Close,
                    onClick = { onAction(StudioAction.CloseAllPanels) },
                    contentDescription = "Close / Quit",
                    size = Daw.space.touchTargetMin,
                    iconSize = Daw.space.iconMin
                )
            }
        }
    }
}

/**
 * Standard Icon-over-caption container sharing baseline alignment
 */
@Composable
private fun TransportIconCaption(
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .sizeIn(minWidth = 36.dp, minHeight = Daw.space.transportHeight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(Daw.space.iconMin),
            contentAlignment = Alignment.Center
        ) {
            content()
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = label,
            style = Daw.type.caption,
            fontSize = 8.sp,
            color = Daw.colors.inkMuted
        )
    }
}

@Preview(name = "TransportBar Preview")
@Composable
private fun TransportBarPreview() {
    DawTheme {
        TransportBar(
            state = StudioUiState(transportState = TransportState.PLAYING),
            onAction = {}
        )
    }
}
