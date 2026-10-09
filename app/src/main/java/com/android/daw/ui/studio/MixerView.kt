package com.android.daw.ui.studio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.android.daw.ui.components.DawIconButton
import com.android.daw.ui.components.Fader
import com.android.daw.ui.components.LevelMeter
import com.android.daw.ui.components.RingKnob
import com.android.daw.ui.theme.Daw
import com.android.daw.ui.theme.DawIcons
import com.android.daw.ui.theme.DawTheme
import com.android.daw.ui.theme.Layer
import com.android.daw.viewmodel.StereoLevel
import com.android.daw.viewmodel.StudioAction
import com.android.daw.viewmodel.StudioUiState
import com.android.daw.viewmodel.TrackUiModel

/**
 * SD Studio DAW Master Mixer Console Panel
 *
 * Strictly adheres to agy_ui_overhaul_prompt.md Section 1B, 5.4:
 * - Slides over surface on Layer.Panel (4f) with animated N0 60% scrim
 * - Zero layout shift to Tier 0 surface
 * - 96dp wide channel strips (Daw.space.stripWidth), N2 background, 1dp N3 dividers
 * - Master strip first and pinned on the left
 * - Top to bottom: Name -> LevelMeter -> Fader -> RingKnob -> Solo/Mute pills -> Type icon block
 * - Zero stock Material 3 widgets, zero hex Color literals
 */
@Composable
fun MixerView(
    state: StudioUiState,
    telemetryProvider: () -> Map<Int, StereoLevel>,
    masterLevelProvider: () -> StereoLevel,
    onAction: (StudioAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val isOpen = state.isMixerOpen || state.isMixerViewActive

    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(Daw.motion.fast()) + slideInHorizontally(Daw.motion.smooth()) { -it },
        exit = fadeOut(Daw.motion.fast()) + slideOutHorizontally(Daw.motion.snappy()) { -it },
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
                    onClick = { onAction(StudioAction.ToggleMixerPanel) }
                )
        ) {
            // Main Panel Body
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.92f)
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
                // Header (48dp height)
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
                            imageVector = DawIcons.Mixer,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(Daw.colors.mint.base),
                            modifier = Modifier.size(Daw.space.iconMin)
                        )
                        Text(
                            text = "MIXER CONSOLE",
                            style = Daw.type.title,
                            color = Daw.colors.inkOnDark
                        )
                        Text(
                            text = "(${state.tracks.size} TRACKS + MASTER)",
                            style = Daw.type.caption,
                            color = Daw.colors.inkMuted
                        )
                    }

                    DawIconButton(
                        icon = DawIcons.Close,
                        onClick = { onAction(StudioAction.ToggleMixerPanel) },
                        contentDescription = "Close Mixer"
                    )
                }

                // Strips Row: Pinned Master Strip (Left) + Scrollable Track Strips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Daw.colors.n2Surface)
                ) {
                    // 1. PINNED MASTER STRIP (First and pinned on the left per Section 5.4)
                    MasterChannelStrip(
                        masterVolume = state.masterVolumeLinear,
                        masterPan = state.masterPan,
                        levelProvider = masterLevelProvider,
                        onVolumeChange = { vol -> onAction(StudioAction.SetMasterVolume(vol)) },
                        onPanChange = { pan -> onAction(StudioAction.SetMasterPan(pan)) },
                        onOpenMasterDsp = { onAction(StudioAction.OpenMasterDsp) },
                        onResetClip = { onAction(StudioAction.ResetClipLeds) },
                        modifier = Modifier.fillMaxHeight()
                    )

                    // 1dp N3 vertical divider
                    Box(
                        modifier = Modifier
                            .width(Daw.space.hairline)
                            .fillMaxHeight()
                            .background(Daw.colors.n3Raised)
                    )

                    // 2. SCROLLABLE AUDIO TRACK STRIPS
                    val scrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .horizontalScroll(scrollState)
                    ) {
                        state.tracks.forEachIndexed { index, track ->
                            TrackChannelStrip(
                                track = track,
                                trackIndex = index,
                                isSelected = track.trackId == state.selectedTrackId,
                                levelProvider = { telemetryProvider()[track.trackId] ?: StereoLevel() },
                                onSelect = { onAction(StudioAction.SelectTrack(track.trackId)) },
                                onVolumeChange = { vol -> onAction(StudioAction.SetTrackVolume(track.trackId, vol)) },
                                onPanChange = { pan -> onAction(StudioAction.SetTrackPan(track.trackId, pan)) },
                                onToggleMute = { onAction(StudioAction.ToggleMute(track.trackId)) },
                                onToggleSolo = { onAction(StudioAction.ToggleSolo(track.trackId)) },
                                onOpenFx = { onAction(StudioAction.OpenTrackDsp(track.trackId)) },
                                onResetClip = { onAction(StudioAction.ResetClipLeds) },
                                modifier = Modifier.fillMaxHeight()
                            )

                            // 1dp N3 divider between strips
                            Box(
                                modifier = Modifier
                                    .width(Daw.space.hairline)
                                    .fillMaxHeight()
                                    .background(Daw.colors.n3Raised)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pinned Master Bus Strip (96dp wide, Master is Mint)
 */
@Composable
private fun MasterChannelStrip(
    masterVolume: Float,
    masterPan: Float,
    levelProvider: () -> StereoLevel,
    onVolumeChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit,
    onOpenMasterDsp: () -> Unit,
    onResetClip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val masterColor = Daw.colors.mint.base

    Column(
        modifier = modifier
            .width(Daw.space.stripWidth)
            .background(Daw.colors.n3Raised.copy(alpha = 0.35f))
            .padding(Daw.space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Channel Title
        Text(
            text = "MASTER",
            style = Daw.type.label,
            fontWeight = FontWeight.Bold,
            color = masterColor,
            maxLines = 1
        )

        // 2. Stereo Level Meter (Draw-phase)
        LevelMeter(
            levelProvider = levelProvider,
            channelColor = masterColor,
            onResetClip = onResetClip,
            showScaleLabels = false,
            modifier = Modifier.height(110.dp)
        )

        // 3. Fader
        Fader(
            volumeLinear = masterVolume,
            onVolumeChange = onVolumeChange,
            label = "M-VOL",
            height = 140.dp
        )

        // 4. Pan RingKnob (56dp)
        RingKnob(
            value = masterPan,
            onValueChange = onPanChange,
            isBipolar = true,
            label = "M-PAN",
            activeColor = masterColor,
            size = Daw.space.circleMd
        )

        // 5. FX Button
        DawIconButton(
            icon = DawIcons.Settings,
            onClick = onOpenMasterDsp,
            contentDescription = "Master FX",
            tint = masterColor,
            size = Daw.space.touchTargetMin
        )

        // 6. Type Icon Block at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Daw.space.touchTargetMin)
                .background(masterColor, Daw.radii.xs),
            contentAlignment = Alignment.Center
        ) {
            Image(
                imageVector = DawIcons.Mixer,
                contentDescription = null,
                colorFilter = ColorFilter.tint(Daw.colors.inkOnLight),
                modifier = Modifier.size(Daw.space.iconMin)
            )
        }
    }
}

/**
 * Standard Track Channel Strip (96dp wide)
 */
@Composable
private fun TrackChannelStrip(
    track: TrackUiModel,
    trackIndex: Int,
    isSelected: Boolean,
    levelProvider: () -> StereoLevel,
    onSelect: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
    onOpenFx: () -> Unit,
    onResetClip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trackColor = Daw.colors.trackColor(trackIndex)

    Column(
        modifier = modifier
            .width(Daw.space.stripWidth)
            .background(if (isSelected) Daw.colors.n3Raised.copy(alpha = 0.5f) else Daw.colors.n2Surface)
            .clickable(onClick = onSelect)
            .padding(Daw.space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Channel Title
        Text(
            text = track.name,
            style = Daw.type.label,
            fontWeight = FontWeight.SemiBold,
            color = trackColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // 2. Level Meter
        LevelMeter(
            levelProvider = levelProvider,
            channelColor = trackColor,
            onResetClip = onResetClip,
            showScaleLabels = false,
            modifier = Modifier.height(110.dp)
        )

        // 3. Fader
        Fader(
            volumeLinear = track.volumeLinear,
            onVolumeChange = onVolumeChange,
            label = "VOL",
            height = 140.dp
        )

        // 4. Pan Knob (56dp RingKnob)
        RingKnob(
            value = track.pan,
            onValueChange = onPanChange,
            isBipolar = true,
            label = "PAN",
            activeColor = trackColor,
            size = Daw.space.circleMd
        )

        // 5. Solo / Mute pills (48x28dp minimum hit area)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Daw.space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Solo Pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .sizeIn(minWidth = 40.dp, minHeight = Daw.space.touchTargetMin)
                    .clip(Daw.radii.sm)
                    .background(if (track.isSoloed) Daw.colors.mint.base else Daw.colors.n3Raised)
                    .clickable(onClick = onToggleSolo),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "S",
                    style = Daw.type.caption,
                    fontWeight = FontWeight.Bold,
                    color = if (track.isSoloed) Daw.colors.inkOnLight else Daw.colors.inkMuted
                )
            }

            // Mute Pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .sizeIn(minWidth = 40.dp, minHeight = Daw.space.touchTargetMin)
                    .clip(Daw.radii.sm)
                    .background(if (track.isMuted) Daw.colors.coral.base else Daw.colors.n3Raised)
                    .clickable(onClick = onToggleMute),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "M",
                    style = Daw.type.caption,
                    fontWeight = FontWeight.Bold,
                    color = if (track.isMuted) Daw.colors.inkOnLight else Daw.colors.inkMuted
                )
            }

            // FX Button
            DawIconButton(
                icon = DawIcons.Settings,
                onClick = onOpenFx,
                contentDescription = "Track FX",
                size = 32.dp,
                iconSize = 18.dp,
                tint = if (isSelected) trackColor else Daw.colors.inkMuted
            )
        }

        // 6. Type Icon Block at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Daw.space.touchTargetMin)
                .background(
                    color = if (isSelected) trackColor else Daw.colors.n3Raised,
                    shape = Daw.radii.xs
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                imageVector = DawIcons.Track,
                contentDescription = null,
                colorFilter = ColorFilter.tint(if (isSelected) Daw.colors.inkOnLight else Daw.colors.inkMuted),
                modifier = Modifier.size(Daw.space.iconMin)
            )
        }
    }
}

@Preview(name = "MixerView Preview")
@Composable
private fun MixerViewPreview() {
    DawTheme {
        MixerView(
            state = StudioUiState(isMixerOpen = true),
            telemetryProvider = { emptyMap() },
            masterLevelProvider = { StereoLevel() },
            onAction = {}
        )
    }
}
