package com.example.domain.model

enum class SignalDirection {
    BUY,
    SELL,
    HOLD;

    companion object {
        fun fromRaw(raw: String?): SignalDirection =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: HOLD
    }
}

data class MacdPoint(
    val macdLine: Double?,
    val signalLine: Double?,
    val histogram: Double?
)

data class BollingerPoint(
    val upper: Double?,
    val middle: Double?,
    val lower: Double?
)

enum class LevelType { SUPPORT, RESISTANCE }

data class PriceLevel(
    val price: Double,
    val type: LevelType,
    val touches: Int
)

data class QuantitativeSignal(
    val direction: SignalDirection,
    val confidencePercent: Int,
    val score: Int, // -100 to +100
    val suggestedEntry: Double,
    val suggestedStopLoss: Double,
    val suggestedTakeProfit: Double,
    val riskRewardRatio: Double,
    val reasons: List<String>
)

data class IndicatorSummary(
    val latestPrice: Double,
    val ema9: Double?,
    val ema20: Double?,
    val ema50: Double?,
    val ema200: Double?,
    val rsi14: Double?,
    val macd: MacdPoint?,
    val bollinger: BollingerPoint?,
    val bollingerPercentB: Double?,
    val atr14: Double?,
    val nearestSupport: Double?,
    val nearestResistance: Double?
)

data class TechnicalAnalysisBundle(
    val ema20Series: List<Double?>,
    val ema50Series: List<Double?>,
    val rsi14Series: List<Double?>,
    val macdSeries: List<MacdPoint>,
    val bollingerSeries: List<BollingerPoint>,
    val supportResistanceLevels: List<PriceLevel>,
    val summary: IndicatorSummary,
    val quantitativeSignal: QuantitativeSignal
)

data class AiAnalysisResult(
    val symbol: String,
    val timeframeLabel: String,
    val signal: SignalDirection,
    val confidence: Int,
    val headline: String,
    val summary: String,
    val keyDrivers: List<String>,
    val entryZone: String,
    val stopLoss: Double,
    val takeProfit: Double,
    val riskRewardNote: String,
    val invalidationCondition: String,
    val generatedAtMs: Long = System.currentTimeMillis()
)
