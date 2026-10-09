package com.android.daw.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalDawColors = staticCompositionLocalOf { Palette.defaultDawColors }
val LocalDawSpace = staticCompositionLocalOf { Space.defaultDawSpace }
val LocalDawRadii = staticCompositionLocalOf { Radii.defaultDawRadii }
val LocalDawType = staticCompositionLocalOf { Type.defaultDawType }
val LocalDawMotion = staticCompositionLocalOf { Motion.defaultDawMotion }

/**
 * Global DAW Theme Accessor
 *
 * One source of truth for all visual values:
 * Daw.colors -> Palette primaries, neutrals, inks
 * Daw.space  -> 4dp grid and component sizing tokens
 * Daw.radii  -> Surface corner shapes
 * Daw.type   -> 5-size type system
 * Daw.motion -> Timing and physics springs
 * Daw.layer  -> Z-index elevation index
 */
object Daw {
    val colors: DawColors
        @Composable
        @ReadOnlyComposable
        get() = LocalDawColors.current

    val space: DawSpace
        @Composable
        @ReadOnlyComposable
        get() = LocalDawSpace.current

    val radii: DawRadii
        @Composable
        @ReadOnlyComposable
        get() = LocalDawRadii.current

    val type: DawType
        @Composable
        @ReadOnlyComposable
        get() = LocalDawType.current

    val motion: DawMotion
        @Composable
        @ReadOnlyComposable
        get() = LocalDawMotion.current

    val layer: Layer
        get() = Layer
}

@Composable
fun DawTheme(
    colors: DawColors = Palette.defaultDawColors,
    space: DawSpace = Space.defaultDawSpace,
    radii: DawRadii = Radii.defaultDawRadii,
    type: DawType = Type.defaultDawType,
    motion: DawMotion = Motion.defaultDawMotion,
    content: @Composable () -> Unit
) {
    val materialColorScheme = darkColorScheme(
        primary = colors.mint.base,
        onPrimary = colors.inkOnLight,
        primaryContainer = colors.mint.dim,
        onPrimaryContainer = colors.inkOnDark,

        secondary = colors.coral.base,
        onSecondary = colors.inkOnLight,
        secondaryContainer = colors.coral.dim,
        onSecondaryContainer = colors.inkOnDark,

        tertiary = colors.sky.base,
        onTertiary = colors.inkOnLight,
        tertiaryContainer = colors.sky.dim,
        onTertiaryContainer = colors.inkOnDark,

        background = colors.n0Workspace,
        onBackground = colors.inkOnDark,

        surface = colors.n2Surface,
        onSurface = colors.inkOnDark,

        surfaceVariant = colors.n3Raised,
        onSurfaceVariant = colors.inkMuted,

        outline = colors.n3Raised,
        outlineVariant = colors.n1Grid,

        error = colors.coral.base,
        onError = colors.inkOnLight
    )

    CompositionLocalProvider(
        LocalDawColors provides colors,
        LocalDawSpace provides space,
        LocalDawRadii provides radii,
        LocalDawType provides type,
        LocalDawMotion provides motion
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = DawTypography,
            content = content
        )
    }
}

/**
 * Backward-compatible entry point for DawStudioTheme
 */
@Composable
fun DawStudioTheme(
    content: @Composable () -> Unit
) {
    DawTheme(content = content)
}

