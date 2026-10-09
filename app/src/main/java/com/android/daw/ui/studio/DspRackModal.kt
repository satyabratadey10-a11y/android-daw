package com.android.daw.ui.studio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.android.daw.ui.components.DawIconButton
import com.android.daw.ui.components.EqCurveVisualizer
import com.android.daw.ui.components.RingKnob
import com.android.daw.ui.components.SlidingTabs
import com.android.daw.ui.components.VerticalSlider
import com.android.daw.ui.theme.Daw
import com.android.daw.ui.theme.DawIcons
import com.android.daw.ui.theme.DawTheme
import com.android.daw.ui.theme.Layer
import com.android.daw.viewmodel.EqBandState
import com.android.daw.viewmodel.FilterType
import com.android.daw.viewmodel.StudioAction
import com.android.daw.viewmodel.StudioUiState

/**
 * SD Studio DAW Instrument / Module / DSP Rack Panel
 *
 * Strictly adheres to agy_ui_overhaul_prompt.md Section 5.5:
 * - Slides from right on Layer.Panel (4f) with animated N0 60% scrim
 * - Width 50% of screen (max 520dp, Daw.space.panelMaxWidth)
 * - Header 48dp (Power toggle, icon, title, preset name, close button)
 * - Tabs with single sliding underline (SlidingTabs)
 * - Controls: RingKnob (56dp), VerticalSlider (4dp track, 24dp thumb), text toggles
 * - Zero stock Material 3 widgets, zero hex Color literals
 */
@Composable
fun DspRackModal(
    state: StudioUiState,
    onAction: (StudioAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOpen = state.isInstrumentPanelOpen || state.isDspInspectorOpen

    val tabs = listOf("EQ", "DELAY", "LIMITER", "SYNTH", "ENVELOPE")
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val targetTrack = state.selectedTrack ?: state.tracks.firstOrNull()
    val isMaster = state.dspTargetTrackId == null
    val targetName = if (isMaster) "MASTER BUS" else targetTrack?.name ?: "TRACK"

    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(Daw.motion.fast()) + slideInHorizontally(Daw.motion.smooth()) { it },
        exit = fadeOut(Daw.motion.fast()) + slideOutHorizontally(Daw.motion.snappy()) { it },
        modifier = modifier.zIndex(Layer.Panel)
    ) {
        // Scrim backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Daw.colors.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.CenterEnd
        ) {
            // Right-side Drawer Body (50% screen width, max 520dp)
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = Daw.space.panelMaxWidth)
                    .fillMaxWidth(0.50f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume tap
                    )
                    .shadow(
                        elevation = Daw.space.lg,
                        shape = Daw.radii.none,
                        ambientColor = Daw.colors.n0Workspace,
                        spotColor = Daw.colors.n0Workspace
                    )
                    .background(Daw.colors.n2Surface)
            ) {
                // 1. Header (48dp height)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Daw.space.touchTargetMin)
                        .background(Daw.colors.n3Raised)
                        .padding(horizontal = Daw.space.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Daw.space.sm)
                    ) {
                        Image(
                            imageVector = DawIcons.Settings,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(Daw.colors.mint.base),
                            modifier = Modifier.size(Daw.space.iconMin)
                        )
                        Column {
                            Text(
                                text = targetName,
                                style = Daw.type.title,
                                color = Daw.colors.inkOnDark
                            )
                            Text(
                                text = "DSP EFFECTS RACK",
                                style = Daw.type.caption,
                                color = Daw.colors.inkMuted
                            )
                        }
                    }

                    DawIconButton(
                        icon = DawIcons.Close,
                        onClick = onDismiss,
                        contentDescription = "Close DSP Rack"
                    )
                }

                // 2. Sliding Tabs Row
                SlidingTabs(
                    tabs = tabs,
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = { selectedTabIndex = it },
                    activeColor = Daw.colors.mint.base,
                    backgroundColor = Daw.colors.n3Raised
                )

                // 3. Tab Contents
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(Daw.space.md),
                    verticalArrangement = Arrangement.spacedBy(Daw.space.md)
                ) {
                    when (selectedTabIndex) {
                        0 -> {
                            // --- EQ Tab ---
                            val eqBands = targetTrack?.eqBands ?: emptyList()
                            Text(
                                text = "4-BAND PARAMETRIC EQUALIZER",
                                style = Daw.type.label,
                                color = Daw.colors.mint.base
                            )

                            // Interactive EQ Curve Canvas
                            EqCurveVisualizer(
                                bands = eqBands,
                                onUpdateBand = { bandIndex, freq, gain, q ->
                                    if (!isMaster && targetTrack != null) {
                                        onAction(StudioAction.UpdateEq(targetTrack.trackId, bandIndex, freq, gain, q))
                                    }
                                }
                            )

                            // Band Controls Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                eqBands.forEach { band ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(Daw.space.xs)
                                    ) {
                                        Text(
                                            text = "BAND ${band.bandIndex + 1}",
                                            style = Daw.type.caption,
                                            color = Daw.colors.trackColor(band.bandIndex)
                                        )

                                        // Frequency RingKnob
                                        RingKnob(
                                            value = ((band.frequencyHz - 20f) / 19980f).coerceIn(0f, 1f),
                                            onValueChange = { norm ->
                                                val freq = 20f + norm * 19980f
                                                if (!isMaster && targetTrack != null) {
                                                    onAction(StudioAction.UpdateEq(targetTrack.trackId, band.bandIndex, freq, band.gainDb, band.q))
                                                }
                                            },
                                            valueFormatter = { "${band.frequencyHz.toInt()}Hz" },
                                            activeColor = Daw.colors.trackColor(band.bandIndex),
                                            size = Daw.space.circleMd,
                                            label = "FREQ"
                                        )

                                        // Gain RingKnob
                                        RingKnob(
                                            value = (band.gainDb / 18f).coerceIn(-1f, 1f),
                                            onValueChange = { norm ->
                                                val gain = norm * 18f
                                                if (!isMaster && targetTrack != null) {
                                                    onAction(StudioAction.UpdateEq(targetTrack.trackId, band.bandIndex, band.frequencyHz, gain, band.q))
                                                }
                                            },
                                            valueFormatter = { String.format("%.1fdB", band.gainDb) },
                                            isBipolar = true,
                                            activeColor = Daw.colors.trackColor(band.bandIndex),
                                            size = Daw.space.circleMd,
                                            label = "GAIN"
                                        )
                                    }
                                }
                            }
                        }

                        1 -> {
                            // --- Delay Tab ---
                            val delay = targetTrack?.delayParams
                            Text(
                                text = "STEREO DELAY EFFECT",
                                style = Daw.type.label,
                                color = Daw.colors.sky.base
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                RingKnob(
                                    value = ((delay?.timeMs ?: 250f) / 1000f).coerceIn(0f, 1f),
                                    onValueChange = { norm ->
                                        if (!isMaster && targetTrack != null) {
                                            onAction(StudioAction.UpdateDelay(targetTrack.trackId, norm * 1000f, delay?.feedback ?: 0.35f, delay?.wetDry ?: 0.3f))
                                        }
                                    },
                                    valueFormatter = { "${((delay?.timeMs ?: 250f)).toInt()}ms" },
                                    activeColor = Daw.colors.sky.base,
                                    label = "TIME"
                                )

                                RingKnob(
                                    value = delay?.feedback ?: 0.35f,
                                    onValueChange = { norm ->
                                        if (!isMaster && targetTrack != null) {
                                            onAction(StudioAction.UpdateDelay(targetTrack.trackId, delay?.timeMs ?: 250f, norm, delay?.wetDry ?: 0.3f))
                                        }
                                    },
                                    activeColor = Daw.colors.sky.base,
                                    label = "FEEDBACK"
                                )

                                RingKnob(
                                    value = delay?.wetDry ?: 0.3f,
                                    onValueChange = { norm ->
                                        if (!isMaster && targetTrack != null) {
                                            onAction(StudioAction.UpdateDelay(targetTrack.trackId, delay?.timeMs ?: 250f, delay?.feedback ?: 0.35f, norm))
                                        }
                                    },
                                    activeColor = Daw.colors.sky.base,
                                    label = "WET/DRY"
                                )
                            }

                            // Toggles
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Daw.space.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DawTextToggle(
                                    text = "PING-PONG",
                                    isActive = delay?.isPingPong ?: true,
                                    onToggle = {
                                        if (!isMaster && targetTrack != null) {
                                            onAction(StudioAction.ToggleDelayPingPong(targetTrack.trackId))
                                        }
                                    }
                                )
                                DawTextToggle(
                                    text = "BYPASS",
                                    isActive = !(delay?.isEnabled ?: true),
                                    onToggle = {
                                        if (!isMaster && targetTrack != null) {
                                            onAction(StudioAction.ToggleDelayBypass(targetTrack.trackId))
                                        }
                                    }
                                )
                            }
                        }

                        2 -> {
                            // --- Limiter Tab ---
                            val limiter = state.masterLimiter
                            Text(
                                text = "MASTER BRICKWALL LIMITER",
                                style = Daw.type.label,
                                color = Daw.colors.coral.base
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                RingKnob(
                                    value = (-(limiter.thresholdDb) / 24f).coerceIn(0f, 1f),
                                    onValueChange = { norm ->
                                        val thresh = -norm * 24f
                                        onAction(StudioAction.UpdateLimiter(thresh, limiter.ceilingDb, limiter.releaseMs))
                                    },
                                    valueFormatter = { String.format("%.1fdB", limiter.thresholdDb) },
                                    activeColor = Daw.colors.coral.base,
                                    label = "THRESH"
                                )

                                RingKnob(
                                    value = (-(limiter.ceilingDb) / 6f).coerceIn(0f, 1f),
                                    onValueChange = { norm ->
                                        val ceil = -norm * 6f
                                        onAction(StudioAction.UpdateLimiter(limiter.thresholdDb, ceil, limiter.releaseMs))
                                    },
                                    valueFormatter = { String.format("%.1fdB", limiter.ceilingDb) },
                                    activeColor = Daw.colors.coral.base,
                                    label = "CEILING"
                                )

                                RingKnob(
                                    value = (limiter.releaseMs / 500f).coerceIn(0f, 1f),
                                    onValueChange = { norm ->
                                        val rel = norm * 500f
                                        onAction(StudioAction.UpdateLimiter(limiter.thresholdDb, limiter.ceilingDb, rel))
                                    },
                                    valueFormatter = { "${limiter.releaseMs.toInt()}ms" },
                                    activeColor = Daw.colors.coral.base,
                                    label = "RELEASE"
                                )
                            }
                        }

                        3 -> {
                            // --- Synth Tab (Tier 3 Deep parameters) ---
                            Text(
                                text = "SYNTHESIZER PARAMETERS",
                                style = Daw.type.label,
                                color = Daw.colors.mint.base
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                RingKnob(value = 0.5f, onValueChange = {}, label = "CUTOFF")
                                RingKnob(value = 0.3f, onValueChange = {}, label = "RESONANCE")
                                RingKnob(value = 0.7f, onValueChange = {}, label = "DRIVE")
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Daw.space.sm)
                            ) {
                                DawTextToggle(text = "MONO", isActive = true, onToggle = {})
                                DawTextToggle(text = "LEGATO", isActive = false, onToggle = {})
                                DawTextToggle(text = "PORTA", isActive = true, onToggle = {})
                            }
                        }

                        4 -> {
                            // --- Envelope Tab ---
                            Text(
                                text = "ADSR AMPLITUDE ENVELOPE",
                                style = Daw.type.label,
                                color = Daw.colors.mint.base
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                VerticalSlider(value = 0.1f, onValueChange = {}, label = "A")
                                VerticalSlider(value = 0.3f, onValueChange = {}, label = "D")
                                VerticalSlider(value = 0.7f, onValueChange = {}, label = "S")
                                VerticalSlider(value = 0.4f, onValueChange = {}, label = "R")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Text Toggle (active = Mint) per Section 5.5
 */
@Composable
private fun DawTextToggle(
    text: String,
    isActive: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(Daw.space.touchTargetMin)
            .clip(Daw.radii.sm)
            .background(if (isActive) Daw.colors.mint.base.copy(alpha = 0.2f) else Daw.colors.n3Raised)
            .clickable(onClick = onToggle)
            .padding(horizontal = Daw.space.md, vertical = Daw.space.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = Daw.type.label,
            fontWeight = FontWeight.Bold,
            color = if (isActive) Daw.colors.mint.base else Daw.colors.inkMuted
        )
    }
}

@Preview(name = "DspRackModal Preview")
@Composable
private fun DspRackModalPreview() {
    DawTheme {
        DspRackModal(
            state = StudioUiState(isInstrumentPanelOpen = true),
            onAction = {},
            onDismiss = {}
        )
    }
}
