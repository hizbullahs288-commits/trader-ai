package com.example.domain.engine

import com.example.domain.model.EquityCurvePoint
import com.example.domain.model.JournalEntry
import com.example.domain.model.PortfolioAnalytics
import com.example.domain.model.TagPerformance
import kotlin.math.abs

object AnalyticsEngine {

    fun computeJournalAnalytics(
        entries: List<JournalEntry>,
        initialCapital: Double = 100_000.0
    ): PortfolioAnalytics {
        if (entries.isEmpty()) {
            return PortfolioAnalytics(
                equityCurve = listOf(EquityCurvePoint("Start", initialCapital))
            )
        }

        val chronological = entries.sortedBy { it.createdAt?.seconds ?: 0L }
        val wins = chronological.filter { it.pnlAmount > 0.0 }
        val losses = chronological.filter { it.pnlAmount < 0.0 }

        val grossProfit = wins.sumOf { it.pnlAmount }
        val grossLoss = abs(losses.sumOf { it.pnlAmount })
        val netPnl = chronological.sumOf { it.pnlAmount }
        val winRate = (wins.size.toDouble() / chronological.size.toDouble()) * 100.0
        val profitFactor = when {
            grossLoss > 0.0 -> grossProfit / grossLoss
            grossProfit > 0.0 -> 99.9
            else -> 0.0
        }
        val avgWin = if (wins.isNotEmpty()) grossProfit / wins.size else 0.0
        val avgLoss = if (losses.isNotEmpty()) grossLoss / losses.size else 0.0
        val bestTrade = chronological.maxOfOrNull { it.pnlAmount } ?: 0.0
        val worstTrade = chronological.minOfOrNull { it.pnlAmount } ?: 0.0

        val curve = mutableListOf(EquityCurvePoint("Start", initialCapital))
        var runningEquity = initialCapital
        chronological.forEachIndexed { index, entry ->
            runningEquity += entry.pnlAmount
            curve.add(EquityCurvePoint("#${index + 1} ${entry.symbol.take(6)}", runningEquity))
        }

        val strategyBreakdown = chronological
            .groupBy { it.strategyTag.ifBlank { "Untagged" } }
            .map { (tag, items) ->
                val tagWins = items.count { it.pnlAmount > 0.0 }
                TagPerformance(
                    tag = tag,
                    trades = items.size,
                    winRatePercent = (tagWins.toDouble() / items.size) * 100.0,
                    totalPnl = items.sumOf { it.pnlAmount }
                )
            }
            .sortedByDescending { it.totalPnl }

        val emotionBreakdown = chronological
            .groupBy { it.emotionTag.ifBlank { "Neutral" } }
            .map { (tag, items) ->
                val tagWins = items.count { it.pnlAmount > 0.0 }
                TagPerformance(
                    tag = tag,
                    trades = items.size,
                    winRatePercent = (tagWins.toDouble() / items.size) * 100.0,
                    totalPnl = items.sumOf { it.pnlAmount }
                )
            }
            .sortedByDescending { it.totalPnl }

        return PortfolioAnalytics(
            totalEntries = chronological.size,
            wins = wins.size,
            losses = losses.size,
            winRatePercent = winRate,
            netRealizedPnl = netPnl,
            grossProfit = grossProfit,
            grossLoss = grossLoss,
            profitFactor = profitFactor,
            averageWin = avgWin,
            averageLoss = avgLoss,
            bestTradePnl = bestTrade,
            worstTradePnl = worstTrade,
            equityCurve = curve,
            strategyBreakdown = strategyBreakdown,
            emotionBreakdown = emotionBreakdown
        )
    }
}
