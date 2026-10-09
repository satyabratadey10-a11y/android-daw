package com.android.daw

import com.android.daw.ui.theme.Layer
import com.android.daw.ui.theme.Palette
import com.android.daw.ui.theme.Space
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Unit test suite for SD Studio design tokens.
 * Verifies 4dp grid compliance, primary hue chromatic integrity,
 * and monotonic z-axis elevation layer ordering.
 */
class DesignTokensTest {

    @Test
    fun testSpaceValuesAreMultiplesOf4Dp() {
        // Core 4dp grid increments
        val coreSpaces = listOf(
            Space.none,
            Space.xs,
            Space.sm,
            Space.md,
            Space.lg,
            Space.xl,
            Space.xxl
        )

        for (space in coreSpaces) {
            val dpVal = space.value
            assertEquals("Core space $dpVal dp must be a multiple of 4", 0f, dpVal % 4f, 1e-6f)
        }

        // Exact expected token values
        assertEquals(0f, Space.none.value, 1e-6f)
        assertEquals(4f, Space.xs.value, 1e-6f)
        assertEquals(8f, Space.sm.value, 1e-6f)
        assertEquals(12f, Space.md.value, 1e-6f)
        assertEquals(16f, Space.lg.value, 1e-6f)
        assertEquals(24f, Space.xl.value, 1e-6f)
        assertEquals(32f, Space.xxl.value, 1e-6f)

        // Specialized ergonomic dimensions
        val ergonomicSpaces = listOf(
            Space.touchTargetMin,
            Space.iconMin,
            Space.circleSm,
            Space.circleMd,
            Space.circleLg,
            Space.dial,
            Space.faderTrackWidth,
            Space.faderThumbHeight,
            Space.faderThumbWidth,
            Space.rowHeight,
            Space.transportHeight,
            Space.rulerHeight,
            Space.stripWidth,
            Space.rulerCap,
            Space.panelMaxWidth
        )

        for (space in ergonomicSpaces) {
            val dpVal = space.value
            assertEquals("Dimension $dpVal dp must be a multiple of 4", 0f, dpVal % 4f, 1e-6f)
        }

        // Ergonomic touch target minimum must be >= 48dp
        assertTrue("touchTargetMin must be >= 48dp", Space.touchTargetMin.value >= 48f)

        // Sub-pixel line tokens
        assertEquals(1f, Space.hairline.value, 1e-6f)
        assertEquals(2f, Space.stroke.value, 1e-6f)
        assertEquals(3f, Space.indicator.value, 1e-6f)
    }

    @Test
    fun testPalettePrimaryHuesIntegrity() {
        // Mint: #CDA1FF (Red = 0xCD, Green = 0xA1, Blue = 0xFF)
        val mintR = (Palette.MintBase.red * 255f).roundToInt()
        val mintG = (Palette.MintBase.green * 255f).roundToInt()
        val mintB = (Palette.MintBase.blue * 255f).roundToInt()
        assertEquals("Mint Red must be 0xCD (205)", 0xCD, mintR)
        assertEquals("Mint Green must be 0xA1 (161)", 0xA1, mintG)
        assertEquals("Mint Blue must be 0xFF (255)", 0xFF, mintB)

        // Coral: #BFCDF5 (Red = 0xBF, Green = 0xCD, Blue = 0xF5)
        val coralR = (Palette.CoralBase.red * 255f).roundToInt()
        val coralG = (Palette.CoralBase.green * 255f).roundToInt()
        val coralB = (Palette.CoralBase.blue * 255f).roundToInt()
        assertEquals("Coral Red must be 0xBF (191)", 0xBF, coralR)
        assertEquals("Coral Green must be 0xCD (205)", 0xCD, coralG)
        assertEquals("Coral Blue must be 0xF5 (245)", 0xF5, coralB)

        // Sky: #4DB8FF (Red = 0x4D, Green = 0xB8, Blue = 0xFF)
        val skyR = (Palette.SkyBase.red * 255f).roundToInt()
        val skyG = (Palette.SkyBase.green * 255f).roundToInt()
        val skyB = (Palette.SkyBase.blue * 255f).roundToInt()
        assertEquals("Sky Red must be 0x4D (77)", 0x4D, skyR)
        assertEquals("Sky Green must be 0xB8 (184)", 0xB8, skyG)
        assertEquals("Sky Blue must be 0xFF (255)", 0xFF, skyB)
    }

    @Test
    fun testPaletteTrackColorCyclingRule() {
        val dawColors = Palette.defaultDawColors

        // Track index 0 -> Coral
        assertEquals(Palette.CoralBase, dawColors.trackColor(0))
        // Track index 1 -> Sky
        assertEquals(Palette.SkyBase, dawColors.trackColor(1))
        // Track index 2 -> Mint
        assertEquals(Palette.MintBase, dawColors.trackColor(2))

        // Tracks index >= 3 cycle using dim tones
        assertEquals(Palette.CoralDim, dawColors.trackColor(3))
        assertEquals(Palette.SkyDim, dawColors.trackColor(4))
        assertEquals(Palette.MintDim, dawColors.trackColor(5))
    }

    @Test
    fun testLayerElevationsAreMonotonicallyOrdered() {
        // Verify strict hierarchy:
        // Workspace < Content < Playhead < Floating < Panel < Popup < Modal < Toast
        assertTrue(Layer.Workspace < Layer.Content)
        assertTrue(Layer.Content < Layer.Playhead)
        assertTrue(Layer.Playhead < Layer.Floating)
        assertTrue(Layer.Floating < Layer.Panel)
        assertTrue(Layer.Panel < Layer.Popup)
        assertTrue(Layer.Popup < Layer.Modal)
        assertTrue(Layer.Modal < Layer.Toast)

        // Verify exact index constants
        assertEquals(0f, Layer.Workspace, 1e-6f)
        assertEquals(1f, Layer.Content, 1e-6f)
        assertEquals(2f, Layer.Playhead, 1e-6f)
        assertEquals(3f, Layer.Floating, 1e-6f)
        assertEquals(4f, Layer.Panel, 1e-6f)
        assertEquals(5f, Layer.Popup, 1e-6f)
        assertEquals(6f, Layer.Modal, 1e-6f)
        assertEquals(7f, Layer.Toast, 1e-6f)
    }
}
