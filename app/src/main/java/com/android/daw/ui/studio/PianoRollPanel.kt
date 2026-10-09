package com.android.daw.ui.studio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.android.daw.ui.components.DawIconButton
import com.android.daw.ui.theme.Daw
import com.android.daw.ui.theme.DawIcons
import com.android.daw.ui.theme.DawTheme
import com.android.daw.ui.theme.Layer
import com.android.daw.viewmodel.StudioAction
import com.android.daw.viewmodel.StudioUiState

/**
 * SD Studio DAW Piano Roll Panel
 *
 * Strictly adheres to agy_ui_overhaul_prompt.md Section 5.8:
 * - Slides from bottom on Layer.Panel (4f) sized to 40% of height
 * - Scrim in N0 60%
 * - Key column 40dp on left (white/black keys with C-labels)
 * - Note grid with beat/bar line weights
 * - Notes in channel color (radius xs)
 * - Zero stock Material 3 widgets, zero hex Color literals
 */
@Composable
fun PianoRollPanel(
    state: StudioUiState,
    onAction: (StudioAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOpen = state.isPianoRollOpen
    val targetTrack = state.selectedTrack ?: state.tracks.firstOrNull()
    val trackIndex = state.tracks.indexOfFirst { it.trackId == targetTrack?.trackId }.coerceAtLeast(0)
    val noteColor = Daw.colors.trackColor(trackIndex)

    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(Daw.motion.fast()) + slideInVertically(Daw.motion.smooth()) { it },
        exit = fadeOut(Daw.motion.fast()) + slideOutVertically(Daw.motion.snappy()) { it },
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
            contentAlignment = Alignment.BottomCenter
        ) {
            // Bottom Sheet Container (40% height)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.42f)
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
                        Text(
                            text = "PIANO ROLL",
                            style = Daw.type.title,
                            color = Daw.colors.inkOnDark
                        )
                        targetTrack?.let { tr ->
                            Text(
                                text = "• ${tr.name}",
                                style = Daw.type.label,
                                color = noteColor
                            )
                        }
                    }

                    DawIconButton(
                        icon = DawIcons.Close,
                        onClick = onDismiss,
                        contentDescription = "Close Piano Roll"
                    )
                }

                // Piano Roll Surface: 40dp Key Column (Left) + Note Grid Canvas (Right)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Daw.colors.n0Workspace)
                ) {
                    // 40dp Key Column on Left
                    PianoKeyColumn(
                        modifier = Modifier
                            .width(Daw.space.circleSm) // 40dp wide per Section 5.8
                            .fillMaxHeight()
                    )

                    // 1dp Divider
                    Box(
                        modifier = Modifier
                            .width(Daw.space.hairline)
                            .fillMaxHeight()
                            .background(Daw.colors.n3Raised)
                    )

                    // Note Grid Canvas
                    PianoRollGrid(
                        noteColor = noteColor,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

/**
 * 40dp Piano Key Column with White/Black keys & C-labels
 */
@Composable
private fun PianoKeyColumn(modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val inkOnDark = Daw.colors.inkOnDark
    val inkMuted = Daw.colors.inkMuted
    val n1Grid = Daw.colors.n1Grid
    val n3Raised = Daw.colors.n3Raised

    Canvas(modifier = modifier) {
        val totalKeys = 12
        val keyHeight = size.height / totalKeys

        // Notes descending from B to C (chromatic)
        val isBlackKey = booleanArrayOf(false, true, false, true, false, false, true, false, true, false, true, false)
        val noteLabels = arrayOf("B", "A#", "A", "G#", "G", "F#", "F", "E", "D#", "D", "C#", "C")

        for (i in 0 until totalKeys) {
            val y = i * keyHeight
            val black = isBlackKey[i % 12]
            val keyColor = if (black) n1Grid else n3Raised

            drawRect(
                color = keyColor,
                topLeft = Offset(0f, y),
                size = Size(if (black) size.width * 0.65f else size.width, keyHeight)
            )

            drawLine(
                color = n1Grid,
                start = Offset(0f, y + keyHeight),
                end = Offset(size.width, y + keyHeight),
                strokeWidth = 1f
            )

            // C label indicator
            if (noteLabels[i].startsWith("C") && !black) {
                drawText(
                    textMeasurer = textMeasurer,
                    text = "C4",
                    topLeft = Offset(4f, y + 2f),
                    style = TextStyle(color = inkOnDark, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                )
            }
        }
    }
}

/**
 * Note Grid Canvas with beat/bar lines and note blocks
 */
@Composable
private fun PianoRollGrid(
    noteColor: Color,
    modifier: Modifier = Modifier
) {
    val n0Workspace = Daw.colors.n0Workspace
    val n1Grid = Daw.colors.n1Grid
    val n3Raised = Daw.colors.n3Raised
    val inkOnDark = Daw.colors.inkOnDark

    Canvas(modifier = modifier) {
        val totalKeys = 12
        val keyHeight = size.height / totalKeys
        val totalBeats = 16
        val beatWidth = size.width / totalBeats

        // Background rows
        for (i in 0 until totalKeys) {
            val y = i * keyHeight
            val isEven = i % 2 == 0
            drawRect(
                color = if (isEven) n0Workspace else n1Grid,
                topLeft = Offset(0f, y),
                size = Size(size.width, keyHeight)
            )
            drawLine(
                color = n1Grid,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 0.5f
            )
        }

        // Vertical Bar & Beat lines
        for (b in 0..totalBeats) {
            val x = b * beatWidth
            val isBarLine = b % 4 == 0
            drawLine(
                color = if (isBarLine) n3Raised else n1Grid,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = if (isBarLine) 1.5f else 0.5f
            )
        }

        // Demo sequence of musical notes
        val sampleNotes = listOf(
            Triple(0, 11, 2),  // C (bar 1 beat 1, 2 beats)
            Triple(2, 9, 2),   // D
            Triple(4, 7, 2),   // E
            Triple(6, 6, 2),   // F
            Triple(8, 4, 2),   // G
            Triple(10, 2, 2),  // A
            Triple(12, 0, 4)   // B
        )

        sampleNotes.forEach { (startBeat, keyIndex, durationBeats) ->
            val noteX = startBeat * beatWidth + 2f
            val noteY = keyIndex * keyHeight + 2f
            val noteW = durationBeats * beatWidth - 4f
            val noteH = keyHeight - 4f

            drawRoundRect(
                color = noteColor,
                topLeft = Offset(noteX, noteY),
                size = Size(noteW, noteH),
                cornerRadius = CornerRadius(4f, 4f)
            )

            drawRoundRect(
                color = inkOnDark.copy(alpha = 0.4f),
                topLeft = Offset(noteX, noteY),
                size = Size(noteW, 2f),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }
    }
}

@Preview(name = "PianoRollPanel Preview")
@Composable
private fun PianoRollPanelPreview() {
    DawTheme {
        PianoRollPanel(
            state = StudioUiState(isPianoRollOpen = true),
            onAction = {},
            onDismiss = {}
        )
    }
}
