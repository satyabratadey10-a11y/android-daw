package com.android.daw.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * SD Studio DAW Corner Radius Tokens
 *
 * Strict single-radius-per-surface discipline:
 * - none: 0dp (grid cells, rulers, dividing boundaries)
 * - xs: 4dp (clips, note blocks, fader thumbs)
 * - sm: 8dp (knob caps, status chips)
 * - md: 12dp (popup menus, module cards, drawer top-inner corners)
 * - full: 1000dp (all circular buttons, FABs)
 */
@Immutable
data class DawRadii(
    val none: CornerBasedShape = RoundedCornerShape(0.dp),
    val xs: CornerBasedShape = RoundedCornerShape(4.dp),
    val sm: CornerBasedShape = RoundedCornerShape(8.dp),
    val md: CornerBasedShape = RoundedCornerShape(12.dp),
    val full: CornerBasedShape = CircleShape,

    val noneDp: Dp = 0.dp,
    val xsDp: Dp = 4.dp,
    val smDp: Dp = 8.dp,
    val mdDp: Dp = 12.dp,
    val fullDp: Dp = 1000.dp
)

object Radii {
    val none: CornerBasedShape = RoundedCornerShape(0.dp)
    val xs: CornerBasedShape = RoundedCornerShape(4.dp)
    val sm: CornerBasedShape = RoundedCornerShape(8.dp)
    val md: CornerBasedShape = RoundedCornerShape(12.dp)
    val full: CornerBasedShape = CircleShape

    val noneDp: Dp = 0.dp
    val xsDp: Dp = 4.dp
    val smDp: Dp = 8.dp
    val mdDp: Dp = 12.dp
    val fullDp: Dp = 1000.dp

    val defaultDawRadii = DawRadii()
}
