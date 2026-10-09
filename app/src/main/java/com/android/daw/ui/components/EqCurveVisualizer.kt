package com.android.daw.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.daw.ui.theme.Daw
import com.android.daw.viewmodel.EqBandState
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow

/**
 * EqCurveVisualizer
 *
 * Studio-grade analytical composite biquad equalizer curve visualizer.
 * Features:
 * - Exact mathematical evaluation of cascaded biquad transfer functions across 20 Hz to 20 kHz.
 * - Logarithmic frequency scale with decibel grid lines (+12 dB, 0 dB, -12 dB).
 * - Interactive draggable filter nodes with frequency and gain manipulation.
 * - Filled spectral area under response with illuminated curve outline.
 * - Zero legacy Color tokens, uses Daw.colors.* and Daw.space.*.
 */
@Composable
fun EqCurveVisualizer(
    bands: List<EqBandState>,
    onUpdateBand: (bandIndex: Int, freqHz: Float, gainDb: Float, q: Float) -> Unit,
    modifier: Modifier = Modifier,
    sampleRate: Int = 44100
) {
    var activeDraggingBandIndex by remember { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(Daw.radii.sm)
            .background(Daw.colors.n2Surface)
            .padding(Daw.space.xs)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bands) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val width = size.width.toFloat()
                            val height = size.height.toFloat()

                            val touchRadiusPx = Daw.space.touchTargetMin.toPx()
                            val closestBand = bands.minByOrNull { band ->
                                val nodeX = freqToX(band.frequencyHz, width)
                                val nodeY = dbToY(band.gainDb, height)
                                hypot(offset.x - nodeX, offset.y - nodeY)
                            }

                            if (closestBand != null) {
                                val nodeX = freqToX(closestBand.frequencyHz, width)
                                val nodeY = dbToY(closestBand.gainDb, height)
                                if (hypot(offset.x - nodeX, offset.y - nodeY) <= touchRadiusPx * 1.5f) {
                                    activeDraggingBandIndex = closestBand.bandIndex
                                }
                            }
                        },
                        onDragEnd = {
                            activeDraggingBandIndex = null
                        },
                        onDragCancel = {
                            activeDraggingBandIndex = null
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val bandIndex = activeDraggingBandIndex ?: return@detectDragGestures
                            val targetBand = bands.firstOrNull { it.bandIndex == bandIndex } ?: return@detectDragGestures

                            val width = size.width.toFloat()
                            val height = size.height.toFloat()

                            val newFreq = xToFreq(change.position.x, width).coerceIn(20f, 20000f)
                            val newGain = yToDb(change.position.y, height).coerceIn(-18f, 18f)

                            onUpdateBand(bandIndex, newFreq, newGain, targetBand.q)
                        }
                    )
                }
        ) {
            val width = size.width
            val height = size.height

            // 1. Draw Grid Lines & Frequency Labels
            val freqGuides = listOf(100f to "100Hz", 1000f to "1kHz", 10000f to "10kHz")
            freqGuides.forEach { (freq, label) ->
                val x = freqToX(freq, width)
                drawLine(
                    color = Daw.colors.n3Raised,
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = Daw.space.hairline.toPx()
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    topLeft = Offset(x + Daw.space.xs.toPx(), height - 16.dp.toPx()),
                    style = TextStyle(color = Daw.colors.inkMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                )
            }

            val dbGuides = listOf(12f to "+12dB", 0f to " 0dB", -12f to "-12dB")
            dbGuides.forEach { (db, label) ->
                val y = dbToY(db, height)
                drawLine(
                    color = if (db == 0f) Daw.colors.mint.faint else Daw.colors.n3Raised,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = if (db == 0f) Daw.space.stroke.toPx() else Daw.space.hairline.toPx()
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    topLeft = Offset(Daw.space.xs.toPx(), y - 12.dp.toPx()),
                    style = TextStyle(color = Daw.colors.inkMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                )
            }

            // 2. Analytical Composite Magnitude Curve Construction
            val curvePath = Path()
            val fillPath = Path()
            val numSteps = 120

            val y0dB = dbToY(0f, height)

            for (step in 0..numSteps) {
                val normX = step.toFloat() / numSteps
                val freq = 20f * 10f.pow(normX * 3.0f) // 20 Hz to 20,000 Hz

                // Sum individual biquad gains in decibels
                var compositeGainDb = 0f
                for (b in bands) {
                    compositeGainDb += b.calculateGainAtFrequency(freq, sampleRate)
                }
                val clampedGain = compositeGainDb.coerceIn(-24f, 24f)

                val x = normX * width
                val y = dbToY(clampedGain, height)

                if (step == 0) {
                    curvePath.moveTo(x, y)
                    fillPath.moveTo(x, y0dB)
                    fillPath.lineTo(x, y)
                } else {
                    curvePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }

            fillPath.lineTo(width, y0dB)
            fillPath.close()

            // 3. Render Filled Spectral Gradient + Outline Curve
            val fillBrush = Brush.verticalGradient(
                colors = listOf(
                    Daw.colors.mint.base.copy(alpha = 0.35f),
                    Daw.colors.mint.base.copy(alpha = 0.05f)
                ),
                startY = 0f,
                endY = height
            )

            drawPath(path = fillPath, brush = fillBrush)

            drawPath(
                path = curvePath,
                color = Daw.colors.mint.base,
                style = Stroke(width = Daw.space.indicator.toPx())
            )

            // 4. Interactive Drag Nodes for each EQ band
            bands.forEach { band ->
                val nodeX = freqToX(band.frequencyHz, width)
                val nodeY = dbToY(band.gainDb, height)
                val isSelected = (activeDraggingBandIndex == band.bandIndex)
                val nodeColor = Daw.colors.trackColor(band.bandIndex)

                // Outer halo
                drawCircle(
                    color = Daw.colors.n0Workspace.copy(alpha = 0.8f),
                    radius = if (isSelected) 14.dp.toPx() else 10.dp.toPx(),
                    center = Offset(nodeX, nodeY)
                )
                // Color ring
                drawCircle(
                    color = nodeColor,
                    radius = if (isSelected) 10.dp.toPx() else 7.dp.toPx(),
                    center = Offset(nodeX, nodeY)
                )
                // Center pin
                drawCircle(
                    color = Daw.colors.inkOnDark,
                    radius = Daw.space.indicator.toPx(),
                    center = Offset(nodeX, nodeY)
                )

                // Band index label near node
                drawText(
                    textMeasurer = textMeasurer,
                    text = "${band.bandIndex + 1}",
                    topLeft = Offset(nodeX - 3.dp.toPx(), nodeY - 18.dp.toPx()),
                    style = TextStyle(color = Daw.colors.inkOnDark, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                )
            }
        }
    }
}

fun freqToX(f: Float, width: Float): Float {
    val clamped = f.coerceIn(20f, 20000f)
    return width * (log10(clamped / 20f) / 3.0f)
}

fun xToFreq(x: Float, width: Float): Float {
    if (width <= 0f) return 1000f
    val norm = (x / width).coerceIn(0f, 1f)
    return 20f * 10f.pow(norm * 3.0f)
}

fun dbToY(db: Float, height: Float): Float {
    val norm = 0.5f - (db.coerceIn(-18f, 18f) / 36.0f)
    return height * norm
}

fun yToDb(y: Float, height: Float): Float {
    if (height <= 0f) return 0f
    val norm = (y / height).coerceIn(0f, 1f)
    return (0.5f - norm) * 36.0f
}
