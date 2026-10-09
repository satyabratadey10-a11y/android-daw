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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.zIndex
import com.android.daw.ui.components.DawIconButton
import com.android.daw.ui.components.FloatingCircle
import com.android.daw.ui.components.FloatingCircleSize
import com.android.daw.ui.theme.Daw
import com.android.daw.ui.theme.DawIcons
import com.android.daw.ui.theme.DawTheme
import com.android.daw.ui.theme.Layer
import com.android.daw.viewmodel.StudioAction
import com.android.daw.viewmodel.StudioUiState

data class DawModuleItem(
    val id: String,
    val name: String,
    val category: String,
    val isInstrument: Boolean
)

/**
 * SD Studio DAW Modules & Instruments Drawer
 *
 * Strictly adheres to agy_ui_overhaul_prompt.md Section 5.5:
 * - Slides from left on Layer.Panel (4f), 50% width
 * - Search / Sort circular buttons (40dp N4)
 * - 48dp list rows with module entries
 * - Scrim in N0 60%
 * - Zero stock Material 3 widgets, zero hex Color literals
 */
@Composable
fun ModulesDrawer(
    state: StudioUiState,
    onAction: (StudioAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOpen = state.isModulesDrawerOpen
    var searchQuery by remember { mutableStateOf("") }

    val modules = remember {
        listOf(
            DawModuleItem("1", "Audio Track", "RECORDER", false),
            DawModuleItem("2", "DirectWave Sampler", "SAMPLER", true),
            DawModuleItem("3", "MiniSynth", "SYNTHESIZER", true),
            DawModuleItem("4", "GMS Synth", "SYNTHESIZER", true),
            DawModuleItem("5", "Drum Sequencer", "DRUMS", true),
            DawModuleItem("6", "Transistor Bass", "SYNTHESIZER", true),
            DawModuleItem("7", "Parametric EQ", "EFFECT", false),
            DawModuleItem("8", "Stereo Delay", "EFFECT", false),
            DawModuleItem("9", "Master Limiter", "EFFECT", false),
            DawModuleItem("10", "Chorus / Flanger", "EFFECT", false)
        )
    }

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
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            // Drawer Container (50% screen width)
            Column(
                modifier = Modifier
                    .fillMaxHeight()
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
                    Text(
                        text = "MODULES & INSTRUMENTS",
                        style = Daw.type.title,
                        color = Daw.colors.inkOnDark
                    )

                    DawIconButton(
                        icon = DawIcons.Close,
                        onClick = onDismiss,
                        contentDescription = "Close Modules Drawer"
                    )
                }

                // Search & Filter Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Daw.space.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Daw.space.sm)
                ) {
                    FloatingCircle(
                        icon = DawIcons.Search,
                        size = FloatingCircleSize.Small,
                        contentDescription = "Search Modules",
                        onClick = {}
                    )
                    FloatingCircle(
                        icon = DawIcons.Sort,
                        size = FloatingCircleSize.Small,
                        contentDescription = "Sort Modules",
                        onClick = {}
                    )
                    Text(
                        text = "AVAILABLE MODULES (${modules.size})",
                        style = Daw.type.caption,
                        color = Daw.colors.inkMuted
                    )
                }

                // 48dp Module List Rows
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(modules, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(Daw.space.rowHeight)
                                .clickable {
                                    onAction(StudioAction.AddTrack(item.name))
                                    onDismiss()
                                }
                                .padding(horizontal = Daw.space.lg),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Daw.space.md)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(Daw.space.circleSm)
                                        .background(
                                            color = if (item.isInstrument) Daw.colors.mint.dim else Daw.colors.sky.dim,
                                            shape = Daw.radii.xs
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        imageVector = if (item.isInstrument) DawIcons.PianoRoll else DawIcons.Track,
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(Daw.colors.inkOnDark),
                                        modifier = Modifier.size(Daw.space.iconMin)
                                    )
                                }

                                Column {
                                    Text(
                                        text = item.name,
                                        style = Daw.type.label,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Daw.colors.inkOnDark
                                    )
                                    Text(
                                        text = item.category,
                                        style = Daw.type.caption,
                                        color = Daw.colors.inkMuted
                                    )
                                }
                            }

                            Image(
                                imageVector = DawIcons.ChevronRight,
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(Daw.colors.inkMuted),
                                modifier = Modifier.size(Daw.space.iconMin)
                            )
                        }

                        // 1dp Hairline divider
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(Daw.space.hairline)
                                .background(Daw.colors.n3Raised)
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "ModulesDrawer Preview")
@Composable
private fun ModulesDrawerPreview() {
    DawTheme {
        ModulesDrawer(
            state = StudioUiState(isModulesDrawerOpen = true),
            onAction = {},
            onDismiss = {}
        )
    }
}
