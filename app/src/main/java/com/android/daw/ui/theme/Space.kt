package com.android.daw.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * SD Studio DAW Spacing & Sizing Tokens
 *
 * Strictly adheres to the 4dp grid system:
 * Every margin, padding, width, height, and gap is tokenized.
 */
@Immutable
data class DawSpace(
    // 4dp Grid Core Tokens
    val none: Dp = 0.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,

    // Specialized DAW Dimensions (Multiples of 4dp / Sub-pixel lines)
    val hairline: Dp = 1.dp,
    val stroke: Dp = 2.dp,
    val indicator: Dp = 3.dp,
    val touchTargetMin: Dp = 48.dp,
    val iconMin: Dp = 24.dp,
    val circleSm: Dp = 40.dp,
    val circleMd: Dp = 48.dp,
    val circleLg: Dp = 56.dp,
    val dial: Dp = 160.dp,
    val faderTrackWidth: Dp = 4.dp,
    val faderThumbHeight: Dp = 24.dp,
    val faderThumbWidth: Dp = 48.dp,
    val rowHeight: Dp = 48.dp,
    val transportHeight: Dp = 48.dp,
    val rulerHeight: Dp = 36.dp,
    val stripWidth: Dp = 96.dp,
    val rulerCap: Dp = 12.dp,
    val panelMaxWidth: Dp = 520.dp
)

object Space {
    val none: Dp = 0.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp

    val hairline: Dp = 1.dp
    val stroke: Dp = 2.dp
    val indicator: Dp = 3.dp
    val touchTargetMin: Dp = 48.dp
    val iconMin: Dp = 24.dp
    val circleSm: Dp = 40.dp
    val circleMd: Dp = 48.dp
    val circleLg: Dp = 56.dp
    val dial: Dp = 160.dp
    val faderTrackWidth: Dp = 4.dp
    val faderThumbHeight: Dp = 24.dp
    val faderThumbWidth: Dp = 48.dp
    val rowHeight: Dp = 48.dp
    val transportHeight: Dp = 48.dp
    val rulerHeight: Dp = 36.dp
    val stripWidth: Dp = 96.dp
    val rulerCap: Dp = 12.dp
    val panelMaxWidth: Dp = 520.dp

    val defaultDawSpace = DawSpace()
}
