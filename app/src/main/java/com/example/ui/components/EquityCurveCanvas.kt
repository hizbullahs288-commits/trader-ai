package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.domain.model.EquityCurvePoint
import com.example.ui.theme.BearRed
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.BullGreen
import kotlin.math.max
import kotlin.math.min

@Composable
fun EquityCurveCanvas(
    points: List<EquityCurvePoint>,
    baselineEquity: Double = 100_000.0,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        if (points.size < 2) {
            val midY = size.height / 2f
            drawLine(
                color = BorderSlate,
                start = Offset(0f, midY),
                end = Offset(size.width, midY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            )
            return@Canvas
        }

        val minEq = min(points.minOf { it.equity }, baselineEquity)
        val maxEq = max(points.maxOf { it.equity }, baselineEquity)
        val pad = ((maxEq - minEq) * 0.12).coerceAtLeast(100.0)
        val low = minEq - pad
        val high = maxEq + pad
        val range = (high - low).coerceAtLeast(1.0)

        fun toY(eq: Double): Float = size.height - (((eq - low) / range).toFloat() * size.height)

        val baseLineY = toY(baselineEquity)
        drawLine(
            color = BorderSlate,
            start = Offset(0f, baseLineY),
            end = Offset(size.width, baseLineY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        )

        val stepX = size.width / (points.size - 1).coerceAtLeast(1)
        val lineColor = if (points.last().equity >= baselineEquity) BullGreen else BearRed

        val linePath = Path()
        val fillPath = Path()
        points.forEachIndexed { idx, pt ->
            val x = idx * stepX
            val y = toY(pt.equity)
            if (idx == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            if (idx == points.lastIndex) {
                fillPath.lineTo(x, size.height)
                fillPath.close()
            }
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.0f))
            )
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.2.dp.toPx())
        )

        val lastX = (points.size - 1) * stepX
        val lastY = toY(points.last().equity)
        drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(lastX, lastY))
    }
}
