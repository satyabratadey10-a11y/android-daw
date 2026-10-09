package com.android.daw.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.android.daw.ui.theme.Daw
import com.android.daw.ui.theme.DawIcons
import com.android.daw.ui.theme.DawTheme

/**
 * SD Studio Core Icon Button
 *
 * Built strictly on androidx.compose.foundation + Canvas graphics.
 * - Guaranteed min 48dp touch target (Daw.space.touchTargetMin)
 * - Tactile pressed/hovered state animations
 * - Transport-grade active indicator support (Mint underline bar)
 * - 38% alpha disabled state discipline
 * - Zero stock Material 3 widgets
 */
@Composable
fun DawIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    isActive: Boolean = false,
    isEnabled: Boolean = true,
    showActiveIndicator: Boolean = false,
    tint: Color = Daw.colors.inkOnDark,
    activeTint: Color = Daw.colors.mint.base,
    indicatorColor: Color = Daw.colors.mint.base,
    size: Dp = Daw.space.touchTargetMin,
    iconSize: Dp = Daw.space.iconMin,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    val pressedAlpha by animateFloatAsState(
        targetValue = when {
            !isEnabled -> 0.38f
            isPressed -> 0.75f
            isHovered -> 0.9f
            else -> 1f
        },
        animationSpec = Daw.motion.snappySpring,
        label = "daw_icon_button_alpha"
    )

    val currentTint = when {
        isActive -> activeTint
        else -> tint
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { alpha = pressedAlpha }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = isEnabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Pressed/hovered tactile background pill
        if (isPressed || isHovered) {
            Box(
                modifier = Modifier
                    .size(size)
                    .background(
                        color = Daw.colors.n3Raised.copy(alpha = if (isPressed) 0.6f else 0.3f),
                        shape = Daw.radii.sm
                    )
            )
        }

        // Icon Graphic
        Image(
            imageVector = icon,
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(currentTint),
            modifier = Modifier.size(iconSize)
        )

        // 3dp active underline bar (Transport bar spec)
        if (showActiveIndicator && isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(Daw.space.indicator)
                    .background(
                        color = indicatorColor,
                        shape = Daw.radii.xs
                    )
            )
        }
    }
}

@Preview(name = "DawIconButton Preview")
@Composable
private fun DawIconButtonPreview() {
    DawTheme {
        DawIconButton(
            icon = DawIcons.Play,
            isActive = true,
            showActiveIndicator = true,
            onClick = {}
        )
    }
}
