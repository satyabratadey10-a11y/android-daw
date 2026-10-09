package com.android.daw.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.zIndex
import com.android.daw.ui.theme.Daw
import com.android.daw.ui.theme.DawIcons
import com.android.daw.ui.theme.DawTheme
import com.android.daw.ui.theme.Layer

data class PopupMenuItem(
    val id: String,
    val title: String,
    val icon: ImageVector? = null,
    val isSelected: Boolean = false,
    val isEnabled: Boolean = true,
    val isDestructive: Boolean = false
)

/**
 * SD Studio DAW Popup Menu
 *
 * Strictly adheres to agy_ui_overhaul_prompt.md:
 * - N4 Control surface (#C8CDDA)
 * - md radius (12dp)
 * - Items 48dp tall with 16dp horizontal text padding
 * - Layer.Popup zIndex (5f)
 * - Clear active, disabled (38% alpha), and destructive states
 * - Zero stock Material 3 widgets
 */
@Composable
fun PopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<PopupMenuItem>,
    onItemSelected: (PopupMenuItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!expanded) return

    // Dismiss overlay on Layer.Popup
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(Layer.Popup)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismissRequest
            )
    ) {
        // Menu Surface Card
        Column(
            modifier = modifier
                .align(Alignment.Center)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // Consume tap to prevent dismissal
                )
                .widthIn(min = Daw.space.dial)
                .shadow(
                    elevation = Daw.space.sm,
                    shape = Daw.radii.md,
                    ambientColor = Daw.colors.n0Workspace,
                    spotColor = Daw.colors.n0Workspace
                )
                .background(
                    color = Daw.colors.n4Control,
                    shape = Daw.radii.md
                )
                .padding(vertical = Daw.space.xs)
        ) {
            items.forEach { item ->
                PopupMenuItemRow(
                    item = item,
                    onClick = {
                        if (item.isEnabled) {
                            onItemSelected(item)
                            onDismissRequest()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PopupMenuItemRow(
    item: PopupMenuItem,
    onClick: () -> Unit
) {
    val itemAlpha = if (item.isEnabled) 1f else 0.38f
    val textColor: Color = when {
        item.isDestructive -> Daw.colors.coral.base
        item.isSelected -> Daw.colors.mint.base
        else -> Daw.colors.inkOnLight
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Daw.space.touchTargetMin)
            .graphicsLayer { alpha = itemAlpha }
            .clickable(
                enabled = item.isEnabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = Daw.space.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.icon != null) {
            Image(
                imageVector = item.icon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(textColor),
                modifier = Modifier.size(Daw.space.iconMin)
            )
            Spacer(modifier = Modifier.width(Daw.space.md))
        }

        Text(
            text = item.title,
            style = Daw.type.label,
            color = textColor,
            modifier = Modifier.weight(1f)
        )

        if (item.isSelected) {
            Image(
                imageVector = DawIcons.Check,
                contentDescription = null,
                colorFilter = ColorFilter.tint(Daw.colors.mint.base),
                modifier = Modifier.size(Daw.space.iconMin)
            )
        }
    }
}

@Preview(name = "PopupMenu Preview")
@Composable
private fun PopupMenuPreview() {
    DawTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            PopupMenu(
                expanded = true,
                onDismissRequest = {},
                items = listOf(
                    PopupMenuItem("1", "Slice", DawIcons.Track),
                    PopupMenuItem("2", "Unlink Loop", DawIcons.Loop),
                    PopupMenuItem("3", "Mute", isSelected = true),
                    PopupMenuItem("4", "Delete", DawIcons.Trash, isDestructive = true)
                ),
                onItemSelected = {}
            )
        }
    }
}
