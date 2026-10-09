package com.android.daw.ui.studio

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.android.daw.ui.theme.Daw
import com.android.daw.ui.theme.DawIcons
import com.android.daw.ui.theme.DawTheme
import com.android.daw.viewmodel.TrackUiModel

/**
 * SD Studio DAW Compact Track Header (Tier 0 Surface)
 *
 * Strictly adheres to agy_ui_overhaul_prompt.md Section 5.3:
 * - Exactly 40dp wide (Daw.space.circleSm)
 * - 48dp tall (Daw.space.rowHeight)
 * - Channel-type icon centered
 * - Selected track = full-height block in channel color with dark icon
 * - Unselected track = N2 Surface background with channel color accent line
 * - Zero volume sliders or M/S/R buttons on surface (relocated to Mixer)
 * - Zero stock Material 3 widgets, zero hex Color literals
 */
@Composable
fun TrackHeader(
    track: TrackUiModel,
    trackIndex: Int,
    isSelected: Boolean,
    onSelectTrack: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = Daw.space.circleSm,
    height: Dp = Daw.space.rowHeight
) {
    val trackColor = Daw.colors.trackColor(trackIndex)

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .background(
                color = if (isSelected) trackColor else Daw.colors.n2Surface,
                shape = Daw.radii.none
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelectTrack
            ),
        contentAlignment = Alignment.Center
    ) {
        // Channel-type icon centered
        Image(
            imageVector = DawIcons.Track,
            contentDescription = track.name,
            colorFilter = ColorFilter.tint(if (isSelected) Daw.colors.inkOnLight else trackColor),
            modifier = Modifier.size(Daw.space.iconMin)
        )

        // Subtle left channel color indicator strip for unselected tracks
        if (!isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(Daw.space.indicator)
                    .fillMaxHeight()
                    .background(trackColor)
            )
        }
    }
}

@Preview(name = "TrackHeader Preview")
@Composable
private fun TrackHeaderPreview() {
    DawTheme {
        TrackHeader(
            track = TrackUiModel(trackId = 0, name = "Kick Drum"),
            trackIndex = 0,
            isSelected = true,
            onSelectTrack = {}
        )
    }
}
