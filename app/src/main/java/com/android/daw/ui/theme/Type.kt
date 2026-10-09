package com.android.daw.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * SD Studio DAW Typography System
 *
 * Strict 5-size type hierarchy:
 * 1. Display: 22.sp (Large titles, hero numbers)
 * 2. Title: 16.sp (Panel headers, section titles)
 * 3. Label: 12.sp (Button labels, track names, tabs)
 * 4. Caption: 10.sp (Secondary text, units, small badges)
 * 5. Mono: 14.sp (Tabular numerals for BPM, timecode, dB values)
 */
@Immutable
data class DawType(
    val display: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    val title: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp
    ),
    val label: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp
    ),
    val caption: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp
    ),
    val mono: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp
    )
)

object Type {
    val defaultDawType = DawType()
}

// Monospace styles dedicated for timecode & telemetry readouts (Backwards compatible)
val TimecodeMusicalStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 14.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.5.sp
)

val TimecodeWallClockStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.5.sp
)

val DbReadoutStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.2.sp
)

// Material 3 Typography mapping to the 5 standard DAW type scales
val DawTypography = Typography(
    displayLarge = Type.defaultDawType.display,
    displayMedium = Type.defaultDawType.display,
    displaySmall = Type.defaultDawType.display,
    headlineLarge = Type.defaultDawType.title,
    headlineMedium = Type.defaultDawType.title,
    headlineSmall = Type.defaultDawType.title,
    titleLarge = Type.defaultDawType.title,
    titleMedium = Type.defaultDawType.title,
    titleSmall = Type.defaultDawType.label,
    bodyLarge = Type.defaultDawType.title,
    bodyMedium = Type.defaultDawType.label,
    bodySmall = Type.defaultDawType.caption,
    labelLarge = Type.defaultDawType.mono,
    labelMedium = Type.defaultDawType.label,
    labelSmall = Type.defaultDawType.caption
)
