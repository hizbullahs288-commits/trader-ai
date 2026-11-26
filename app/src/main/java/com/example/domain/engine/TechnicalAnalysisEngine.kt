package com.example.domain.engine

import com.example.domain.model.BollingerPoint
import com.example.domain.model.Candle
import com.example.domain.model.IndicatorSummary
import com.example.domain.model.LevelType
import com.example.domain.model.MacdPoint
import com.example.domain.model.PriceLevel
import com.example.domain.model.QuantitativeSignal
import com.example.domain.model.SignalDirection
import com.example.domain.model.TechnicalAnalysisBundle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

object TechnicalAnalysisEngine {

    fun analyze(candles: List<Candle>): TechnicalAnalysisBundle {
        if (candles.isEmpty()) {
            return TechnicalAnalysisBundle(
                ema20Series = emptyList(),
                ema50Series = emptyList(),
                rsi14Series = emptyList(),
                macdSeries = emptyList(),
                bollingerSeries = emptyList(),
                supportResistanceLevels = emptyList(),
                summary = IndicatorSummary(
                    latestPrice = 0.0,
                    ema9 = null,
                    ema20 = null,
                    ema50 = null,
                    ema200 = null,
                    rsi14 = null,
                    macd = null,
                    bollinger = null,
                    bollingerPercentB = null,
                    atr14 = null,
                    nearestSupport = null,
                    nearestResistance = null
                ),
                quantitativeSignal = QuantitativeSignal(
                    direction = SignalDirection.HOLD,
                    confidencePercent = 0,
                    score = 0,
                    suggestedEntry = 0.0,
                    suggestedStopLoss = 0.0,
                    suggestedTakeProfit = 0.0,
                    riskRewardRatio = 0.0,
                    reasons = listOf("Insufficient candle history for technical analysis.")
                )
            )
        }

        val closes = candles.map { it.close }
        val ema9Series = calculateEma(closes, 9)
        val ema20Series = calculateEma(closes, 20)
        val ema50Series = calculateEma(closes, 50)
        val ema200Series = calculateEma(closes, 200)
        val rsi14Series = calculateRsi(closes, 14)
        val macdSeries = calculateMacd(closes, fastPeriod = 12, slowPeriod = 26, signalPeriod = 9)
        val bollingerSeries = calculateBollingerBands(closes, period = 20, stdDevMultiplier = 2.0)
        val atr14 = calculateAtr(candles, period = 14)
        val srLevels = detectSupportResistance(candles)

        val latestPrice = closes.last()
        val latestEma9 = ema9Series.lastOrNull()
        val latestEma20 = ema20Series.lastOrNull()
        val latestEma50 = ema50Series.lastOrNull()
        val latestEma200 = ema200Series.lastOrNull()
        val latestRsi = rsi14Series.lastOrNull()
        val latestMacd = macdSeries.lastOrNull()
        val prevMacd = if (macdSeries.size >= 2) macdSeries[macdSeries.size - 2] else null
        val latestBb = bollingerSeries.lastOrNull()

        val percentB = if (latestBb?.upper != null && latestBb.lower != null && (latestBb.upper - latestBb.lower) > 0.0) {
            (latestPrice - latestBb.lower) / (latestBb.upper - latestBb.lower)
        } else null

        val nearestSupport = srLevels
            .filter { it.type == LevelType.SUPPORT && it.price <= latestPrice }
            .maxByOrNull { it.price }?.price
            ?: srLevels.filter { it.price < latestPrice }.maxByOrNull { it.price }?.price

        val nearestResistance = srLevels
            .filter { it.type == LevelType.RESISTANCE && it.price >= latestPrice }
            .minByOrNull { it.price }?.price
            ?: srLevels.filter { it.price > latestPrice }.minByOrNull { it.price }?.price

        val summary = IndicatorSummary(
            latestPrice = latestPrice,
            ema9 = latestEma9,
            ema20 = latestEma20,
            ema50 = latestEma50,
            ema200 = latestEma200,
            rsi14 = latestRsi,
            macd = latestMacd,
            bollinger = latestBb,
            bollingerPercentB = percentB,
            atr14 = atr14,
            nearestSupport = nearestSupport,
            nearestResistance = nearestResistance
        )

        val quantSignal = evaluateQuantitativeSignal(summary, prevMacd)

        return TechnicalAnalysisBundle(
            ema20Series = ema20Series,
            ema50Series = ema50Series,
            rsi14Series = rsi14Series,
            macdSeries = macdSeries,
            bollingerSeries = bollingerSeries,
            supportResistanceLevels = srLevels,
            summary = summary,
            quantitativeSignal = quantSignal
        )
    }

    fun calculateEma(values: List<Double>, period: Int): List<Double?> {
        if (values.isEmpty() || period <= 0) return emptyList()
        val result = MutableList<Double?>(values.size) { null }
        if (values.size < period) return result

        val sma = values.take(period).average()
        result[period - 1] = sma
        val multiplier = 2.0 / (period + 1.0)
        var prevEma = sma
        for (i in period until values.size) {
            val ema = (values[i] - prevEma) * multiplier + prevEma
            result[i] = ema
            prevEma = ema
        }
        return result
    }

    fun calculateRsi(closes: List<Double>, period: Int = 14): List<Double?> {
        if (closes.isEmpty() || period <= 0) return emptyList()
        val result = MutableList<Double?>(closes.size) { null }
        if (closes.size <= period) return result

        var gainSum = 0.0
        var lossSum = 0.0
        for (i in 1..period) {
            val diff = closes[i] - closes[i - 1]
            if (diff >= 0) gainSum += diff else lossSum += abs(diff)
        }

        var avgGain = gainSum / period
        var avgLoss = lossSum / period
        result[period] = computeRsiValue(avgGain, avgLoss)

        for (i in (period + 1) until closes.size) {
            val diff = closes[i] - closes[i - 1]
            val gain = if (diff > 0) diff else 0.0
            val loss = if (diff < 0) abs(diff) else 0.0
            avgGain = ((avgGain * (period - 1)) + gain) / period
            avgLoss = ((avgLoss * (period - 1)) + loss) / period
            result[i] = computeRsiValue(avgGain, avgLoss)
        }
        return result
    }

    private fun computeRsiValue(avgGain: Double, avgLoss: Double): Double {
        if (avgLoss == 0.0 && avgGain == 0.0) return 50.0
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    fun calculateMacd(
        closes: List<Double>,
        fastPeriod: Int = 12,
        slowPeriod: Int = 26,
        signalPeriod: Int = 9
    ): List<MacdPoint> {
        if (closes.isEmpty()) return emptyList()
        val fastEma = calculateEma(closes, fastPeriod)
        val slowEma = calculateEma(closes, slowPeriod)

        val macdValues = closes.indices.map { idx ->
            val f = fastEma[idx]
            val s = slowEma[idx]
            if (f != null && s != null) f - s else null
        }

        val validMacdPairs = macdValues.mapIndexedNotNull { idx, v -> if (v != null) idx to v else null }
        val signalMap = mutableMapOf<Int, Double>()
        if (validMacdPairs.size >= signalPeriod) {
            val signalEma = calculateEma(validMacdPairs.map { it.second }, signalPeriod)
            validMacdPairs.forEachIndexed { listIdx, (origIdx, _) ->
                signalEma[listIdx]?.let { signalMap[origIdx] = it }
            }
        }

        return closes.indices.map { idx ->
            val m = macdValues[idx]
            val sig = signalMap[idx]
            val hist = if (m != null && sig != null) m - sig else null
            MacdPoint(macdLine = m, signalLine = sig, histogram = hist)
        }
    }

    fun calculateBollingerBands(
        closes: List<Double>,
        period: Int = 20,
        stdDevMultiplier: Double = 2.0
    ): List<BollingerPoint> {
        if (closes.isEmpty() || period <= 0) return emptyList()
        return closes.indices.map { i ->
            if (i < period - 1) {
                BollingerPoint(null, null, null)
            } else {
                val window = closes.subList(i - period + 1, i + 1)
                val mean = window.average()
                val variance = window.sumOf { (it - mean).pow(2) } / period
                val stdDev = sqrt(variance)
                BollingerPoint(
                    upper = mean + stdDevMultiplier * stdDev,
                    middle = mean,
                    lower = mean - stdDevMultiplier * stdDev
                )
            }
        }
    }

    fun calculateAtr(candles: List<Candle>, period: Int = 14): Double? {
        if (candles.size <= period) return null
        val trueRanges = mutableListOf<Double>()
        for (i in 1 until candles.size) {
            val curr = candles[i]
            val prevClose = candles[i - 1].close
            val tr = maxOf(
                curr.high - curr.low,
                abs(curr.high - prevClose),
                abs(curr.low - prevClose)
            )
            trueRanges.add(tr)
        }
        if (trueRanges.size < period) return null
        var atr = trueRanges.take(period).average()
        for (i in period until trueRanges.size) {
            atr = ((atr * (period - 1)) + trueRanges[i]) / period
        }
        return atr
    }

    fun detectSupportResistance(
        candles: List<Candle>,
        swingWindow: Int = 3,
        maxLevelsPerSide: Int = 2
    ): List<PriceLevel> {
        if (candles.size < swingWindow * 2 + 1) return emptyList()
        val latestPrice = candles.last().close
        val avgRange = candles.takeLast(30).map { it.high - it.low }.average().coerceAtLeast(latestPrice * 0.002)
        val clusterTolerance = max(avgRange * 0.85, latestPrice * 0.004)

        val swingHighs = mutableListOf<Double>()
        val swingLows = mutableListOf<Double>()

        for (i in swingWindow until candles.size - swingWindow) {
            val current = candles[i]
            val left = candles.subList(i - swingWindow, i)
            val right = candles.subList(i + 1, i + 1 + swingWindow)
            if (left.all { current.high >= it.high } && right.all { current.high >= it.high }) {
                swingHighs.add(current.high)
            }
            if (left.all { current.low <= it.low } && right.all { current.low <= it.low }) {
                swingLows.add(current.low)
            }
        }

        val supportLevels = clusterLevels(swingLows, clusterTolerance, LevelType.SUPPORT)
            .filter { it.price <= latestPrice * 1.005 }
            .sortedWith(compareByDescending<PriceLevel> { it.touches }.thenByDescending { it.price })
            .take(maxLevelsPerSide)

        val resistanceLevels = clusterLevels(swingHighs, clusterTolerance, LevelType.RESISTANCE)
            .filter { it.price >= latestPrice * 0.995 }
            .sortedWith(compareByDescending<PriceLevel> { it.touches }.thenBy { it.price })
            .take(maxLevelsPerSide)

        return (supportLevels + resistanceLevels).sortedByDescending { it.price }
    }

    private fun clusterLevels(
        rawLevels: List<Double>,
        tolerance: Double,
        type: LevelType
    ): List<PriceLevel> {
        if (rawLevels.isEmpty()) return emptyList()
        val sorted = rawLevels.sorted()
        val clusters = mutableListOf<MutableList<Double>>()
        for (lvl in sorted) {
            val matching = clusters.lastOrNull()
            if (matching != null && abs(lvl - matching.average()) <= tolerance) {
                matching.add(lvl)
            } else {
                clusters.add(mutableListOf(lvl))
            }
        }
        return clusters.map { cluster ->
            PriceLevel(price = cluster.average(), type = type, touches = cluster.size)
        }
    }

    private fun evaluateQuantitativeSignal(
        summary: IndicatorSummary,
        prevMacd: MacdPoint?
    ): QuantitativeSignal {
        var score = 0
        val reasons = mutableListOf<String>()
        val price = summary.latestPrice

        // 1. EMA Trend Structure
        val ema20 = summary.ema20
        val ema50 = summary.ema50
        if (ema20 != null && ema50 != null) {
            if (price > ema20 && ema20 > ema50) {
                score += 25
                reasons += "Bullish EMA alignment: price above rising EMA 20 & EMA 50."
            } else if (price < ema20 && ema20 < ema50) {
                score -= 25
                reasons += "Bearish EMA alignment: price below falling EMA 20 & EMA 50."
            } else if (price > ema20) {
                score += 10
                reasons += "Price holds above short-term EMA 20."
            } else if (price < ema20) {
                score -= 10
                reasons += "Price trades below short-term EMA 20."
            }
        }

        // 2. RSI Momentum
        val rsi = summary.rsi14
        if (rsi != null) {
            when {
                rsi <= 30.0 -> {
                    score += 25
                    reasons += "RSI (${rsi.roundToInt()}) is oversold (<=30), signaling potential mean reversion up."
                }
                rsi in 30.0..45.0 -> {
                    score += 10
                    reasons += "RSI (${rsi.roundToInt()}) sits in recovery zone with upside room."
                }
                rsi in 55.0..69.9 -> {
                    score += 12
                    reasons += "RSI (${rsi.roundToInt()}) shows constructive bullish momentum."
                }
                rsi >= 70.0 -> {
                    score -= 25
                    reasons += "RSI (${rsi.roundToInt()}) is overbought (>=70), raising pullback risk."
                }
                else -> {
                    reasons += "RSI (${rsi.roundToInt()}) is neutral near equilibrium."
                }
            }
        }

        // 3. MACD Momentum & Crossovers
        val macd = summary.macd
        if (macd?.macdLine != null && macd.signalLine != null && macd.histogram != null) {
            val prevHist = prevMacd?.histogram
            if (prevHist != null && prevHist <= 0.0 && macd.histogram > 0.0) {
                score += 25
                reasons += "Fresh bullish MACD crossover (histogram turned positive)."
            } else if (prevHist != null && prevHist >= 0.0 && macd.histogram < 0.0) {
                score -= 25
                reasons += "Fresh bearish MACD crossover (histogram turned negative)."
            } else if (macd.macdLine > macd.signalLine) {
                score += 15
                reasons += "MACD line remains above signal line."
            } else {
                score -= 15
                reasons += "MACD line remains below signal line."
            }
        }

        // 4. Bollinger Bands %B
        val pctB = summary.bollingerPercentB
        if (pctB != null) {
            when {
                pctB <= 0.10 -> {
                    score += 15
                    reasons += "Price testing lower Bollinger Band (%B ${(pctB * 100).roundToInt()}%)."
                }
                pctB >= 0.90 -> {
                    score -= 15
                    reasons += "Price extended near upper Bollinger Band (%B ${(pctB * 100).roundToInt()}%)."
                }
            }
        }

        val clampedScore = score.coerceIn(-100, 100)
        val direction = when {
            clampedScore >= 25 -> SignalDirection.BUY
            clampedScore <= -25 -> SignalDirection.SELL
            else -> SignalDirection.HOLD
        }

        val confidence = when (direction) {
            SignalDirection.HOLD -> (55 - abs(clampedScore)).coerceIn(35, 65)
            else -> (50 + abs(clampedScore) / 2).coerceIn(55, 94)
        }

        val atr = (summary.atr14 ?: (price * 0.015)).coerceAtLeast(price * 0.002)
        val (sl, tp) = when (direction) {
            SignalDirection.SELL -> {
                val stop = price + (atr * 1.5)
                val target = (price - (atr * 3.0)).coerceAtLeast(price * 0.1)
                stop to target
            }
            else -> {
                val stop = (price - (atr * 1.5)).coerceAtLeast(price * 0.1)
                val target = price + (atr * 3.0)
                stop to target
            }
        }
        val riskDist = abs(price - sl)
        val rewardDist = abs(tp - price)
        val rr = if (riskDist > 0.0) rewardDist / riskDist else 0.0

        return QuantitativeSignal(
            direction = direction,
            confidencePercent = confidence,
            score = clampedScore,
            suggestedEntry = price,
            suggestedStopLoss = sl,
            suggestedTakeProfit = tp,
            riskRewardRatio = rr,
            reasons = if (reasons.isEmpty()) listOf("Mixed indicator readings across timeframes.") else reasons
        )
    }
}
