package com.android.daw.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.android.daw.ui.theme.Daw

/**
 * SD Studio DAW Divider Components
 *
 * Strictly token-driven 1dp structural hairline dividers adhering to agy_ui_overhaul_prompt.md:
 * - DawHorizontalDivider: spans width with thickness = Daw.space.hairline, color = Daw.colors.n3Raised
 * - DawVerticalDivider: spans height with thickness = Daw.space.hairline, color = Daw.colors.n3Raised
 */
@Composable
fun DawHorizontalDivider(
    modifier: Modifier = Modifier,
    color: Color = Daw.colors.n3Raised,
    thickness: Dp = Daw.space.hairline
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .background(color)
    )
}

@Composable
fun DawVerticalDivider(
    modifier: Modifier = Modifier,
    color: Color = Daw.colors.n3Raised,
    thickness: Dp = Daw.space.hairline
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(thickness)
            .background(color)
    )
}
