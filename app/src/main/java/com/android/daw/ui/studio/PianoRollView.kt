package com.android.daw.ui.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.android.daw.ui.components.DawIconButton
import com.android.daw.ui.components.FloatingCircle
import com.android.daw.ui.components.FloatingCircleSize
import com.android.daw.ui.theme.Daw
import com.android.daw.ui.theme.DawIcons
import com.android.daw.ui.theme.Layer
import com.android.daw.viewmodel.AudioClipUiModel
import com.android.daw.viewmodel.MidiNote
import com.android.daw.viewmodel.PianoRollSnap
import com.android.daw.viewmodel.PianoRollTool
import com.android.daw.viewmodel.StudioAction
import com.android.daw.viewmodel.StudioUiState
import com.android.daw.viewmodel.TrackInstrumentType
import com.android.daw.viewmodel.TrackUiModel
import kotlin.math.max
import kotlin.math.min

/**
 * SD Studio DAW Piano Roll View
 *
 * Full-screen replacement of the timeline area adhering strictly to sprint specification:
 * - Full replacement of timeline area (transport remains visible at bottom)
 * - 48dp header: title + clip name, tool toggle (Draw / Select / Erase), Snap selector, Undo/Redo, Velocity-lane toggle, Close 'X'
 * - 40dp key column: labelled at every C (C2..C6), black-key shading, tapping audits note via native DSP
 * - Top bar ruler: bar-number bubbles centered on bar lines
 * - Note editing: tap/drag to draw, drag body to move with snap, drag right edge to resize, tap to erase in Erase mode
 * - Velocity lane: toggleable 48dp lane with draggable velocity stems
 * - Audible during playback & editing: zero-block latency JNI dispatch
 */
@Composable
fun PianoRollView(
    state: StudioUiState,
    playheadProvider: () -> Long,
    onAction: (StudioAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val targetTrack = state.selectedTrack ?: state.tracks.firstOrNull { it.instrumentType != TrackInstrumentType.AUDIO } ?: state.tracks.firstOrNull()
    val trackIndex = state.tracks.indexOfFirst { it.trackId == targetTrack?.trackId }.coerceAtLeast(0)
    val targetClip = targetTrack?.clips?.find { it.clipId == state.pianoRollClipId }
        ?: targetTrack?.clips?.firstOrNull { it.isMidi }
        ?: targetTrack?.clips?.firstOrNull()

    val sampleRate = state.sampleRate
    val zoom = state.zoomPixelsPerSecond
    val scrollOffset = state.horizontalScrollOffsetPx
    val bpm = state.bpm
    val snap = state.pianoRollSnap
    val tool = state.pianoRollTool

    // Pitch Range: C2 (36) to C6 (84) = 48 semitones
    val minPitch = 36
    val maxPitch = 84
    val totalKeys = maxPitch - minPitch + 1

    val trackColor = Daw.colors.trackColor(trackIndex)
    val density = LocalDensity.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Daw.colors.n0Workspace)
    ) {
        // =========================================================================
        // 1. 48dp PIANO ROLL HEADER
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Daw.space.rowHeight)
                .background(Daw.colors.n2Surface)
                .padding(horizontal = Daw.space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Close X and Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Daw.space.sm)
            ) {
                DawIconButton(
                    icon = DawIcons.Close,
                    contentDescription = "Close Piano Roll",
                    onClick = { onAction(StudioAction.ClosePianoRoll) }
                )

                Column {
                    Text(
                        text = "Piano Roll • ${targetClip?.name ?: "Pattern"}",
                        style = Daw.type.label,
                        fontWeight = FontWeight.Bold,
                        color = Daw.colors.inkOnDark
                    )
                    Text(
                        text = targetTrack?.name ?: "Instrument Track",
                        style = Daw.type.caption,
                        color = trackColor
                    )
                }
            }

            // Center: Tools (Draw / Select / Erase) & Snap Selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Daw.space.xs)
            ) {
                // Tool: DRAW
                ToolChip(
                    label = "DRAW",
                    icon = DawIcons.Edit,
                    isSelected = tool == PianoRollTool.DRAW,
                    onClick = { onAction(StudioAction.SetPianoRollTool(PianoRollTool.DRAW)) }
                )

                // Tool: SELECT
                ToolChip(
                    label = "SELECT",
                    icon = DawIcons.Check,
                    isSelected = tool == PianoRollTool.SELECT,
                    onClick = { onAction(StudioAction.SetPianoRollTool(PianoRollTool.SELECT)) }
                )

                // Tool: ERASE
                ToolChip(
                    label = "ERASE",
                    icon = DawIcons.Trash,
                    isSelected = tool == PianoRollTool.ERASE,
                    onClick = { onAction(StudioAction.SetPianoRollTool(PianoRollTool.ERASE)) }
                )

                Spacer(modifier = Modifier.width(Daw.space.sm))

                // Snap Selector Chips
                PianoRollSnap.values().forEach { snapOpt ->
                    val isSnapSelected = snap == snapOpt
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .background(
                                color = if (isSnapSelected) Daw.colors.mint.base else Daw.colors.n3Raised,
                                shape = Daw.radii.xs
                            )
                            .clickable { onAction(StudioAction.SetPianoRollSnap(snapOpt)) }
                            .padding(horizontal = Daw.space.xs + 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = snapOpt.label,
                            style = Daw.type.caption,
                            fontWeight = if (isSnapSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSnapSelected) Daw.colors.inkOnLight else Daw.colors.inkOnDark
                        )
                    }
                }
            }

            // Right: Velocity Lane Toggle & Undo / Redo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Daw.space.xs)
            ) {
                // Velocity Toggle
                DawIconButton(
                    icon = DawIcons.Vol,
                    isActive = state.isVelocityLaneVisible,
                    showActiveIndicator = true,
                    contentDescription = "Toggle Velocity Lane",
                    onClick = { onAction(StudioAction.ToggleVelocityLane) }
                )

                // Undo
                DawIconButton(
                    icon = DawIcons.Back,
                    contentDescription = "Undo",
                    onClick = { onAction(StudioAction.Undo) }
                )

                // Redo
                DawIconButton(
                    icon = DawIcons.Forward,
                    contentDescription = "Redo",
                    onClick = { onAction(StudioAction.Redo) }
                )
            }
        }

        // 1dp Divider Line below header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Daw.space.hairline)
                .background(Daw.colors.n3Raised)
        )

        // =========================================================================
        // 2. PIANO ROLL BODY: 40dp Key Column + Note Canvas
        // =========================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar: Timecode Ruler (36dp height, bar bubbles centered on bar line)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Daw.space.rulerHeight)
                ) {
                    // Empty space above 40dp key column
                    Box(
                        modifier = Modifier
                            .width(Daw.space.circleSm) // 40dp
                            .fillMaxHeight()
                            .background(Daw.colors.n2Surface)
                    )

                    // 1dp vertical divider
                    Box(
                        modifier = Modifier
                            .width(Daw.space.hairline)
                            .fillMaxHeight()
                            .background(Daw.colors.n3Raised)
                    )

                    // Ruler Canvas
                    PianoRollRuler(
                        totalFrames = state.totalFrames,
                        sampleRate = sampleRate,
                        bpm = bpm,
                        beatsPerBar = state.beatsPerBar,
                        zoom = zoom,
                        scrollOffset = scrollOffset,
                        onSeek = { frame -> onAction(StudioAction.Seek(frame)) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }

                // Note Grid and Key Column Area
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // 40dp Key Column
                    PianoKeyColumn(
                        minPitch = minPitch,
                        maxPitch = maxPitch,
                        onNoteOn = { pitch ->
                            targetTrack?.let { tr ->
                                onAction(StudioAction.AuditionNoteOn(tr.trackId, pitch, 0.85f))
                            }
                        },
                        onNoteOff = { pitch ->
                            targetTrack?.let { tr ->
                                onAction(StudioAction.AuditionNoteOff(tr.trackId, pitch))
                            }
                        },
                        modifier = Modifier
                            .width(Daw.space.circleSm) // 40dp wide
                            .fillMaxHeight()
                    )

                    // 1dp vertical divider
                    Box(
                        modifier = Modifier
                            .width(Daw.space.hairline)
                            .fillMaxHeight()
                            .background(Daw.colors.n3Raised)
                    )

                    // Note Canvas Grid
                    NoteGridCanvas(
                        state = state,
                        targetTrack = targetTrack,
                        targetClip = targetClip,
                        trackColor = trackColor,
                        minPitch = minPitch,
                        maxPitch = maxPitch,
                        totalKeys = totalKeys,
                        zoom = zoom,
                        scrollOffset = scrollOffset,
                        playheadProvider = playheadProvider,
                        tool = tool,
                        snap = snap,
                        onAction = onAction,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }

                // =========================================================================
                // 3. VELOCITY LANE (Optional 48dp height docked at bottom)
                // =========================================================================
                if (state.isVelocityLaneVisible && targetClip != null && targetTrack != null) {
                    VelocityLane(
                        notes = targetClip.notes,
                        trackColor = trackColor,
                        sampleRate = sampleRate,
                        zoom = zoom,
                        scrollOffset = scrollOffset,
                        onUpdateVelocity = { note, newVel ->
                            onAction(
                                StudioAction.UpdateMidiNote(
                                    trackId = targetTrack.trackId,
                                    clipId = targetClip.clipId,
                                    noteId = note.id,
                                    newPitch = note.pitch,
                                    newStartFrame = note.startFrame,
                                    newDurationFrames = note.durationFrames,
                                    velocity = newVel
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Daw.space.rowHeight) // 48dp
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .background(
                color = if (isSelected) Daw.colors.mint.base else Daw.colors.n3Raised,
                shape = Daw.radii.xs
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Daw.space.xs + 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            androidx.compose.foundation.Image(
                imageVector = icon,
                contentDescription = label,
                colorFilter = ColorFilter.tint(if (isSelected) Daw.colors.inkOnLight else Daw.colors.inkOnDark),
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                style = Daw.type.caption,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Daw.colors.inkOnLight else Daw.colors.inkOnDark
            )
        }
    }
}

/**
 * 40dp Key Column with white/black key styling and labels at every C.
 * Tapping a key auditions through native audio engine.
 */
@Composable
private fun PianoKeyColumn(
    minPitch: Int,
    maxPitch: Int,
    onNoteOn: (Int) -> Unit,
    onNoteOff: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalKeys = maxPitch - minPitch + 1
    val textMeasurer = rememberTextMeasurer()
    val inkMuted = Daw.colors.inkMuted
    val inkOnDark = Daw.colors.inkOnDark
    val inkOnLight = Daw.colors.inkOnLight
    val mintBase = Daw.colors.mint.base
    val n2Surface = Daw.colors.n2Surface
    val n0Workspace = Daw.colors.n0Workspace
    val n3Raised = Daw.colors.n3Raised

    var activePitch by remember { mutableIntStateOf(-1) }

    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .pointerInput(minPitch, maxPitch) {
                detectTapGestures(
                    onPress = { offset ->
                        val keyHeight = size.height / totalKeys
                        val keyIndex = (offset.y / keyHeight).toInt().coerceIn(0, totalKeys - 1)
                        val pitch = maxPitch - keyIndex
                        activePitch = pitch
                        onNoteOn(pitch)
                        tryAwaitRelease()
                        onNoteOff(pitch)
                        activePitch = -1
                    }
                )
            }
    ) {
        val keyHeight = size.height / totalKeys

        for (i in 0 until totalKeys) {
            val pitch = maxPitch - i
            val semitone = pitch % 12
            val isBlackKey = semitone == 1 || semitone == 3 || semitone == 6 || semitone == 8 || semitone == 10
            val isC = semitone == 0
            val isPressed = pitch == activePitch

            val yTop = i * keyHeight

            val keyColor = when {
                isPressed -> mintBase
                isBlackKey -> n0Workspace
                else -> n2Surface
            }

            drawRect(
                color = keyColor,
                topLeft = Offset(0f, yTop),
                size = Size(size.width, keyHeight)
            )

            // Divider between keys
            drawLine(
                color = n3Raised.copy(alpha = 0.5f),
                start = Offset(0f, yTop + keyHeight),
                end = Offset(size.width, yTop + keyHeight),
                strokeWidth = 0.5.dp.toPx()
            )

            // Label every C (e.g. C2, C3, C4, C5, C6)
            if (isC) {
                val octave = (pitch / 12) - 1
                val label = "C$octave"
                val layout = textMeasurer.measure(
                    text = label,
                    style = TextStyle(
                        color = if (isPressed) inkOnLight else inkOnDark,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Default,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
                    )
                )
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(6.dp.toPx(), yTop + (keyHeight - layout.size.height) / 2f)
                )
            }
        }
    }
}

/**
 * Note Grid Canvas
 * Renders semitone rows, bar/beat lines, and notes.
 * Supports drawing, moving, resizing, and deleting notes with snap quantization.
 */
@Composable
private fun NoteGridCanvas(
    state: StudioUiState,
    targetTrack: TrackUiModel?,
    targetClip: AudioClipUiModel?,
    trackColor: Color,
    minPitch: Int,
    maxPitch: Int,
    totalKeys: Int,
    zoom: Float,
    scrollOffset: Float,
    playheadProvider: () -> Long,
    tool: PianoRollTool,
    snap: PianoRollSnap,
    onAction: (StudioAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val sampleRate = state.sampleRate
    val bpm = state.bpm
    val originInsetPx = TimelineLayoutMath.ORIGIN_INSET_DP * 2.5f

    val n0Workspace = Daw.colors.n0Workspace
    val n1Grid = Daw.colors.n1Grid
    val n2Surface = Daw.colors.n2Surface
    val n3Raised = Daw.colors.n3Raised
    val mintBase = Daw.colors.mint.base
    val inkOnDark = Daw.colors.inkOnDark
    val inkOnLight = Daw.colors.inkOnLight

    // Drag-move / drag-resize state
    var draggingNoteId by remember { mutableIntStateOf(-1) }
    var isResizing by remember { mutableStateOf(false) }
    var dragStartNoteStartFrame by remember { mutableLongStateOf(0L) }
    var dragStartNoteDurFrames by remember { mutableLongStateOf(0L) }
    var dragStartPitch by remember { mutableIntStateOf(60) }
    var accumulatedDragX by remember { mutableFloatStateOf(0f) }
    var accumulatedDragY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(targetClip?.clipId, tool, snap, zoom, scrollOffset) {
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
            .pointerInput(targetClip?.clipId, tool, snap, zoom, scrollOffset) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        val clip = targetClip ?: return@detectTapGestures
                        val tr = targetTrack ?: return@detectTapGestures
                        val keyHeight = size.height / totalKeys
                        val keyIndex = (tapOffset.y / keyHeight).toInt().coerceIn(0, totalKeys - 1)
                        val pitch = maxPitch - keyIndex

                        val tapTimeSec = (tapOffset.x + scrollOffset - originInsetPx).coerceAtLeast(0f) / zoom
                        val rawFrame = (tapTimeSec * sampleRate).toLong()
                        val snappedFrame = PianoRollSnapMath.quantizeFrame(rawFrame, snap, bpm, sampleRate)

                        // Check if tapped on existing note
                        val clickedNote = clip.notes.find { note ->
                            val nStartSec = note.startFrame.toFloat() / sampleRate
                            val nDurSec = note.durationFrames.toFloat() / sampleRate
                            val nX = originInsetPx + (nStartSec * zoom) - scrollOffset
                            val nW = max(6f, nDurSec * zoom)
                            val nKeyIdx = maxPitch - note.pitch
                            val nY = nKeyIdx * keyHeight
                            tapOffset.x >= nX && tapOffset.x <= (nX + nW) && tapOffset.y >= nY && tapOffset.y <= (nY + keyHeight)
                        }

                        when (tool) {
                            PianoRollTool.ERASE -> {
                                if (clickedNote != null) {
                                    onAction(StudioAction.DeleteMidiNote(tr.trackId, clip.clipId, clickedNote.id))
                                }
                            }
                            PianoRollTool.DRAW -> {
                                if (clickedNote == null) {
                                    val defDuration = PianoRollSnapMath.defaultNoteDuration(snap, bpm, sampleRate)
                                    onAction(
                                        StudioAction.AddMidiNote(
                                            trackId = tr.trackId,
                                            clipId = clip.clipId,
                                            pitch = pitch,
                                            startFrame = snappedFrame,
                                            durationFrames = defDuration,
                                            velocity = 0.85f
                                        )
                                    )
                                    // Immediate audition
                                    onAction(StudioAction.AuditionNoteOn(tr.trackId, pitch, 0.85f))
                                }
                            }
                            PianoRollTool.SELECT -> {}
                        }
                    }
                )
            }
            .pointerInput(targetClip?.clipId, tool, snap, zoom, scrollOffset) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        val clip = targetClip ?: return@detectDragGestures
                        val keyHeight = size.height / totalKeys

                        val note = clip.notes.find { n ->
                            val nStartSec = n.startFrame.toFloat() / sampleRate
                            val nDurSec = n.durationFrames.toFloat() / sampleRate
                            val nX = originInsetPx + (nStartSec * zoom) - scrollOffset
                            val nW = max(10f, nDurSec * zoom)
                            val nKeyIdx = maxPitch - n.pitch
                            val nY = nKeyIdx * keyHeight
                            startOffset.x >= (nX - 8f) && startOffset.x <= (nX + nW + 24f) &&
                                startOffset.y >= (nY - 6f) && startOffset.y <= (nY + keyHeight + 6f)
                        }

                        if (note != null) {
                            draggingNoteId = note.id
                            dragStartNoteStartFrame = note.startFrame
                            dragStartNoteDurFrames = note.durationFrames
                            dragStartPitch = note.pitch
                            accumulatedDragX = 0f
                            accumulatedDragY = 0f

                            val nStartSec = note.startFrame.toFloat() / sampleRate
                            val nDurSec = note.durationFrames.toFloat() / sampleRate
                            val nX = originInsetPx + (nStartSec * zoom) - scrollOffset
                            val nW = max(10f, nDurSec * zoom)

                            val resizeZonePx = 28.dp.toPx().coerceAtMost(nW * 0.75f).coerceAtLeast(16f)
                            isResizing = (startOffset.x >= (nX + nW - resizeZonePx))
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val clip = targetClip ?: return@detectDragGestures
                        val tr = targetTrack ?: return@detectDragGestures
                        if (draggingNoteId == -1) return@detectDragGestures

                        accumulatedDragX += dragAmount.x
                        accumulatedDragY += dragAmount.y

                        val keyHeight = size.height / totalKeys
                        val currentNote = clip.notes.find { it.id == draggingNoteId } ?: return@detectDragGestures

                        if (isResizing) {
                            val totalDeltaSec = accumulatedDragX / zoom
                            val totalDeltaFrames = (totalDeltaSec * sampleRate).toLong()
                            val rawDur = maxOf(256L, dragStartNoteDurFrames + totalDeltaFrames)
                            val finalDur = if (snap == PianoRollSnap.OFF) {
                                rawDur
                            } else {
                                PianoRollSnapMath.quantizeDuration(rawDur, snap, bpm, sampleRate)
                            }

                            if (finalDur != currentNote.durationFrames) {
                                onAction(
                                    StudioAction.UpdateMidiNote(
                                        trackId = tr.trackId,
                                        clipId = clip.clipId,
                                        noteId = currentNote.id,
                                        newPitch = currentNote.pitch,
                                        newStartFrame = currentNote.startFrame,
                                        newDurationFrames = finalDur,
                                        velocity = currentNote.velocity
                                    )
                                )
                            }
                        } else {
                            val totalDeltaSec = accumulatedDragX / zoom
                            val totalDeltaFrames = (totalDeltaSec * sampleRate).toLong()
                            val rawStart = maxOf(0L, dragStartNoteStartFrame + totalDeltaFrames)
                            val finalStart = if (snap == PianoRollSnap.OFF) {
                                rawStart
                            } else {
                                PianoRollSnapMath.quantizeFrame(rawStart, snap, bpm, sampleRate)
                            }

                            val deltaPitch = (-accumulatedDragY / keyHeight).toInt()
                            val newPitch = (dragStartPitch + deltaPitch).coerceIn(minPitch, maxPitch)

                            if (finalStart != currentNote.startFrame || newPitch != currentNote.pitch) {
                                onAction(
                                    StudioAction.UpdateMidiNote(
                                        trackId = tr.trackId,
                                        clipId = clip.clipId,
                                        noteId = currentNote.id,
                                        newPitch = newPitch,
                                        newStartFrame = finalStart,
                                        newDurationFrames = currentNote.durationFrames,
                                        velocity = currentNote.velocity
                                    )
                                )
                            }
                        }
                    },
                    onDragEnd = {
                        draggingNoteId = -1
                        isResizing = false
                        accumulatedDragX = 0f
                        accumulatedDragY = 0f
                    },
                    onDragCancel = {
                        draggingNoteId = -1
                        isResizing = false
                        accumulatedDragX = 0f
                        accumulatedDragY = 0f
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val keyHeight = size.height / totalKeys
            val canvasW = size.width
            val canvasH = size.height

            // 1. Semitone row background shading
            for (i in 0 until totalKeys) {
                val pitch = maxPitch - i
                val semitone = pitch % 12
                val isBlackKey = semitone == 1 || semitone == 3 || semitone == 6 || semitone == 8 || semitone == 10
                val yTop = i * keyHeight

                val rowBg = if (isBlackKey) n1Grid.copy(alpha = 0.5f) else n2Surface.copy(alpha = 0.25f)
                drawRect(
                    color = rowBg,
                    topLeft = Offset(0f, yTop),
                    size = Size(canvasW, keyHeight)
                )

                drawLine(
                    color = n1Grid,
                    start = Offset(0f, yTop + keyHeight),
                    end = Offset(canvasW, yTop + keyHeight),
                    strokeWidth = 0.5.dp.toPx()
                )
            }

            // 2. Bar and beat vertical grid lines
            val framesPerBeat = (60.0 / bpm) * sampleRate
            val framesPerBar = framesPerBeat * state.beatsPerBar
            val secPerBar = (framesPerBar / sampleRate).toFloat()
            val pxPerBar = secPerBar * zoom

            if (pxPerBar > 4f) {
                val totalBars = ((canvasW + scrollOffset) / pxPerBar).toInt() + 2
                for (bar in 0..totalBars) {
                    val barX = originInsetPx + (bar * pxPerBar) - scrollOffset
                    if (barX in 0f..canvasW) {
                        drawLine(
                            color = n3Raised,
                            start = Offset(barX, 0f),
                            end = Offset(barX, canvasH),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Beat sub-lines
                    val pxPerBeat = pxPerBar / state.beatsPerBar
                    for (b in 1 until state.beatsPerBar) {
                        val beatX = barX + (b * pxPerBeat)
                        if (beatX in 0f..canvasW) {
                            drawLine(
                                color = n1Grid,
                                start = Offset(beatX, 0f),
                                end = Offset(beatX, canvasH),
                                strokeWidth = 0.5.dp.toPx()
                            )
                        }
                    }
                }
            }

            // 3. Render MIDI Notes
            val currentPlayhead = playheadProvider()
            val notes = targetClip?.notes ?: emptyList()
            val noteCornerRadius = 2.dp.toPx()

            notes.forEach { note ->
                val nStartSec = note.startFrame.toFloat() / sampleRate
                val nDurSec = note.durationFrames.toFloat() / sampleRate
                val nX = originInsetPx + (nStartSec * zoom) - scrollOffset
                val nW = max(6f, nDurSec * zoom)

                val keyIdx = maxPitch - note.pitch
                val nY = keyIdx * keyHeight + 1.dp.toPx()
                val nH = keyHeight - 2.dp.toPx()

                if (nX + nW >= 0f && nX <= canvasW) {
                    val isPlayingNow = currentPlayhead >= note.startFrame && currentPlayhead <= (note.startFrame + note.durationFrames)
                    val noteFill = if (isPlayingNow) mintBase else trackColor.copy(alpha = 0.85f)

                    drawRoundRect(
                        color = noteFill,
                        topLeft = Offset(nX, nY),
                        size = Size(nW, nH),
                        cornerRadius = CornerRadius(noteCornerRadius, noteCornerRadius)
                    )

                    // Right edge resize handle indicator
                    val handleW = min(6.dp.toPx(), nW / 3f)
                    drawRoundRect(
                        color = inkOnLight.copy(alpha = 0.25f),
                        topLeft = Offset(nX + nW - handleW, nY),
                        size = Size(handleW, nH),
                        cornerRadius = CornerRadius(noteCornerRadius, noteCornerRadius)
                    )

                    // Tactile duration resize dot placed at the end of the note duration
                    val isThisNoteResizing = isResizing && draggingNoteId == note.id
                    val dotRadius = if (isThisNoteResizing) 4.5.dp.toPx() else 3.5.dp.toPx()
                    val dotCenter = Offset(
                        x = (nX + nW - 5.dp.toPx()).coerceAtLeast(nX + dotRadius),
                        y = nY + (nH / 2f)
                    )

                    if (isThisNoteResizing) {
                        drawCircle(
                            color = mintBase.copy(alpha = 0.45f),
                            radius = dotRadius * 2f,
                            center = dotCenter
                        )
                    }

                    // Outer dot rim / shadow
                    drawCircle(
                        color = n0Workspace.copy(alpha = 0.6f),
                        radius = dotRadius + 1.dp.toPx(),
                        center = dotCenter
                    )

                    // Grab dot handle
                    drawCircle(
                        color = if (isThisNoteResizing) mintBase else inkOnLight,
                        radius = dotRadius,
                        center = dotCenter
                    )
                }
            }

            // 4. Playhead Vertical Line
            val playheadX = TimelineLayoutMath.calculatePlayheadX(
                frame = currentPlayhead,
                sampleRate = sampleRate,
                zoomPixelsPerSecond = zoom,
                scrollOffsetPx = scrollOffset,
                originInsetPx = originInsetPx
            )

            if (playheadX in 0f..canvasW) {
                drawLine(
                    color = mintBase,
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, canvasH),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }
}

/**
 * 36dp Piano Roll Ruler
 */
@Composable
private fun PianoRollRuler(
    totalFrames: Long,
    sampleRate: Int,
    bpm: Double,
    beatsPerBar: Int,
    zoom: Float,
    scrollOffset: Float,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val n3Raised = Daw.colors.n3Raised
    val n4Control = Daw.colors.n4Control
    val inkOnLight = Daw.colors.inkOnLight

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(sampleRate, zoom, scrollOffset) {
                detectTapGestures { tapOffset ->
                    val originInsetPx = TimelineLayoutMath.ORIGIN_INSET_DP * 2.5f
                    val targetFrame = TimelineLayoutMath.calculateFrameFromTap(
                        tapX = tapOffset.x,
                        scrollOffsetPx = scrollOffset,
                        originInsetPx = originInsetPx,
                        zoomPixelsPerSecond = zoom,
                        sampleRate = sampleRate
                    )
                    onSeek(targetFrame)
                }
            }
    ) {
        val originInsetPx = TimelineLayoutMath.ORIGIN_INSET_DP.dp.toPx()
        val bubbleRadiusPx = TimelineLayoutMath.BUBBLE_RADIUS_DP.dp.toPx()
        val bubbleDiameterPx = TimelineLayoutMath.BUBBLE_DIAMETER_DP.dp.toPx()

        val framesPerBeat = (60.0 / bpm) * sampleRate
        val framesPerBar = framesPerBeat * beatsPerBar
        val secPerBar = (framesPerBar / sampleRate).toFloat()
        val pixelsPerBar = secPerBar * zoom

        if (pixelsPerBar > 4f) {
            val totalBars = ((size.width + scrollOffset) / pixelsPerBar).toInt() + 2
            val bubbleTopY = (size.height - bubbleDiameterPx) / 2f

            for (barIndex in 0..totalBars) {
                val barX = TimelineLayoutMath.calculateBarX(barIndex, pixelsPerBar, scrollOffset, originInsetPx)
                if (barX in 0f..size.width) {
                    drawLine(
                        color = n3Raised,
                        start = Offset(barX, size.height - 8.dp.toPx()),
                        end = Offset(barX, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                val bubbleCenterX = TimelineLayoutMath.calculateBubbleCenterX(barIndex, pixelsPerBar, scrollOffset, originInsetPx)
                val bubbleLeftX = bubbleCenterX - bubbleRadiusPx

                if (bubbleLeftX + bubbleDiameterPx >= 0f && bubbleLeftX <= size.width) {
                    drawCircle(
                        color = n4Control,
                        radius = bubbleRadiusPx,
                        center = Offset(bubbleCenterX, bubbleTopY + bubbleRadiusPx)
                    )

                    val barNumberStr = (barIndex + 1).toString()
                    val textLayout = textMeasurer.measure(
                        text = barNumberStr,
                        style = TextStyle(
                            color = inkOnLight,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Default,
                            fontWeight = FontWeight.Bold,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
                        )
                    )

                    val textX = bubbleCenterX - (textLayout.size.width / 2f)
                    val textY = (bubbleTopY + bubbleRadiusPx) - (textLayout.size.height / 2f)
                    drawText(textLayoutResult = textLayout, topLeft = Offset(textX, textY))
                }
            }
        }
    }
}

/**
 * 48dp Velocity Lane docked below the note grid.
 * Displays vertical velocity stems that can be dragged up/down.
 */
@Composable
private fun VelocityLane(
    notes: List<MidiNote>,
    trackColor: Color,
    sampleRate: Int,
    zoom: Float,
    scrollOffset: Float,
    onUpdateVelocity: (MidiNote, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val n0Workspace = Daw.colors.n0Workspace
    val n3Raised = Daw.colors.n3Raised
    val mintBase = Daw.colors.mint.base
    val originInsetPx = TimelineLayoutMath.ORIGIN_INSET_DP * 2.5f

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .background(n0Workspace)
            .pointerInput(notes, zoom, scrollOffset) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val touchX = change.position.x
                    val touchY = change.position.y
                    val newVel = (1f - (touchY / size.height)).coerceIn(0.05f, 1.0f)

                    val matchingNote = notes.minByOrNull { note ->
                        val nStartSec = note.startFrame.toFloat() / sampleRate
                        val nX = originInsetPx + (nStartSec * zoom) - scrollOffset
                        kotlin.math.abs(touchX - nX)
                    }

                    if (matchingNote != null) {
                        onUpdateVelocity(matchingNote, newVel)
                    }
                }
            }
    ) {
        // Top border divider
        drawLine(
            color = n3Raised,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx()
        )

        // Draw vertical velocity stems for each note
        notes.forEach { note ->
            val nStartSec = note.startFrame.toFloat() / sampleRate
            val nX = originInsetPx + (nStartSec * zoom) - scrollOffset
            val stemHeight = size.height * note.velocity.coerceIn(0f, 1f)
            val stemTopY = size.height - stemHeight

            if (nX in 0f..size.width) {
                drawLine(
                    color = trackColor.copy(alpha = 0.8f),
                    start = Offset(nX, size.height),
                    end = Offset(nX, stemTopY),
                    strokeWidth = 2.dp.toPx()
                )
                drawCircle(
                    color = mintBase,
                    radius = 3.dp.toPx(),
                    center = Offset(nX, stemTopY)
                )
            }
        }
    }
}
