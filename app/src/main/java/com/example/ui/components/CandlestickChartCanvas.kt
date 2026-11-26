package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.BollingerPoint
import com.example.domain.model.Candle
import com.example.domain.model.LevelType
import com.example.domain.model.MacdPoint
import com.example.domain.model.PriceLevel
import com.example.domain.model.TechnicalAnalysisBundle
import com.example.ui.theme.BearRed
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.BullGreen
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MonoLabelSmall
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.viewmodel.OverlayPreferences
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun CandlestickChartSection(
    candles: List<Candle>,
    analysis: TechnicalAnalysisBundle,
    overlays: OverlayPreferences,
    priceDecimals: Int,
    visibleCount: Int,
    modifier: Modifier = Modifier
) {
    if (candles.isEmpty()) return

    val sliceCount = visibleCount.coerceIn(10, candles.size)
    val startIdx = (candles.size - sliceCount).coerceAtLeast(0)
    val visibleCandles = remember(candles, startIdx) { candles.subList(startIdx, candles.size) }
    val visibleEma20 = remember(analysis.ema20Series, startIdx) {
        if (analysis.ema20Series.size == candles.size) analysis.ema20Series.subList(startIdx, candles.size) else emptyList()
    }
    val visibleEma50 = remember(analysis.ema50Series, startIdx) {
        if (analysis.ema50Series.size == candles.size) analysis.ema50Series.subList(startIdx, candles.size) else emptyList()
    }
    val visibleBb = remember(analysis.bollingerSeries, startIdx) {
        if (analysis.bollingerSeries.size == candles.size) analysis.bollingerSeries.subList(startIdx, candles.size) else emptyList()
    }
    val visibleRsi = remember(analysis.rsi14Series, startIdx) {
        if (analysis.rsi14Series.size == candles.size) analysis.rsi14Series.subList(startIdx, candles.size) else emptyList()
    }
    val visibleMacd = remember(analysis.macdSeries, startIdx) {
        if (analysis.macdSeries.size == candles.size) analysis.macdSeries.subList(startIdx, candles.size) else emptyList()
    }

    var selectedIndex by remember(visibleCandles.size, candles.lastOrNull()?.timestampMs) {
        mutableStateOf<Int?>(null)
    }

    val inspectedIdx = selectedIndex?.coerceIn(0, visibleCandles.lastIndex) ?: visibleCandles.lastIndex
    val inspectedCandle = visibleCandles[inspectedIdx]
    val inspectedRsi = visibleRsi.getOrNull(inspectedIdx)
    val inspectedMacd = visibleMacd.getOrNull(inspectedIdx)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, BorderSlate.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Crosshair OHLCV Readout Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianBg.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = TradingFormatters.formatTime(inspectedCandle.timestampMs),
                    style = MonoLabelSmall,
                    color = TextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OhlcTag("O", inspectedCandle.open, priceDecimals, TextSecondary)
                    OhlcTag("H", inspectedCandle.high, priceDecimals, BullGreen)
                    OhlcTag("L", inspectedCandle.low, priceDecimals, BearRed)
                    OhlcTag(
                        "C",
                        inspectedCandle.close,
                        priceDecimals,
                        if (inspectedCandle.isBullish) BullGreen else BearRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Main Candlestick Canvas
            CandlestickCanvas(
                candles = visibleCandles,
                ema20 = if (overlays.showEma20) visibleEma20 else emptyList(),
                ema50 = if (overlays.showEma50) visibleEma50 else emptyList(),
                bollinger = if (overlays.showBollinger) visibleBb else emptyList(),
                levels = if (overlays.showSupportResistance) analysis.supportResistanceLevels else emptyList(),
                priceDecimals = priceDecimals,
                selectedIndex = selectedIndex,
                onSelectIndex = { selectedIndex = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )

            // Volume Sub-Pane
            if (overlays.showVolume) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "VOLUME • ${TradingFormatters.formatVolume(inspectedCandle.volume)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
                VolumeCanvas(
                    candles = visibleCandles,
                    selectedIndex = selectedIndex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                )
            }

            // RSI Sub-Pane
            if (overlays.showRsi && visibleRsi.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                val rsiLabel = inspectedRsi?.let { String.format("%.1f", it) } ?: "--"
                val rsiColor = when {
                    (inspectedRsi ?: 50.0) >= 70.0 -> BearRed
                    (inspectedRsi ?: 50.0) <= 30.0 -> BullGreen
                    else -> CyanAccent
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "RSI (14)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                    Text(
                        text = rsiLabel,
                        style = MonoLabelSmall,
                        color = rsiColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                RsiCanvas(
                    rsiSeries = visibleRsi,
                    selectedIndex = selectedIndex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                )
            }

            // MACD Sub-Pane
            if (overlays.showMacd && visibleMacd.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "MACD (12, 26, 9)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                    Text(
                        text = inspectedMacd?.histogram?.let { String.format("%+.4f", it) } ?: "--",
                        style = MonoLabelSmall,
                        color = if ((inspectedMacd?.histogram ?: 0.0) >= 0.0) BullGreen else BearRed
                    )
                }
                MacdCanvas(
                    macdSeries = visibleMacd,
                    selectedIndex = selectedIndex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                )
            }
        }
    }
}

@Composable
private fun OhlcTag(label: String, price: Double, decimals: Int, valueColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )
        Text(
            text = TradingFormatters.formatPrice(price, decimals),
            style = MaterialTheme.typography.labelSmall,
            color = valueColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CandlestickCanvas(
    candles: List<Candle>,
    ema20: List<Double?>,
    ema50: List<Double?>,
    bollinger: List<BollingerPoint>,
    levels: List<PriceLevel>,
    priceDecimals: Int,
    selectedIndex: Int?,
    onSelectIndex: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val axisTextStyle = remember {
        TextStyle(
            color = TextTertiary,
            fontSize = 10.sp,
            fontFamily = JetBrainsMonoFontFamily
        )
    }

    Canvas(
        modifier = modifier
            .pointerInput(candles.size) {
                detectTapGestures { offset ->
                    val rightAxisW = 68.dp.toPx()
                    val plotW = (size.width - rightAxisW).coerceAtLeast(1f)
                    val step = plotW / candles.size.coerceAtLeast(1)
                    val idx = (offset.x / step).toInt().coerceIn(0, candles.lastIndex)
                    onSelectIndex(if (selectedIndex == idx) null else idx)
                }
            }
            .pointerInput(candles.size) {
                detectHorizontalDragGestures(
                    onDragEnd = {},
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val rightAxisW = 68.dp.toPx()
                        val plotW = (size.width - rightAxisW).coerceAtLeast(1f)
                        val step = plotW / candles.size.coerceAtLeast(1)
                        val idx = (change.position.x / step).toInt().coerceIn(0, candles.lastIndex)
                        onSelectIndex(idx)
                    }
                )
            }
    ) {
        val rightAxisWidth = 68.dp.toPx()
        val plotWidth = (size.width - rightAxisWidth).coerceAtLeast(1f)
        val plotHeight = size.height

        var minPrice = candles.minOf { it.low }
        var maxPrice = candles.maxOf { it.high }

        bollinger.forEach { bp ->
            bp.upper?.let { maxPrice = max(maxPrice, it) }
            bp.lower?.let { minPrice = min(minPrice, it) }
        }
        val padding = ((maxPrice - minPrice) * 0.08).coerceAtLeast(maxPrice * 0.001)
        minPrice -= padding
        maxPrice += padding
        val priceRange = (maxPrice - minPrice).coerceAtLeast(1e-6)

        fun priceToY(price: Double): Float {
            return plotHeight - (((price - minPrice) / priceRange).toFloat() * plotHeight)
        }

        // 1. Horizontal grid + price scale
        val gridSteps = 4
        val dashGrid = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        for (i in 0..gridSteps) {
            val ratio = i.toFloat() / gridSteps
            val y = plotHeight * ratio
            drawLine(
                color = BorderSlate.copy(alpha = 0.4f),
                start = Offset(0f, y),
                end = Offset(plotWidth, y),
                strokeWidth = 1f,
                pathEffect = dashGrid
            )
            val priceAtLine = maxPrice - (priceRange * ratio)
            val label = TradingFormatters.formatPrice(priceAtLine, priceDecimals)
            val measured = textMeasurer.measure(label, axisTextStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x = plotWidth + 6.dp.toPx(),
                    y = (y - measured.size.height / 2f).coerceIn(0f, plotHeight - measured.size.height)
                )
            )
        }

        val stepX = plotWidth / candles.size.coerceAtLeast(1)

        // 2. Support & Resistance lines
        val srDash = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
        levels.forEach { level ->
            if (level.price in minPrice..maxPrice) {
                val y = priceToY(level.price)
                val color = if (level.type == LevelType.SUPPORT) BullGreen.copy(alpha = 0.55f)
                else BearRed.copy(alpha = 0.55f)
                drawLine(
                    color = color,
                    start = Offset(0f, y),
                    end = Offset(plotWidth, y),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = srDash
                )
            }
        }

        // 3. Bollinger Bands channel
        if (bollinger.size == candles.size) {
            val upperPath = Path()
            val lowerPath = Path()
            var started = false
            bollinger.forEachIndexed { idx, bp ->
                val u = bp.upper
                val l = bp.lower
                if (u != null && l != null) {
                    val x = idx * stepX + stepX / 2f
                    val yu = priceToY(u)
                    val yl = priceToY(l)
                    if (!started) {
                        upperPath.moveTo(x, yu)
                        lowerPath.moveTo(x, yl)
                        started = true
                    } else {
                        upperPath.lineTo(x, yu)
                        lowerPath.lineTo(x, yl)
                    }
                }
            }
            if (started) {
                drawPath(upperPath, color = IndigoAccent.copy(alpha = 0.65f), style = Stroke(1.2.dp.toPx()))
                drawPath(lowerPath, color = IndigoAccent.copy(alpha = 0.65f), style = Stroke(1.2.dp.toPx()))
            }
        }

        // 4. EMA 20 & EMA 50 Overlays
        drawIndicatorLine(ema20, stepX, ::priceToY, CyanAccent, 1.5.dp.toPx())
        drawIndicatorLine(ema50, stepX, ::priceToY, GoldAccent, 1.5.dp.toPx())

        // 5. Candlesticks
        val bodyWidth = (stepX * 0.64f).coerceIn(2.dp.toPx(), 14.dp.toPx())
        candles.forEachIndexed { idx, candle ->
            val centerX = idx * stepX + stepX / 2f
            val color = if (candle.isBullish) BullGreen else BearRed

            val highY = priceToY(candle.high)
            val lowY = priceToY(candle.low)
            val openY = priceToY(candle.open)
            val closeY = priceToY(candle.close)

            // Wick
            drawLine(
                color = color,
                start = Offset(centerX, highY),
                end = Offset(centerX, lowY),
                strokeWidth = 1.3.dp.toPx()
            )

            // Body
            val topY = min(openY, closeY)
            val rawH = abs(closeY - openY)
            val bodyH = rawH.coerceAtLeast(1.5.dp.toPx())
            drawRect(
                color = color,
                topLeft = Offset(centerX - bodyWidth / 2f, topY),
                size = Size(bodyWidth, bodyH)
            )
        }

        // 6. Live price line
        val lastPrice = candles.last().close
        val lastY = priceToY(lastPrice)
        val liveColor = if (candles.last().isBullish) BullGreen else BearRed
        drawLine(
            color = liveColor.copy(alpha = 0.5f),
            start = Offset(0f, lastY),
            end = Offset(plotWidth, lastY),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
        )

        // 7. Crosshair on active selection
        if (selectedIndex != null && selectedIndex in candles.indices) {
            val selCandle = candles[selectedIndex]
            val selX = selectedIndex * stepX + stepX / 2f
            val selY = priceToY(selCandle.close)
            drawLine(
                color = Color.White.copy(alpha = 0.45f),
                start = Offset(selX, 0f),
                end = Offset(selX, plotHeight),
                strokeWidth = 1f
            )
            drawLine(
                color = Color.White.copy(alpha = 0.45f),
                start = Offset(0f, selY),
                end = Offset(plotWidth, selY),
                strokeWidth = 1f
            )
            drawCircle(
                color = CyanAccent,
                radius = 4.dp.toPx(),
                center = Offset(selX, selY)
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIndicatorLine(
    series: List<Double?>,
    stepX: Float,
    priceToY: (Double) -> Float,
    color: Color,
    strokeWidthPx: Float
) {
    if (series.isEmpty()) return
    val path = Path()
    var started = false
    series.forEachIndexed { idx, value ->
        if (value != null) {
            val x = idx * stepX + stepX / 2f
            val y = priceToY(value)
            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
        }
    }
    if (started) {
        drawPath(path = path, color = color, style = Stroke(width = strokeWidthPx))
    }
}

@Composable
private fun VolumeCanvas(
    candles: List<Candle>,
    selectedIndex: Int?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val rightAxisWidth = 68.dp.toPx()
        val plotWidth = (size.width - rightAxisWidth).coerceAtLeast(1f)
        val maxVol = candles.maxOfOrNull { it.volume }?.coerceAtLeast(1.0) ?: 1.0
        val stepX = plotWidth / candles.size.coerceAtLeast(1)
        val barW = (stepX * 0.65f).coerceIn(1.5.dp.toPx(), 12.dp.toPx())

        candles.forEachIndexed { idx, c ->
            val h = ((c.volume / maxVol).toFloat() * size.height).coerceAtLeast(1f)
            val x = idx * stepX + (stepX - barW) / 2f
            val alpha = if (selectedIndex == null || selectedIndex == idx) 0.55f else 0.25f
            val color = if (c.isBullish) BullGreen.copy(alpha = alpha) else BearRed.copy(alpha = alpha)
            drawRect(
                color = color,
                topLeft = Offset(x, size.height - h),
                size = Size(barW, h)
            )
        }
    }
}

@Composable
private fun RsiCanvas(
    rsiSeries: List<Double?>,
    selectedIndex: Int?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val rightAxisWidth = 68.dp.toPx()
        val plotWidth = (size.width - rightAxisWidth).coerceAtLeast(1f)
        val h = size.height

        fun rsiY(v: Double): Float = h - ((v.coerceIn(0.0, 100.0) / 100.0).toFloat() * h)

        val dash = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
        val y70 = rsiY(70.0)
        val y30 = rsiY(30.0)
        drawLine(BearRed.copy(alpha = 0.45f), Offset(0f, y70), Offset(plotWidth, y70), strokeWidth = 1f, pathEffect = dash)
        drawLine(BullGreen.copy(alpha = 0.45f), Offset(0f, y30), Offset(plotWidth, y30), strokeWidth = 1f, pathEffect = dash)

        val stepX = plotWidth / rsiSeries.size.coerceAtLeast(1)
        val path = Path()
        var started = false
        rsiSeries.forEachIndexed { idx, value ->
            if (value != null) {
                val x = idx * stepX + stepX / 2f
                val y = rsiY(value)
                if (!started) {
                    path.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }
            }
        }
        if (started) {
            drawPath(path, color = CyanAccent, style = Stroke(width = 1.5.dp.toPx()))
        }

        if (selectedIndex != null && selectedIndex in rsiSeries.indices) {
            rsiSeries[selectedIndex]?.let { v ->
                val x = selectedIndex * stepX + stepX / 2f
                drawCircle(CyanAccent, radius = 3.dp.toPx(), center = Offset(x, rsiY(v)))
            }
        }
    }
}

@Composable
private fun MacdCanvas(
    macdSeries: List<MacdPoint>,
    selectedIndex: Int?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val rightAxisWidth = 68.dp.toPx()
        val plotWidth = (size.width - rightAxisWidth).coerceAtLeast(1f)
        val h = size.height

        var maxAbs = 0.0001
        macdSeries.forEach { p ->
            p.macdLine?.let { maxAbs = max(maxAbs, abs(it)) }
            p.signalLine?.let { maxAbs = max(maxAbs, abs(it)) }
            p.histogram?.let { maxAbs = max(maxAbs, abs(it)) }
        }

        fun valToY(v: Double): Float {
            val normalized = (v / maxAbs).coerceIn(-1.0, 1.0)
            return (h / 2f) - (normalized.toFloat() * (h * 0.44f))
        }

        val zeroY = h / 2f
        drawLine(BorderSlate.copy(alpha = 0.6f), Offset(0f, zeroY), Offset(plotWidth, zeroY), 1f)

        val stepX = plotWidth / macdSeries.size.coerceAtLeast(1)
        val barW = (stepX * 0.6f).coerceIn(1.5.dp.toPx(), 10.dp.toPx())

        macdSeries.forEachIndexed { idx, pt ->
            pt.histogram?.let { hist ->
                val x = idx * stepX + (stepX - barW) / 2f
                val yVal = valToY(hist)
                val top = min(zeroY, yVal)
                val barH = abs(yVal - zeroY).coerceAtLeast(1f)
                val col = if (hist >= 0) BullGreen.copy(alpha = 0.65f) else BearRed.copy(alpha = 0.65f)
                drawRect(col, Offset(x, top), Size(barW, barH))
            }
        }

        val macdPath = Path()
        val signalPath = Path()
        var startedM = false
        var startedS = false
        macdSeries.forEachIndexed { idx, pt ->
            val x = idx * stepX + stepX / 2f
            pt.macdLine?.let { m ->
                val y = valToY(m)
                if (!startedM) { macdPath.moveTo(x, y); startedM = true } else macdPath.lineTo(x, y)
            }
            pt.signalLine?.let { s ->
                val y = valToY(s)
                if (!startedS) { signalPath.moveTo(x, y); startedS = true } else signalPath.lineTo(x, y)
            }
        }
        if (startedM) drawPath(macdPath, color = CyanAccent, style = Stroke(1.3.dp.toPx()))
        if (startedS) drawPath(signalPath, color = GoldAccent, style = Stroke(1.3.dp.toPx()))

        if (selectedIndex != null && selectedIndex in macdSeries.indices) {
            val selX = selectedIndex * stepX + stepX / 2f
            drawLine(Color.White.copy(alpha = 0.35f), Offset(selX, 0f), Offset(selX, h), 1f)
        }
    }
}
