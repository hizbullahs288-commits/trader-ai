package com.example

import com.example.domain.engine.AnalyticsEngine
import com.example.domain.engine.RiskCalculatorEngine
import com.example.domain.engine.TechnicalAnalysisEngine
import com.example.domain.model.AssetClass
import com.example.domain.model.Candle
import com.example.domain.model.JournalEntry
import com.example.domain.model.PaperPosition
import com.example.domain.model.RiskCalculationInput
import com.example.domain.model.SignalDirection
import com.example.domain.model.TradeSide
import com.example.domain.model.WatchlistDefaults
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TradingEngineUnitTest {

    private fun buildTrendCandles(
        count: Int,
        startPrice: Double,
        step: Double
    ): List<Candle> {
        return (0 until count).map { i ->
            val open = startPrice + (i * step)
            val close = open + step * 0.8
            val high = maxOf(open, close) + abs(step) * 0.5 + 0.5
            val low = minOf(open, close) - abs(step) * 0.5 - 0.5
            Candle(
                timestampMs = 1_700_000_000_000L + (i * 3_600_000L),
                open = open,
                high = high,
                low = low,
                close = close,
                volume = 1_000.0 + i * 25.0
            )
        }
    }

    @Test
    fun `technical analysis detects bullish trend, RSI overbought, EMA, MACD, and Bollinger`() {
        val uptrend = buildTrendCandles(count = 70, startPrice = 100.0, step = 1.2)
        val bundle = TechnicalAnalysisEngine.analyze(uptrend)

        assertNotNull(bundle.summary.ema20)
        assertNotNull(bundle.summary.ema50)
        assertTrue(bundle.summary.ema20!! > bundle.summary.ema50!!)
        assertNotNull(bundle.summary.rsi14)
        assertTrue("Expected high RSI in steady uptrend", bundle.summary.rsi14!! > 70.0)
        assertNotNull(bundle.summary.macd?.macdLine)
        assertTrue(bundle.summary.macd!!.macdLine!! > 0.0)
        val bb = bundle.summary.bollinger!!
        assertNotNull(bb.upper)
        assertNotNull(bb.lower)
        assertTrue(bb.upper!! > bb.lower!!)
        assertNotNull(bundle.summary.atr14)
        assertTrue(bundle.summary.atr14!! > 0.0)
    }

    @Test
    fun `technical analysis detects oversold RSI and bearish EMA alignment on downtrend`() {
        val downtrend = buildTrendCandles(count = 70, startPrice = 200.0, step = -1.3)
        val bundle = TechnicalAnalysisEngine.analyze(downtrend)

        assertTrue(bundle.summary.ema20!! < bundle.summary.ema50!!)
        assertTrue("Expected oversold RSI in steady downtrend", bundle.summary.rsi14!! < 30.0)
        assertTrue(bundle.summary.macd!!.macdLine!! < 0.0)
    }

    @Test
    fun `support and resistance detects pivot swing highs and lows`() {
        val oscillating = (0 until 60).map { i ->
            val wave = kotlin.math.sin(i * 0.45) * 10.0 + 100.0
            Candle(
                timestampMs = i * 60_000L,
                open = wave - 0.3,
                high = wave + 1.5,
                low = wave - 1.5,
                close = wave + 0.3,
                volume = 500.0
            )
        }
        val levels = TechnicalAnalysisEngine.detectSupportResistance(oscillating)
        assertTrue("Expected detected S/R levels", levels.isNotEmpty())
    }

    @Test
    fun `quantitative signal returns valid direction and stop loss targets`() {
        val candles = buildTrendCandles(count = 65, startPrice = 50.0, step = 0.5)
        val bundle = TechnicalAnalysisEngine.analyze(candles)
        val sig = bundle.quantitativeSignal

        assertTrue(sig.direction in SignalDirection.entries)
        assertTrue(sig.confidencePercent in 0..100)
        assertTrue(sig.suggestedStopLoss > 0.0)
        assertTrue(sig.suggestedTakeProfit > 0.0)
        assertTrue(sig.reasons.isNotEmpty())
    }

    @Test
    fun `risk calculator computes exact long sizing, risk amount, R_R, and forex lots`() {
        val res = RiskCalculatorEngine.calculate(
            RiskCalculationInput(
                accountBalance = 100_000.0,
                riskPercent = 1.0,
                entryPrice = 1.1000,
                stopLossPrice = 1.0950,
                takeProfitPrice = 1.1150,
                assetClass = AssetClass.FOREX,
                symbol = "EURUSD=X"
            )
        )

        assertTrue(res.isValid)
        assertEquals(TradeSide.LONG, res.side)
        assertEquals(1_000.0, res.riskAmountUsd, 0.01)
        assertEquals(0.005, res.stopDistancePrice, 0.0001)
        assertEquals(200_000.0, res.positionUnits, 1.0)
        assertEquals(2.0, res.forexStandardLots!!, 0.01)
        assertEquals(3.0, res.riskRewardRatio, 0.01)
        assertEquals(25.0, res.breakEvenWinRatePercent, 0.1)
    }

    @Test
    fun `risk calculator rejects invalid stop loss equal to entry`() {
        val res = RiskCalculatorEngine.calculate(
            RiskCalculationInput(
                accountBalance = 50_000.0,
                riskPercent = 1.0,
                entryPrice = 100.0,
                stopLossPrice = 100.0
            )
        )
        assertFalse(res.isValid)
        assertNotNull(res.validationError)
    }

    @Test
    fun `paper position computes long and short unrealized PnL accurately`() {
        val longPos = PaperPosition(
            symbol = "BTC-USD",
            side = TradeSide.LONG.name,
            quantity = 2.0,
            entryPrice = 90_000.0
        )
        assertEquals(4_000.0, longPos.computeUnrealizedPnl(92_000.0), 0.01)

        val shortPos = PaperPosition(
            symbol = "GC=F",
            side = TradeSide.SHORT.name,
            quantity = 10.0,
            entryPrice = 2_650.0
        )
        assertEquals(500.0, shortPos.computeUnrealizedPnl(2_600.0), 0.01)
    }

    @Test
    fun `analytics engine computes win rate, profit factor, and equity curve`() {
        val entries = listOf(
            JournalEntry(
                id = "1",
                symbol = "AAPL",
                pnlAmount = 500.0,
                strategyTag = "Breakout",
                emotionTag = "Disciplined",
                createdAt = Timestamp(1000, 0)
            ),
            JournalEntry(
                id = "2",
                symbol = "EURUSD=X",
                pnlAmount = -200.0,
                strategyTag = "EMA Pullback",
                emotionTag = "FOMO",
                createdAt = Timestamp(2000, 0)
            ),
            JournalEntry(
                id = "3",
                symbol = "BTC-USD",
                pnlAmount = 700.0,
                strategyTag = "Breakout",
                emotionTag = "Disciplined",
                createdAt = Timestamp(3000, 0)
            )
        )

        val analytics = AnalyticsEngine.computeJournalAnalytics(entries, initialCapital = 100_000.0)
        assertEquals(3, analytics.totalEntries)
        assertEquals(2, analytics.wins)
        assertEquals(1, analytics.losses)
        assertEquals(66.67, analytics.winRatePercent, 0.1)
        assertEquals(1000.0, analytics.netRealizedPnl, 0.01)
        assertEquals(6.0, analytics.profitFactor, 0.01)
        assertEquals(4, analytics.equityCurve.size)
        assertEquals(101_000.0, analytics.equityCurve.last().equity, 0.01)
        assertEquals("Breakout", analytics.strategyBreakdown.first().tag)
    }

    @Test
    fun `watchlist defaults cover all four asset classes`() {
        val classes = WatchlistDefaults.instruments.map { it.assetClass }.toSet()
        assertTrue(AssetClass.CRYPTO in classes)
        assertTrue(AssetClass.FOREX in classes)
        assertTrue(AssetClass.STOCKS in classes)
        assertTrue(AssetClass.GOLD in classes)
    }
}
