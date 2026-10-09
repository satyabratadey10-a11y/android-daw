package com.android.daw.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.android.daw.ui.theme.Daw

/**
 * SD Studio DAW ListRow Component
 *
 * Strictly adheres to agy_ui_overhaul_prompt.md (Section 5.5, 5.6):
 * - 48dp height (Daw.space.rowHeight / touchTargetMin)
 * - 16dp horizontal text inset (Daw.space.lg)
 * - Optional leading icon / slot
 * - Title (Daw.type.label) + Subtitle (Daw.type.caption)
 * - Optional trailing value / control / chevron slot
 * - Selected indicator bar (Daw.space.indicator) in active color
 * - 100% token-driven, zero raw dp/sp or hardcoded hex colors
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    activeColor: Color = Daw.colors.active,
    onClick: (() -> Unit)? = null
) {
    val rowModifier = modifier
        .fillMaxWidth()
        .height(Daw.space.rowHeight)
        .clip(Daw.radii.xs)
        .background(
            if (selected) {
                Daw.colors.n3Raised
            } else {
                Daw.colors.transparent
            }
        )
        .then(
            if (onClick != null && enabled) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )
        .alpha(if (enabled) 1f else 0.38f)

    Box(
        modifier = rowModifier,
        contentAlignment = Alignment.CenterStart
    ) {
        // Active selection indicator bar on left edge
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(Daw.space.indicator)
                    .background(activeColor)
                    .align(Alignment.CenterStart)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Daw.space.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Daw.space.md)
        ) {
            // Optional leading slot (icon, indicator, or number)
            leadingContent?.let { leading ->
                Box(contentAlignment = Alignment.Center) {
                    leading()
                }
            }

            // Title & optional subtitle column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = Daw.type.label,
                    color = if (selected) activeColor else Daw.colors.inkOnDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                subtitle?.let { sub ->
                    Text(
                        text = sub,
                        style = Daw.type.caption,
                        color = Daw.colors.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Optional trailing slot (value, chevron, switch, or button)
            trailingContent?.let { trailing ->
                Box(contentAlignment = Alignment.CenterEnd) {
                    trailing()
                }
            }
        }
    }
}
