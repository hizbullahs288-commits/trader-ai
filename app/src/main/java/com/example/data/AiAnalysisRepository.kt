package com.example.data

import android.util.Log
import com.example.domain.model.AiAnalysisResult
import com.example.domain.model.Candle
import com.example.domain.model.MarketInstrument
import com.example.domain.model.SignalDirection
import com.example.domain.model.TechnicalAnalysisBundle
import com.example.domain.model.Timeframe
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale

private const val TAG = "AiAnalysisRepo"

class AiAnalysisRepository {

    private val analysisSchema = Schema.obj(
        properties = mapOf(
            "signal" to Schema.enumeration(
                values = listOf("BUY", "SELL", "HOLD"),
                description = "Recommended trading bias based strictly on the provided live indicators."
            ),
            "confidence" to Schema.integer(
                description = "Confidence score from 0 to 100 reflecting indicator confluence."
            ),
            "headline" to Schema.string(
                description = "Concise 1-sentence technical thesis headline."
            ),
            "summary" to Schema.string(
                description = "2-3 sentence executive summary of trend, momentum, and volatility."
            ),
            "keyDrivers" to Schema.array(
                items = Schema.string(),
                description = "3 to 5 bullet points citing exact RSI, MACD, EMA, Bollinger, and S/R readings."
            ),
            "entryZone" to Schema.string(
                description = "Suggested price zone or condition for entry."
            ),
            "stopLoss" to Schema.double(
                description = "Suggested technical invalidation stop-loss price."
            ),
            "takeProfit" to Schema.double(
                description = "Suggested primary target take-profit price."
            ),
            "riskRewardNote" to Schema.string(
                description = "Short explanation of risk-to-reward profile."
            ),
            "invalidationCondition" to Schema.string(
                description = "Specific price/indicator condition that invalidates this thesis."
            )
        )
    )

    private val structuredModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = analysisSchema
                temperature = 0.2f
            }
        )
    }

    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.35f
            }
        )
    }

    suspend fun analyzeMarketSetup(
        instrument: MarketInstrument,
        timeframe: Timeframe,
        candles: List<Candle>,
        bundle: TechnicalAnalysisBundle
    ): Result<AiAnalysisResult> {
        val prompt = buildTechnicalPrompt(instrument, timeframe, candles, bundle)
        return try {
            val response = withTimeout(60_000L) {
                structuredModel.generateContent(prompt)
            }
            val jsonText = response.text
                ?: return Result.failure(IllegalStateException("Gemini returned an empty response."))

            val json = JSONObject(jsonText)
            val driversArr = json.optJSONArray("keyDrivers")
            val drivers = if (driversArr != null) {
                (0 until driversArr.length()).map { driversArr.getString(it) }
            } else emptyList()

            val result = AiAnalysisResult(
                symbol = instrument.symbol,
                timeframeLabel = timeframe.label,
                signal = SignalDirection.fromRaw(json.optString("signal", "HOLD")),
                confidence = json.optInt("confidence", 50).coerceIn(0, 100),
                headline = json.optString("headline", "Technical Analysis Complete"),
                summary = json.optString("summary", ""),
                keyDrivers = drivers,
                entryZone = json.optString("entryZone", fmtPrice(bundle.summary.latestPrice, instrument.priceDecimals)),
                stopLoss = json.optDouble("stopLoss", bundle.quantitativeSignal.suggestedStopLoss),
                takeProfit = json.optDouble("takeProfit", bundle.quantitativeSignal.suggestedTakeProfit),
                riskRewardNote = json.optString("riskRewardNote", ""),
                invalidationCondition = json.optString("invalidationCondition", "")
            )
            Result.success(result)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI technical analysis timed out", e)
            Result.failure(IllegalStateException("AI analysis timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI technical analysis failed", e)
            Result.failure(e)
        }
    }

    fun askFollowUpStream(
        instrument: MarketInstrument,
        timeframe: Timeframe,
        bundle: TechnicalAnalysisBundle,
        question: String
    ): Flow<String> = flow {
        val contextPrompt = buildString {
            appendLine("You are an institutional technical analyst. Answer clearly and concisely using ONLY the live telemetry below. Remind the user this is educational analysis, not financial advice.")
            appendLine("Instrument: ${instrument.displayName} (${instrument.symbol}) | Timeframe: ${timeframe.label}")
            appendLine("Live Price: ${fmtPrice(bundle.summary.latestPrice, instrument.priceDecimals)}")
            appendLine("RSI(14): ${bundle.summary.rsi14?.let { String.format(Locale.US, "%.2f", it) } ?: "N/A"}")
            appendLine("EMA20: ${bundle.summary.ema20?.let { fmtPrice(it, instrument.priceDecimals) } ?: "N/A"}")
            appendLine("EMA50: ${bundle.summary.ema50?.let { fmtPrice(it, instrument.priceDecimals) } ?: "N/A"}")
            appendLine("MACD Hist: ${bundle.summary.macd?.histogram?.let { String.format(Locale.US, "%.4f", it) } ?: "N/A"}")
            appendLine("Support: ${bundle.summary.nearestSupport?.let { fmtPrice(it, instrument.priceDecimals) } ?: "N/A"} | Resistance: ${bundle.summary.nearestResistance?.let { fmtPrice(it, instrument.priceDecimals) } ?: "N/A"}")
            appendLine("Trader Question: $question")
        }

        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            chatModel.generateContentStream(contextPrompt).collect { chunk ->
                val text = chunk.text.orEmpty()
                if (text.isNotEmpty()) {
                    accumulated.append(text)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("AI returned an empty response.")
        }
    }

    private fun buildTechnicalPrompt(
        instrument: MarketInstrument,
        timeframe: Timeframe,
        candles: List<Candle>,
        bundle: TechnicalAnalysisBundle
    ): String {
        val s = bundle.summary
        val d = instrument.priceDecimals
        val recentCandles = candles.takeLast(12).joinToString("\n") { c ->
            "O=${fmtPrice(c.open, d)} H=${fmtPrice(c.high, d)} L=${fmtPrice(c.low, d)} C=${fmtPrice(c.close, d)} V=${c.volume.toLong()}"
        }
        val levels = bundle.supportResistanceLevels.joinToString("; ") {
            "${it.type.name}: ${fmtPrice(it.price, d)} (${it.touches} touches)"
        }.ifBlank { "None detected" }

        return """
            Perform an institutional-grade technical analysis using strictly these LIVE market metrics for ${instrument.displayName} (${instrument.symbol}, ${instrument.assetClass.label}) on the ${timeframe.label} timeframe. Never invent prices or indicators outside these figures.

            LIVE PRICE & INDICATORS:
            - Latest Close: ${fmtPrice(s.latestPrice, d)}
            - EMA(9): ${s.ema9?.let { fmtPrice(it, d) } ?: "N/A"}
            - EMA(20): ${s.ema20?.let { fmtPrice(it, d) } ?: "N/A"}
            - EMA(50): ${s.ema50?.let { fmtPrice(it, d) } ?: "N/A"}
            - EMA(200): ${s.ema200?.let { fmtPrice(it, d) } ?: "N/A"}
            - RSI(14): ${s.rsi14?.let { String.format(Locale.US, "%.2f", it) } ?: "N/A"}
            - MACD Line: ${s.macd?.macdLine?.let { String.format(Locale.US, "%.5f", it) } ?: "N/A"} | Signal: ${s.macd?.signalLine?.let { String.format(Locale.US, "%.5f", it) } ?: "N/A"} | Histogram: ${s.macd?.histogram?.let { String.format(Locale.US, "%.5f", it) } ?: "N/A"}
            - Bollinger Bands(20,2): Upper=${s.bollinger?.upper?.let { fmtPrice(it, d) } ?: "N/A"}, Mid=${s.bollinger?.middle?.let { fmtPrice(it, d) } ?: "N/A"}, Lower=${s.bollinger?.lower?.let { fmtPrice(it, d) } ?: "N/A"}, %B=${s.bollingerPercentB?.let { String.format(Locale.US, "%.2f", it) } ?: "N/A"}
            - ATR(14): ${s.atr14?.let { fmtPrice(it, d) } ?: "N/A"}
            - Support & Resistance Levels: $levels
            - Quantitative Baseline Score: ${bundle.quantitativeSignal.score}/100 (${bundle.quantitativeSignal.direction.name})

            LAST 12 CANDLES (oldest to newest):
            $recentCandles

            Provide a clear BUY, SELL, or HOLD recommendation, realistic stop-loss and take-profit prices grounded in ATR and support/resistance, and crisp technical explanations.
        """.trimIndent()
    }

    private fun fmtPrice(value: Double, decimals: Int): String =
        String.format(Locale.US, "%.${decimals}f", value)
}
