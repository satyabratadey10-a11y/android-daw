package com.android.daw.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.daw.ui.theme.Daw
import java.util.Locale

/**
 * BPM readout widget adhering strictly to UI-02:
 * - Single line (softWrap = false, maxLines = 1)
 * - Fixed width sized for "999" (44dp)
 * - Tabular numerals ("tnum")
 * - "BPM" caption below
 */
@Composable
fun BpmReadout(
    bpm: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val bpmFormatted = String.format(Locale.US, "%.0f", bpm)
        Text(
            text = bpmFormatted,
            style = Daw.type.mono.copy(
                fontFeatureSettings = "tnum",
                textAlign = TextAlign.Center
            ),
            maxLines = 1,
            softWrap = false,
            fontWeight = FontWeight.Bold,
            color = Daw.colors.inkOnDark
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "BPM",
            style = Daw.type.caption,
            fontSize = 8.sp,
            color = Daw.colors.inkMuted,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center
        )
    }
}
