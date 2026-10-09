package com.android.daw.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * SD Studio DAW Color System
 *
 * Strictly adheres to agy_ui_overhaul_prompt.md:
 * - Palette.kt is the ONLY file containing Color(0x...).
 * - Exactly 3 primary hues: Mint, Coral, Sky. Each with Base, Dim, Faint tonal steps.
 * - Cool slate neutrals: N0 Workspace, N1 Grid, N2 Surface, N3 Raised, N4 Control.
 * - Text/icon inks: InkOnDark, InkMuted, InkOnLight.
 */
object Palette {
    // --- 1. Primaries ---
    // Mint: Master bus, active/selected state, playhead, positive confirm
    val MintBase = Color(0xFFCDA1FF)
    val MintDim = Color(0xFF6D5C89)
    val MintFaint = Color(0xFF423D54)

    // Coral: Record, instrument channels, destructive/error
    val CoralBase = Color(0xFFBFCDF5)
    val CoralDim = Color(0xFF677085)
    val CoralFaint = Color(0xFF3F4652)

    // Sky: Drum/sampler channels, secondary info, loop/range
    val SkyBase = Color(0xFF4DB8FF)
    val SkyDim = Color(0xFF346789)
    val SkyFaint = Color(0xFF284254)

    // --- 2. Neutrals ---
    // N0 Workspace: App background, empty timeline
    val N0Workspace = Color(0xFF1F2429)

    // N1 Grid: Grid cells, track lanes (alternate with N0 for beat shading)
    val N1Grid = Color(0xFF2A3036)

    // N2 Surface: Transport bar, mixer strips, panel bodies
    val N2Surface = Color(0xFF363D44)

    // N3 Raised: Drawers, panel headers, module cards, divider lines
    val N3Raised = Color(0xFF444C55)

    // N4 Control: Floating circular buttons, popup menus
    val N4Control = Color(0xFFC8CDDA)

    // --- 3. Inks (Typography & Icons) ---
    // InkOnDark: Primary text and icons on N0–N3
    val InkOnDark = Color(0xFFE6E9F0)

    // InkMuted: Secondary labels, units, ticks
    val InkMuted = Color(0xFF8C95A0)

    // InkOnLight: Text and icons on N4 Control surfaces
    val InkOnLight = Color(0xFF363D44)

    // --- 4. System & Overlay Scrims ---
    // ScrimDark: 60% alpha of N0 Workspace for Layer.Panel and above
    val ScrimDark = Color(0x991F2429)

    // Transparent
    val Transparent = Color(0x00000000)

    // Default DAW colors instance
    val defaultDawColors = DawColors(
        mint = PrimaryTones(MintBase, MintDim, MintFaint),
        coral = PrimaryTones(CoralBase, CoralDim, CoralFaint),
        sky = PrimaryTones(SkyBase, SkyDim, SkyFaint),
        n0Workspace = N0Workspace,
        n1Grid = N1Grid,
        n2Surface = N2Surface,
        n3Raised = N3Raised,
        n4Control = N4Control,
        inkOnDark = InkOnDark,
        inkMuted = InkMuted,
        inkOnLight = InkOnLight,
        scrim = ScrimDark,
        transparent = Transparent
    )
}

@Immutable
data class PrimaryTones(
    val base: Color,
    val dim: Color,
    val faint: Color
)

@Immutable
data class DawColors(
    val mint: PrimaryTones,
    val coral: PrimaryTones,
    val sky: PrimaryTones,
    val n0Workspace: Color,
    val n1Grid: Color,
    val n2Surface: Color,
    val n3Raised: Color,
    val n4Control: Color,
    val inkOnDark: Color,
    val inkMuted: Color,
    val inkOnLight: Color,
    val scrim: Color,
    val transparent: Color
) {
    // Semantic shorthands
    val master: Color get() = mint.base
    val active: Color get() = mint.base
    val playhead: Color get() = mint.base
    val record: Color get() = coral.base
    val error: Color get() = coral.base
    val loop: Color get() = sky.base
    val background: Color get() = n0Workspace
    val surface: Color get() = n2Surface
    val raised: Color get() = n3Raised
    val control: Color get() = n4Control
    val textPrimary: Color get() = inkOnDark
    val textMuted: Color get() = inkMuted
    val textOnControl: Color get() = inkOnLight

    /**
     * Track/clip color cycling rule: Coral -> Sky -> Mint
     * Tracks beyond index 2 reuse primaries at Dim tone.
     */
    fun trackColor(trackIndex: Int, isDim: Boolean = false): Color {
        val tone = when ((trackIndex % 3 + 3) % 3) {
            0 -> coral
            1 -> sky
            else -> mint
        }
        return if (isDim || trackIndex >= 3) tone.dim else tone.base
    }

    /**
     * Ghost notes and inactive tints
     */
    fun faintTrackColor(trackIndex: Int): Color {
        return when ((trackIndex % 3 + 3) % 3) {
            0 -> coral.faint
            1 -> sky.faint
            else -> mint.faint
        }
    }
}

// Backward-compatibility aliases for legacy callers (Strictly mapped to 3-color Palette tokens, zero Color(0x...))
val BrandPrimary: Color get() = Palette.MintBase
val BrandPrimaryVariant: Color get() = Palette.SkyBase
val BrandAccent: Color get() = Palette.SkyBase
val BrandAccentCyan: Color get() = Palette.SkyBase
val SignalRecordRed: Color get() = Palette.CoralBase
val SignalMuteYellow: Color get() = Palette.CoralDim
val SignalClipCrimson: Color get() = Palette.CoralBase
val MeterElectricGreen: Color get() = Palette.MintBase
val MeterWarmAmber: Color get() = Palette.MintDim
val MeterVibrantRed: Color get() = Palette.CoralBase
val MeterClipOver: Color get() = Palette.CoralBase
val SurfaceCanvas: Color get() = Palette.N0Workspace
val SurfaceCard: Color get() = Palette.N2Surface
val SurfaceCardElevated: Color get() = Palette.N3Raised
val SurfacePersistentBar: Color get() = Palette.N2Surface
val SurfaceModalSheet: Color get() = Palette.N3Raised
val DividingStroke: Color get() = Palette.N3Raised
val DividingStrokeLight: Color get() = Palette.N1Grid
val TextHighEmphasis: Color get() = Palette.InkOnDark
val TextMediumEmphasis: Color get() = Palette.InkMuted
val TextDisabled: Color get() = Palette.InkMuted
val TrackColor1: Color get() = Palette.CoralBase
val TrackColor2: Color get() = Palette.SkyBase
val TrackColor3: Color get() = Palette.MintBase
val TrackColor4: Color get() = Palette.CoralDim
val TrackColor5: Color get() = Palette.SkyDim
val TrackColor6: Color get() = Palette.MintDim
val TrackColor7: Color get() = Palette.CoralFaint
val TrackColor8: Color get() = Palette.SkyFaint

