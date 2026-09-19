package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.vision.Direction

@Composable
fun FinishLineOverlay(
    stripPositionRatio: Float,
    stripWidthPx: Int,
    dualStripEnabled: Boolean,
    motionA: Float,
    motionB: Float,
    thresholdRatio: Float,
    detectedDirection: Direction,
    isTriggered: Boolean,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Calculate X coordinates
        val finishX = width * stripPositionRatio
        val separationPx = width * 0.12f
        val guardAX = finishX - separationPx

        val stripWidth = stripWidthPx.toFloat().coerceIn(16f, 72f)

        // Subtle track guide grid
        val gridColor = Color(0x33FFFFFF)
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)

        // Lane horizon guidelines
        drawLine(
            color = gridColor,
            start = Offset(0f, height * 0.3f),
            end = Offset(width, height * 0.3f),
            strokeWidth = 1.5f,
            pathEffect = dashEffect
        )
        drawLine(
            color = gridColor,
            start = Offset(0f, height * 0.7f),
            end = Offset(width, height * 0.7f),
            strokeWidth = 1.5f,
            pathEffect = dashEffect
        )

        // 1. Draw Guard Strip A (if Dual Strip enabled)
        if (dualStripEnabled && guardAX > 0) {
            val stripAColor = if (motionA >= thresholdRatio) Color(0xFF00F0FF) else Color(0x8806B6D4)

            // Strip A ROI bounding box
            drawRect(
                color = stripAColor.copy(alpha = 0.12f),
                topLeft = Offset(guardAX - stripWidth / 2, 0f),
                size = Size(stripWidth, height)
            )
            drawRect(
                color = stripAColor.copy(alpha = 0.5f),
                topLeft = Offset(guardAX - stripWidth / 2, 0f),
                size = Size(stripWidth, height),
                style = Stroke(width = 1.5f)
            )

            // Motion fill in Strip A
            if (motionA > 0.01f) {
                val fillHeightA = height * motionA.coerceIn(0f, 1f)
                drawRect(
                    color = Color(0xAA00F0FF),
                    topLeft = Offset(guardAX - stripWidth / 2, height - fillHeightA),
                    size = Size(stripWidth, fillHeightA)
                )
            }

            // Center laser line
            drawLine(
                color = stripAColor,
                start = Offset(guardAX, 0f),
                end = Offset(guardAX, height),
                strokeWidth = 2f
            )

            // Label
            drawText(
                textMeasurer = textMeasurer,
                text = "STRIP A [GUARD]",
                topLeft = Offset(guardAX - 50f, 32f),
                style = TextStyle(
                    color = stripAColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            )
        }

        // 2. Draw Finish Line Strip B
        val finishColor = when {
            isTriggered -> Color(0xFFFF2222)
            motionB >= thresholdRatio -> Color(0xFFF59E0B)
            else -> Color(0xFFEAB308)
        }

        // Strip B ROI box
        drawRect(
            color = finishColor.copy(alpha = if (isTriggered) 0.35f else 0.15f),
            topLeft = Offset(finishX - stripWidth / 2, 0f),
            size = Size(stripWidth, height)
        )
        drawRect(
            color = finishColor.copy(alpha = 0.8f),
            topLeft = Offset(finishX - stripWidth / 2, 0f),
            size = Size(stripWidth, height),
            style = Stroke(width = if (isTriggered) 3f else 2f)
        )

        // Motion fill in Strip B
        if (motionB > 0.01f) {
            val fillHeightB = height * motionB.coerceIn(0f, 1f)
            drawRect(
                color = finishColor.copy(alpha = 0.65f),
                topLeft = Offset(finishX - stripWidth / 2, height - fillHeightB),
                size = Size(stripWidth, fillHeightB)
            )
        }

        // Finish line laser center beam
        drawLine(
            color = finishColor,
            start = Offset(finishX, 0f),
            end = Offset(finishX, height),
            strokeWidth = if (isTriggered) 4f else 2.5f
        )

        // Threshold indicator notch on Finish Line
        val threshY = height * (1f - thresholdRatio)
        drawLine(
            color = Color.White,
            start = Offset(finishX - stripWidth / 2 - 8f, threshY),
            end = Offset(finishX + stripWidth / 2 + 8f, threshY),
            strokeWidth = 2f
        )

        // Labels
        val finishLabel = if (isTriggered) "TRIGGERED FINISH!" else "FINISH LINE"
        drawText(
            textMeasurer = textMeasurer,
            text = finishLabel,
            topLeft = Offset(finishX - 44f, 32f),
            style = TextStyle(
                color = finishColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        )

        // Direction arrow indicator if detected
        if (dualStripEnabled && detectedDirection != Direction.NONE) {
            val dirText = when (detectedDirection) {
                Direction.LEFT_TO_RIGHT -> "MOTION → → → [L to R]"
                Direction.RIGHT_TO_LEFT -> "[R to L] ← ← ← MOTION"
                Direction.ANY -> "MOTION DETECTED"
                Direction.NONE -> ""
            }
            drawText(
                textMeasurer = textMeasurer,
                text = dirText,
                topLeft = Offset(finishX - 60f, height - 70f),
                style = TextStyle(
                    color = Color.Yellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            )
        }
    }
}
