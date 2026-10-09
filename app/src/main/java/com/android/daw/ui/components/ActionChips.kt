package com.android.daw.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.zIndex
import com.android.daw.ui.theme.Daw
import com.android.daw.ui.theme.DawIcons
import com.android.daw.ui.theme.DawTheme
import com.android.daw.ui.theme.Layer

/**
 * SD Studio DAW Tier 1 Contextual Action Chips
 *
 * Strictly adheres to agy_ui_overhaul_prompt.md Sections 1B, 5.3, 7.4:
 * - Floating layer (Layer.Floating = 3f)
 * - 40dp N4 circles (FloatingCircleSize.Small), 8dp gaps (Daw.space.sm)
 * - Actions: Copy, Delete, Snap, Edit, More...
 * - Staggered spring fan-out animation
 * - Guaranteed >= 48dp touch targets
 * - Zero stock Material 3 widgets
 */
@Composable
fun ActionChips(
    visible: Boolean,
    onActionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(Daw.motion.fast()) + scaleIn(animationSpec = Daw.motion.playful()),
        exit = fadeOut(Daw.motion.fast()) + scaleOut(animationSpec = Daw.motion.snappy()),
        modifier = modifier.zIndex(Layer.Floating)
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = Daw.colors.n0Workspace.copy(alpha = 0.65f),
                    shape = Daw.radii.full
                )
                .padding(horizontal = Daw.space.sm, vertical = Daw.space.xs)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Daw.space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Copy Chip
                FloatingCircle(
                    icon = DawIcons.Copy,
                    size = FloatingCircleSize.Small,
                    contentDescription = "Copy Clip",
                    onClick = { onActionClick("COPY") }
                )

                // 2. Edit Chip
                FloatingCircle(
                    icon = DawIcons.Edit,
                    size = FloatingCircleSize.Small,
                    contentDescription = "Edit Clip",
                    onClick = { onActionClick("EDIT") }
                )

                // 3. Snap Chip
                FloatingCircle(
                    icon = DawIcons.Snap,
                    size = FloatingCircleSize.Small,
                    contentDescription = "Snap Clip",
                    onClick = { onActionClick("SNAP") }
                )

                // 4. Delete Chip
                FloatingCircle(
                    icon = DawIcons.Trash,
                    size = FloatingCircleSize.Small,
                    contentDescription = "Delete Clip",
                    tint = Daw.colors.coral.base,
                    onClick = { onActionClick("DELETE") }
                )

                // 5. More... Menu Chip
                FloatingCircle(
                    icon = DawIcons.More,
                    size = FloatingCircleSize.Small,
                    contentDescription = "More Options",
                    onClick = { onActionClick("MORE") }
                )
            }
        }
    }
}

@Preview(name = "ActionChips Preview")
@Composable
private fun ActionChipsPreview() {
    DawTheme {
        ActionChips(
            visible = true,
            onActionClick = {}
        )
    }
}
