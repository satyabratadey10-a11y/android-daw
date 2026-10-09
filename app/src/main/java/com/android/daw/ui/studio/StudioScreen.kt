package com.android.daw.ui.studio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.android.daw.ui.components.BpmDial
import com.android.daw.ui.theme.Daw
import com.android.daw.ui.theme.DawTheme
import com.android.daw.ui.theme.Layer
import com.android.daw.viewmodel.StereoLevel
import com.android.daw.viewmodel.StudioAction
import com.android.daw.viewmodel.StudioUiState

/**
 * StudioScreen
 *
 * Root screen orchestrating the 4-Tier Surface vs. Depth Architecture:
 * - Tier 0 (Surface): Timeline sequencer (TimelineView) + Transport bar (TransportBar). Calm, zero top bar.
 * - Tier 1 (Contextual): Action chips & popup menus on clip selection.
 * - Tier 2 (Panels): MixerView, DspRackModal, PianoRollPanel, ModulesDrawer, ProjectDrawer, BpmDial.
 *   Slide over surface on proper z-layers (Layer.Panel, Layer.Popup) with N0 60% scrim; zero layout shift.
 * - Tier 3 (Deep): Advanced synth parameters, envelopes, and settings inside panels.
 *
 * Constraints:
 * - Single-point root insets: WindowInsets.safeDrawing applied once at root container.
 * - Zero stock Material 3 widgets (Scaffold, Surface, Button, Slider, Switch, Card, AlertDialog).
 * - Zero hex Color literals; strictly tokenized via Daw.colors.* and Daw.space.*.
 * - Modifier.zIndex() exclusively with Layer.* constants.
 */
@Composable
fun StudioScreen(
    state: StudioUiState,
    playheadProvider: () -> Long,
    telemetryProvider: () -> Map<Int, StereoLevel>,
    masterLevelProvider: () -> StereoLevel,
    onAction: (StudioAction) -> Unit,
    modifier: Modifier = Modifier
) {
    // Single-point root container with safeDrawing insets (displayCutout + systemBars)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Daw.colors.n0Workspace)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .zIndex(Layer.Workspace)
    ) {
        // =========================================================================
        // TIER 0: CALM SURFACE VERTICAL STACK (Timeline area -> Transport bar)
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(Layer.Content)
        ) {
            // Multi-track Timeline Sequencer (Takes all available height)
            TimelineView(
                state = state,
                playheadProvider = playheadProvider,
                onAction = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // Persistent 48dp Transport Bar docked at bottom
            TransportBar(
                state = state,
                onAction = onAction,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // =========================================================================
        // TIER 2: SLIDING PANELS & DRAWERS (Layer.Panel = 4f, Zero layout shift)
        // =========================================================================

        // 1. Mixer Panel (Slides over surface from left with scrim)
        MixerView(
            state = state,
            telemetryProvider = telemetryProvider,
            masterLevelProvider = masterLevelProvider,
            onAction = onAction,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Instrument / Module / DSP Rack Panel (Slides from right, 50% width)
        DspRackModal(
            state = state,
            onAction = onAction,
            onDismiss = { onAction(StudioAction.ToggleInstrumentPanel) },
            modifier = Modifier.fillMaxSize()
        )

        // 3. Piano Roll Bottom Sheet (Slides from bottom, 40% height)
        PianoRollPanel(
            state = state,
            onAction = onAction,
            onDismiss = { onAction(StudioAction.TogglePianoRollPanel) },
            modifier = Modifier.fillMaxSize()
        )

        // 4. Modules & Instruments Left Drawer (50% width)
        ModulesDrawer(
            state = state,
            onAction = onAction,
            onDismiss = { onAction(StudioAction.ToggleModulesDrawer) },
            modifier = Modifier.fillMaxSize()
        )

        // 5. Project & Settings Right Drawer (56% width)
        ProjectDrawer(
            state = state,
            onAction = onAction,
            onDismiss = { onAction(StudioAction.ToggleProjectDrawer) },
            modifier = Modifier.fillMaxSize()
        )

        // =========================================================================
        // TIER 2: POPUP OVERLAYS (Layer.Popup = 5f)
        // =========================================================================

        // Centered 160dp Circular BPM Dial Popup
        if (state.isBpmDialOpen) {
            BpmDial(
                bpm = state.bpm,
                onBpmChange = { newBpm -> onAction(StudioAction.SetTempo(newBpm)) },
                onDismissRequest = { onAction(StudioAction.ToggleBpmDial) }
            )
        }

        // =========================================================================
        // TIER 2: MODAL OVERLAYS (Layer.Modal = 6f)
        // =========================================================================

        // Offline Project Mixdown Export Modal
        if (state.isExporting) {
            ExportProgressModal(
                progressPercent = state.exportProgressPercent,
                onCancel = { onAction(StudioAction.CancelExport) }
            )
        }

        // =========================================================================
        // TOAST / TRANSIENT NOTIFICATION LAYER (Layer.Toast = 7f)
        // =========================================================================
        state.statusMessage?.let { msg ->
            StatusBanner(
                message = msg,
                onDismiss = { onAction(StudioAction.DismissStatusMessage) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = Daw.space.md)
                    .zIndex(Layer.Toast)
            )
        }
    }
}

/**
 * Status banner notification on Layer.Toast (7f)
 */
@Composable
private fun StatusBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(horizontal = Daw.space.lg)
            .shadow(elevation = Daw.space.sm, shape = Daw.radii.sm)
            .background(Daw.colors.n3Raised, Daw.radii.sm)
            .clickable(onClick = onDismiss)
            .padding(horizontal = Daw.space.lg, vertical = Daw.space.sm)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Daw.space.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(Daw.space.sm)
                    .background(Daw.colors.mint.base, CircleShape)
            )
            Text(
                text = message,
                style = Daw.type.label,
                color = Daw.colors.inkOnDark
            )
            Spacer(modifier = Modifier.width(Daw.space.xs))
            Text(
                text = "[DISMISS]",
                style = Daw.type.caption,
                fontWeight = FontWeight.Bold,
                color = Daw.colors.inkMuted
            )
        }
    }
}

/**
 * Offline Export Progress Overlay Dialog on Layer.Modal (6f)
 */
@Composable
private fun ExportProgressModal(
    progressPercent: Int,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(Layer.Modal)
            .background(Daw.colors.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {} // Scrim consumes taps
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(320.dp)
                .shadow(elevation = Daw.space.xl, shape = Daw.radii.md)
                .background(Daw.colors.n2Surface, Daw.radii.md)
                .padding(Daw.space.xl)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Daw.space.md)
            ) {
                Text(
                    text = "OFFLINE PROJECT MIXDOWN",
                    style = Daw.type.title,
                    color = Daw.colors.mint.base,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = "Summing multi-track planar audio buffers with TPDF dithering...",
                    style = Daw.type.caption,
                    color = Daw.colors.inkMuted
                )

                // Custom Canvas Linear Progress Bar
                val gutterColor = Daw.colors.n0Workspace
                val progressColor = Daw.colors.mint.base
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Daw.space.sm)
                ) {
                    val trackRadius = 4f
                    // Track gutter
                    drawRoundRect(
                        color = gutterColor,
                        size = size,
                        cornerRadius = CornerRadius(trackRadius, trackRadius)
                    )
                    // Progress bar
                    val activeWidth = size.width * (progressPercent / 100f).coerceIn(0f, 1f)
                    if (activeWidth > 0f) {
                        drawRoundRect(
                            color = progressColor,
                            size = Size(activeWidth, size.height),
                            cornerRadius = CornerRadius(trackRadius, trackRadius)
                        )
                    }
                }

                Text(
                    text = "$progressPercent%",
                    style = Daw.type.mono,
                    fontWeight = FontWeight.Bold,
                    color = Daw.colors.inkOnDark
                )

                // Cancel button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Daw.space.touchTargetMin)
                        .clip(Daw.radii.xs)
                        .background(Daw.colors.n3Raised)
                        .clickable(onClick = onCancel),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CANCEL EXPORT",
                        style = Daw.type.label,
                        fontWeight = FontWeight.Bold,
                        color = Daw.colors.coral.base
                    )
                }
            }
        }
    }
}

@Preview(name = "StudioScreen Preview")
@Composable
private fun StudioScreenPreview() {
    DawTheme {
        StudioScreen(
            state = StudioUiState(),
            playheadProvider = { 0L },
            telemetryProvider = { emptyMap() },
            masterLevelProvider = { StereoLevel() },
            onAction = {}
        )
    }
}
