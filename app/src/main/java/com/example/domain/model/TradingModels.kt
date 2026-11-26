package com.example.domain.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TradeSide(val label: String) {
    LONG("Long (Buy)"),
    SHORT("Short (Sell)");

    companion object {
        fun fromRaw(raw: String?): TradeSide =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: LONG
    }
}

enum class PositionStatus {
    OPEN,
    CLOSED;

    companion object {
        fun fromRaw(raw: String?): PositionStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: OPEN
    }
}

@Keep
@IgnoreExtraProperties
data class UserAccount(
    val userId: String = "",
    val virtualBalance: Double = 100_000.0,
    val initialCapital: Double = 100_000.0,
    val realizedPnl: Double = 0.0,
    val totalTrades: Long = 0L,
    val winningTrades: Long = 0L,
    val defaultRiskPercent: Double = 1.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PaperPosition(
    @DocumentId val id: String = "",
    val userId: String = "",
    val symbol: String = "",
    val displayName: String = "",
    val assetClass: String = AssetClass.CRYPTO.name,
    val side: String = TradeSide.LONG.name,
    val status: String = PositionStatus.OPEN.name,
    val quantity: Double = 0.0,
    val entryPrice: Double = 0.0,
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
    val exitPrice: Double? = null,
    val realizedPnl: Double? = null,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    @ServerTimestamp val closedAt: Timestamp? = null
) {
    val parsedAssetClass: AssetClass get() = AssetClass.fromRaw(assetClass)
    val parsedSide: TradeSide get() = TradeSide.fromRaw(side)
    val parsedStatus: PositionStatus get() = PositionStatus.fromRaw(status)

    fun computeUnrealizedPnl(currentPrice: Double): Double {
        if (parsedStatus == PositionStatus.CLOSED) return realizedPnl ?: 0.0
        return when (parsedSide) {
            TradeSide.LONG -> (currentPrice - entryPrice) * quantity
            TradeSide.SHORT -> (entryPrice - currentPrice) * quantity
        }
    }

    fun computeUnrealizedPercent(currentPrice: Double): Double {
        if (entryPrice <= 0.0) return 0.0
        val diff = when (parsedSide) {
            TradeSide.LONG -> (currentPrice - entryPrice) / entryPrice
            TradeSide.SHORT -> (entryPrice - currentPrice) / entryPrice
        }
        return diff * 100.0
    }
}

@Keep
@IgnoreExtraProperties
data class JournalEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val symbol: String = "",
    val assetClass: String = AssetClass.CRYPTO.name,
    val side: String = TradeSide.LONG.name,
    val entryPrice: Double = 0.0,
    val exitPrice: Double = 0.0,
    val quantity: Double = 0.0,
    val pnlAmount: Double = 0.0,
    val pnlPercent: Double = 0.0,
    val strategyTag: String = "Trend Following",
    val emotionTag: String = "Disciplined",
    val notes: String = "",
    val linkedPositionId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedAssetClass: AssetClass get() = AssetClass.fromRaw(assetClass)
    val parsedSide: TradeSide get() = TradeSide.fromRaw(side)
}

data class RiskCalculationInput(
    val accountBalance: Double = 100_000.0,
    val riskPercent: Double = 1.0,
    val entryPrice: Double = 0.0,
    val stopLossPrice: Double = 0.0,
    val takeProfitPrice: Double = 0.0,
    val assetClass: AssetClass = AssetClass.CRYPTO,
    val symbol: String = "BTC-USD"
)

data class RiskCalculationResult(
    val isValid: Boolean,
    val validationError: String? = null,
    val side: TradeSide = TradeSide.LONG,
    val riskAmountUsd: Double = 0.0,
    val stopDistancePrice: Double = 0.0,
    val stopDistancePercent: Double = 0.0,
    val positionUnits: Double = 0.0,
    val forexStandardLots: Double? = null,
    val notionalValueUsd: Double = 0.0,
    val accountAllocationPercent: Double = 0.0,
    val potentialRewardUsd: Double = 0.0,
    val riskRewardRatio: Double = 0.0,
    val breakEvenWinRatePercent: Double = 0.0,
    val warnings: List<String> = emptyList()
)

data class EquityCurvePoint(
    val label: String,
    val equity: Double
)

data class TagPerformance(
    val tag: String,
    val trades: Int,
    val winRatePercent: Double,
    val totalPnl: Double
)

data class PortfolioAnalytics(
    val totalEntries: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val winRatePercent: Double = 0.0,
    val netRealizedPnl: Double = 0.0,
    val grossProfit: Double = 0.0,
    val grossLoss: Double = 0.0,
    val profitFactor: Double = 0.0,
    val averageWin: Double = 0.0,
    val averageLoss: Double = 0.0,
    val bestTradePnl: Double = 0.0,
    val worstTradePnl: Double = 0.0,
    val equityCurve: List<EquityCurvePoint> = emptyList(),
    val strategyBreakdown: List<TagPerformance> = emptyList(),
    val emotionBreakdown: List<TagPerformance> = emptyList()
)
