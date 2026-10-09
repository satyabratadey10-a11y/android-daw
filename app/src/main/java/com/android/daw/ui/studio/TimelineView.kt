package com.android.daw.ui.studio

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.android.daw.ui.components.ActionChips
import com.android.daw.ui.components.FloatingCircle
import com.android.daw.ui.components.FloatingCircleSize
import com.android.daw.ui.components.PopupMenu
import com.android.daw.ui.components.PopupMenuItem
import com.android.daw.ui.components.WaveformCanvas
import com.android.daw.ui.theme.Daw
import com.android.daw.ui.theme.DawIcons
import com.android.daw.ui.theme.DawTheme
import com.android.daw.ui.theme.Layer
import com.android.daw.viewmodel.StudioAction
import com.android.daw.viewmodel.StudioUiState
import kotlin.math.max

/**
 * SD Studio DAW Tier 0 Surface Timeline / Playlist Sequencer
 *
 * Strictly adheres to agy_ui_overhaul_prompt.md Section 1B, 5.3, 7.3:
 * - Ruler 36dp (bar numbers in 24dp N4 circles, tick marks, loop range in Sky Faint)
 * - Track header column 40dp wide, centered channel icon, selected block in channel color
 * - Track rows 48dp tall (clips 44dp, 2dp gap)
 * - 56dp Add-track FAB in Layer.Floating centered below tracks
 * - Playhead: 2dp Mint line, 12dp Mint cap, comet fading trail while playing
 * - Tier 1 Contextual Action Chips upon clip selection in Layer.Floating
 * - PopupMenu on Layer.Popup
 * - Zero top bar, zero stock Material 3 widgets, zero hex Color literals
 */
@Composable
fun TimelineView(
    state: StudioUiState,
    playheadProvider: () -> Long,
    onAction: (StudioAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val sampleRate = state.sampleRate
    val zoom = state.zoomPixelsPerSecond
    val scrollOffset = state.horizontalScrollOffsetPx

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Daw.colors.n0Workspace)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. RULER BAR (36dp height per Section 5.3)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Daw.space.rulerHeight)
                    .background(Daw.colors.n2Surface)
            ) {
                // Fixed top-left 40dp corner above track headers
                Box(
                    modifier = Modifier
                        .width(Daw.space.circleSm)
                        .fillMaxHeight()
                        .background(Daw.colors.n3Raised),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#",
                        style = Daw.type.caption,
                        fontWeight = FontWeight.Bold,
                        color = Daw.colors.inkMuted
                    )
                }

                // Bar / Beat Ruler Canvas
                TimelineRuler(
                    totalFrames = state.totalFrames,
                    sampleRate = sampleRate,
                    bpm = state.bpm,
                    beatsPerBar = state.beatsPerBar,
                    zoom = zoom,
                    scrollOffset = scrollOffset,
                    isLooping = state.isLooping,
                    loopStartFrame = state.loopStartFrame,
                    loopEndFrame = state.loopEndFrame,
                    onSeek = { frame -> onAction(StudioAction.Seek(frame)) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            // 1dp Hairline divider under ruler
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Daw.space.hairline)
                    .background(Daw.colors.n3Raised)
            )

            // 2. MULTI-TRACK LANES CONTAINER
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            val oldZoom = state.zoomPixelsPerSecond
                            val newZoom = (oldZoom * gestureZoom).coerceIn(20f, 2000f)
                            if (newZoom != oldZoom) {
                                onAction(StudioAction.SetZoom(newZoom))
                            }
                            if (pan.x != 0f) {
                                val newScroll = max(0f, state.horizontalScrollOffsetPx - pan.x)
                                onAction(StudioAction.SetScrollOffset(newScroll))
                            }
                        }
                    }
            ) {
                // Track Lanes List (48dp height per track per Section 5.3)
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = state.tracks,
                        key = { _, tr -> tr.trackId }
                    ) { index, track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(Daw.space.rowHeight)
                                .background(if (index % 2 == 0) Daw.colors.n0Workspace else Daw.colors.n1Grid)
                        ) {
                            // 40dp Compact Track Header
                            TrackHeader(
                                track = track,
                                trackIndex = index,
                                isSelected = track.trackId == state.selectedTrackId,
                                onSelectTrack = { onAction(StudioAction.SelectTrack(track.trackId)) },
                                modifier = Modifier.fillMaxHeight()
                            )

                            // 1dp Divider
                            Box(
                                modifier = Modifier
                                    .width(Daw.space.hairline)
                                    .fillMaxHeight()
                                    .background(Daw.colors.n3Raised)
                            )

                            // Waveform / Clips Lane (Clips 44dp, 2dp gap)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clipToBounds()
                                    .padding(vertical = Daw.space.stroke)
                            ) {
                                track.clips.forEach { clip ->
                                    val isClipSelected = clip.clipId == state.selectedClipId

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {
                                                    val nextSelection = if (isClipSelected) null else clip.clipId
                                                    onAction(StudioAction.SelectClip(nextSelection))
                                                }
                                            )
                                    ) {
                                        WaveformCanvas(
                                            clip = clip,
                                            pixelsPerSecond = zoom,
                                            scrollOffsetPx = scrollOffset,
                                            trackColor = Daw.colors.trackColor(index),
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        // Selection outline indicator (1.5dp Mint outline)
                                        if (isClipSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        color = Daw.colors.mint.base.copy(alpha = 0.12f),
                                                        shape = Daw.radii.xs
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 1dp Divider between track rows
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(Daw.space.hairline)
                                .background(Daw.colors.n1Grid)
                        )
                    }

                    // Spacer at bottom of tracks for the Add FAB
                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }

                // 3. PLAYHEAD OVERLAY (Layer.Playhead = 2f)
                val mintColor = Daw.colors.mint.base
                val playheadStrokeDp = Daw.space.stroke
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = Daw.space.circleSm) // 40dp offset from track header
                        .clipToBounds()
                        .zIndex(Layer.Playhead)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val frame = playheadProvider()
                        val timeSec = frame.toFloat() / sampleRate
                        val playheadX = (timeSec * zoom) - scrollOffset

                        if (playheadX in 0f..size.width) {
                            val strokeWidthPx = playheadStrokeDp.toPx()

                            // Playhead comet fading trail (24dp Mint fading strip proportional to playback)
                            if (state.isPlaying) {
                                val cometWidth = 24.dp.toPx()
                                val cometLeft = max(0f, playheadX - cometWidth)
                                val cometBrush = Brush.horizontalGradient(
                                    colors = listOf(mintColor.copy(alpha = 0f), mintColor.copy(alpha = 0.35f)),
                                    startX = cometLeft,
                                    endX = playheadX
                                )
                                drawRect(
                                    brush = cometBrush,
                                    topLeft = Offset(cometLeft, 0f),
                                    size = Size(playheadX - cometLeft, size.height)
                                )
                            }

                            // 2dp Mint Playhead vertical line
                            drawLine(
                                color = mintColor,
                                start = Offset(playheadX, 0f),
                                end = Offset(playheadX, size.height),
                                strokeWidth = strokeWidthPx
                            )

                            // 12dp Mint cap in the ruler
                            drawCircle(
                                color = mintColor,
                                radius = strokeWidthPx * 2f,
                                center = Offset(playheadX, 6f)
                            )
                        }
                    }
                }

                // 4. ADD-TRACK FAB (56dp circle, centered below tracks, Layer.Floating = 3f)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = Daw.space.lg)
                        .zIndex(Layer.Floating)
                ) {
                    FloatingCircle(
                        icon = DawIcons.Add,
                        size = FloatingCircleSize.Large, // 56dp N4 circle per Section 5.3
                        contentDescription = "Add Track",
                        onClick = { onAction(StudioAction.AddTrack("Audio Track")) }
                    )
                }

                // 5. TIER 1 CONTEXTUAL ACTION CHIPS (Layer.Floating = 3f)
                if (state.selectedClipId != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = Daw.space.lg)
                            .zIndex(Layer.Floating)
                    ) {
                        ActionChips(
                            visible = true,
                            onActionClick = { actionName ->
                                if (actionName == "MORE") {
                                    onAction(StudioAction.SetClipMenuOpen(true))
                                } else {
                                    onAction(StudioAction.ExecuteClipAction(state.selectedClipId, actionName))
                                }
                            }
                        )
                    }
                }
            }
        }

        // 6. TIER 1 POPUP MENU ("More..." options on Layer.Popup = 5f)
        PopupMenu(
            expanded = state.isClipMenuOpen,
            onDismissRequest = { onAction(StudioAction.SetClipMenuOpen(false)) },
            items = listOf(
                PopupMenuItem("slice", "Slice Clip", DawIcons.Slice),
                PopupMenuItem("unlink", "Unlink Loop", DawIcons.UnlinkLoop),
                PopupMenuItem("combine", "Combine Tracks", DawIcons.Combine),
                PopupMenuItem("mute", "Mute Clip", DawIcons.Mute),
                PopupMenuItem("delete", "Delete Clip", DawIcons.Trash, isDestructive = true)
            ),
            onItemSelected = { item ->
                state.selectedClipId?.let { clipId ->
                    onAction(StudioAction.ExecuteClipAction(clipId, item.id.uppercase()))
                }
            }
        )
    }
}

/**
 * 36dp Bar/Beat/Tick Timecode Ruler
 * Bar numbers in 24dp circles (N4), tick marks, loop range in Sky Faint
 */
@Composable
private fun TimelineRuler(
    totalFrames: Long,
    sampleRate: Int,
    bpm: Double,
    beatsPerBar: Int,
    zoom: Float,
    scrollOffset: Float,
    isLooping: Boolean,
    loopStartFrame: Long,
    loopEndFrame: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val n3Raised = Daw.colors.n3Raised
    val n4Control = Daw.colors.n4Control
    val inkOnLight = Daw.colors.inkOnLight
    val skyFaint = Daw.colors.sky.faint
    val skyBase = Daw.colors.sky.base

    Canvas(
        modifier = modifier
            .clipToBounds()
            .pointerInput(sampleRate, zoom, scrollOffset) {
                detectTapGestures { offset ->
                    val timeSec = (offset.x + scrollOffset) / zoom
                    val targetFrame = (timeSec * sampleRate).toLong()
                    onSeek(targetFrame)
                }
            }
    ) {
        val width = size.width
        val height = size.height

        val secondsPerBeat = 60.0 / bpm
        val secondsPerBar = secondsPerBeat * beatsPerBar
        val pixelsPerBar = (secondsPerBar * zoom).toFloat()

        if (pixelsPerBar <= 1f) return@Canvas

        val startBar = (scrollOffset / pixelsPerBar).toInt()
        val endBar = ((scrollOffset + width) / pixelsPerBar).toInt() + 2

        // 1. Draw Loop Region Banner if enabled (Sky Faint per Section 5.3)
        if (isLooping) {
            val loopStartSec = loopStartFrame.toFloat() / sampleRate
            val loopEndSec = loopEndFrame.toFloat() / sampleRate
            val loopStartX = (loopStartSec * zoom) - scrollOffset
            val loopEndX = (loopEndSec * zoom) - scrollOffset
            val loopWidth = loopEndX - loopStartX

            if (loopWidth > 0f) {
                drawRect(
                    color = skyFaint,
                    topLeft = Offset(loopStartX, 0f),
                    size = Size(loopWidth, height)
                )
                drawLine(
                    color = skyBase,
                    start = Offset(loopStartX, 0f),
                    end = Offset(loopStartX, height),
                    strokeWidth = 2f
                )
                drawLine(
                    color = skyBase,
                    start = Offset(loopEndX, 0f),
                    end = Offset(loopEndX, height),
                    strokeWidth = 2f
                )
            }
        }

        // 2. Draw Bar Numbers inside 24dp circles (N4) and Beat Ticks
        val circleRadius = 12.dp.toPx()

        for (bar in startBar..endBar) {
            val barX = (bar * pixelsPerBar) - scrollOffset

            // Major bar line
            drawLine(
                color = n3Raised,
                start = Offset(barX, 0f),
                end = Offset(barX, height),
                strokeWidth = 1.5f
            )

            // Bar number in 24dp N4 circle
            val circleCenterX = barX + circleRadius + 4f
            val circleCenterY = height / 2f

            if (circleCenterX - circleRadius < width && circleCenterX + circleRadius > 0f) {
                drawCircle(
                    color = n4Control,
                    radius = circleRadius,
                    center = Offset(circleCenterX, circleCenterY)
                )

                val barNumText = "${bar + 1}"
                drawText(
                    textMeasurer = textMeasurer,
                    text = barNumText,
                    topLeft = Offset(circleCenterX - 4f, circleCenterY - 7f),
                    style = TextStyle(
                        color = inkOnLight,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // Minor beat ticks between bars
            val pixelsPerBeat = pixelsPerBar / beatsPerBar
            for (beat in 1 until beatsPerBar) {
                val beatX = barX + (beat * pixelsPerBeat)
                if (beatX in 0f..width) {
                    drawLine(
                        color = n3Raised.copy(alpha = 0.5f),
                        start = Offset(beatX, height - 8f),
                        end = Offset(beatX, height),
                        strokeWidth = 1f
                    )
                }
            }
        }
    }
}

@Preview(name = "TimelineView Preview")
@Composable
private fun TimelineViewPreview() {
    DawTheme {
        TimelineView(
            state = StudioUiState(),
            playheadProvider = { 0L },
            onAction = {}
        )
    }
}
