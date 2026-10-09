package com.android.daw.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.android.daw.ui.theme.Daw
import com.android.daw.ui.theme.DawTheme

/**
 * SD Studio DAW Sliding Tabs
 *
 * Strictly adheres to agy_ui_overhaul_prompt.md:
 * - Single shared underline that stretches and settles with playful spring
 * - 48dp minimum item height (Daw.space.touchTargetMin)
 * - 3dp Mint underline indicator (Daw.space.indicator)
 * - Zero stock Material 3 TabRow / Tab
 */
@Composable
fun SlidingTabs(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Daw.colors.mint.base,
    inactiveTextColor: Color = Daw.colors.inkMuted,
    activeTextColor: Color = Daw.colors.inkOnDark,
    backgroundColor: Color = Daw.colors.n2Surface
) {
    if (tabs.isEmpty()) return

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(Daw.space.touchTargetMin)
            .background(backgroundColor)
    ) {
        val tabWidth = maxWidth / tabs.size.coerceAtLeast(1)

        val animatedIndicatorOffset by animateDpAsState(
            targetValue = tabWidth * selectedTabIndex.coerceIn(0, tabs.size - 1),
            animationSpec = Daw.motion.playful(),
            label = "sliding_tab_indicator_offset"
        )

        // Bottom baseline stroke (1dp N3 divider)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(Daw.space.hairline)
                .background(Daw.colors.n3Raised)
        )

        // Sliding Underline Indicator (3dp height with playful spring settle)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = animatedIndicatorOffset)
                .width(tabWidth)
                .height(Daw.space.indicator)
                .background(
                    color = activeColor,
                    shape = Daw.radii.xs
                )
        )

        // Interactive Tab Items Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Daw.space.touchTargetMin),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = index == selectedTabIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(Daw.space.touchTargetMin)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onTabSelected(index) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = Daw.type.label,
                        color = if (isSelected) activeTextColor else inactiveTextColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Preview(name = "SlidingTabs Preview")
@Composable
private fun SlidingTabsPreview() {
    DawTheme {
        SlidingTabs(
            tabs = listOf("SAMPLE", "FILTER", "FLT ENV", "FRQ ENV", "LFO"),
            selectedTabIndex = 1,
            onTabSelected = {}
        )
    }
}
