package com.android.daw.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.android.daw.ui.theme.Daw
import com.android.daw.ui.theme.DawTheme

/**
 * PanPot
 *
 * Rotary pan controller with center detent snapping.
 * Implemented using SD Studio RingKnob adhering to the 4-tier design system.
 */
@Composable
fun PanPot(
    pan: Float, // -1.0f (L) to +1.0f (R)
    onPanChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "PAN",
    size: Dp = Daw.space.circleLg
) {
    RingKnob(
        value = pan,
        onValueChange = { newVal ->
            // Snap to center if within +/- 0.05
            val snapped = if (kotlin.math.abs(newVal) < 0.05f) 0f else newVal
            onPanChange(snapped)
        },
        modifier = modifier,
        label = label,
        defaultValue = 0f,
        isBipolar = true,
        activeColor = Daw.colors.sky.base,
        trackColor = Daw.colors.n3Raised,
        size = size
    )
}

@Preview(name = "PanPot Preview")
@Composable
private fun PanPotPreview() {
    DawTheme {
        PanPot(
            pan = 0f,
            onPanChange = {}
        )
    }
}
