package com.android.daw.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import com.android.daw.ui.theme.Daw
import com.android.daw.ui.theme.DawIcons
import com.android.daw.ui.theme.DawTheme
import com.android.daw.ui.theme.Layer

enum class FloatingCircleSize {
    Small, // 40dp visual (Action chips, drawer rails)
    Large  // 56dp visual (Add FAB, primary transport triggers)
}

/**
 * SD Studio Floating Circle Button
 *
 * Strictly adheres to agy_ui_overhaul_prompt.md:
 * - N4 Control surface (#C8CDDA)
 * - Layer.Floating zIndex (3f)
 * - InkOnLight icon tint (#363D44)
 * - Guaranteed >= 48dp hit area (even on 40dp visual variant)
 * - Max one soft shadow style
 */
@Composable
fun FloatingCircle(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    size: FloatingCircleSize = FloatingCircleSize.Large,
    contentDescription: String? = null,
    isEnabled: Boolean = true,
    tint: Color = Daw.colors.inkOnLight,
    surfaceColor: Color = Daw.colors.n4Control,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: (@Composable () -> Unit)? = null
) {
    val visualSize: Dp = when (size) {
        FloatingCircleSize.Small -> Daw.space.circleSm
        FloatingCircleSize.Large -> Daw.space.circleLg
    }

    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = Daw.motion.playfulSpring,
        label = "floating_circle_scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isEnabled) 1f else 0.38f,
        animationSpec = Daw.motion.snappySpring,
        label = "floating_circle_alpha"
    )

    // Outer container ensures minimum 48dp touch target on screen
    Box(
        modifier = modifier
            .zIndex(Layer.Floating)
            .defaultMinSize(
                minWidth = Daw.space.touchTargetMin,
                minHeight = Daw.space.touchTargetMin
            )
            .graphicsLayer {
                this.scaleX = scale
                this.scaleY = scale
                this.alpha = alpha
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = isEnabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Visual surface circle with soft shadow
        Box(
            modifier = Modifier
                .size(visualSize)
                .shadow(
                    elevation = Daw.space.xs,
                    shape = Daw.radii.full,
                    clip = false,
                    ambientColor = Daw.colors.n0Workspace,
                    spotColor = Daw.colors.n0Workspace
                )
                .background(
                    color = surfaceColor,
                    shape = Daw.radii.full
                ),
            contentAlignment = Alignment.Center
        ) {
            if (content != null) {
                content()
            } else if (icon != null) {
                Image(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    colorFilter = ColorFilter.tint(tint),
                    modifier = Modifier.size(Daw.space.iconMin)
                )
            }
        }
    }
}

@Preview(name = "FloatingCircle Preview")
@Composable
private fun FloatingCirclePreview() {
    DawTheme {
        FloatingCircle(
            icon = DawIcons.Add,
            size = FloatingCircleSize.Large,
            onClick = {}
        )
    }
}
