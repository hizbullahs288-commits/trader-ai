package com.example.domain.engine

import com.example.domain.model.AssetClass
import com.example.domain.model.RiskCalculationInput
import com.example.domain.model.RiskCalculationResult
import com.example.domain.model.TradeSide
import kotlin.math.abs

object RiskCalculatorEngine {

    fun calculate(input: RiskCalculationInput): RiskCalculationResult {
        if (input.accountBalance <= 0.0) {
            return RiskCalculationResult(isValid = false, validationError = "Account balance must be greater than 0.")
        }
        if (input.riskPercent <= 0.0 || input.riskPercent > 25.0) {
            return RiskCalculationResult(isValid = false, validationError = "Risk % must be between 0.1% and 25%.")
        }
        if (input.entryPrice <= 0.0) {
            return RiskCalculationResult(isValid = false, validationError = "Enter a valid entry price (> 0).")
        }
        if (input.stopLossPrice <= 0.0 || input.stopLossPrice == input.entryPrice) {
            return RiskCalculationResult(isValid = false, validationError = "Stop-loss must be > 0 and different from entry.")
        }

        val side = if (input.stopLossPrice < input.entryPrice) TradeSide.LONG else TradeSide.SHORT
        val riskAmount = input.accountBalance * (input.riskPercent / 100.0)
        val stopDist = abs(input.entryPrice - input.stopLossPrice)
        val stopDistPercent = (stopDist / input.entryPrice) * 100.0
        val units = riskAmount / stopDist
        val notionalValue = units * input.entryPrice
        val allocationPct = (notionalValue / input.accountBalance) * 100.0

        val forexLots = if (input.assetClass == AssetClass.FOREX) {
            // 1 standard Forex lot = 100,000 base currency units
            units / 100_000.0
        } else null

        val hasValidTp = input.takeProfitPrice > 0.0 && (
            (side == TradeSide.LONG && input.takeProfitPrice > input.entryPrice) ||
                (side == TradeSide.SHORT && input.takeProfitPrice < input.entryPrice)
            )

        val rewardDist = if (hasValidTp) abs(input.takeProfitPrice - input.entryPrice) else 0.0
        val potentialReward = if (hasValidTp) units * rewardDist else 0.0
        val rrRatio = if (hasValidTp && stopDist > 0.0) rewardDist / stopDist else 0.0
        val breakEvenWinRate = if (rrRatio > 0.0) (1.0 / (1.0 + rrRatio)) * 100.0 else 0.0

        val warnings = mutableListOf<String>()
        if (input.riskPercent > 2.5) {
            warnings += "High risk per trade (${String.format("%.1f", input.riskPercent)}%). Institutional desks typically cap risk at 0.5%–2.0%."
        }
        if (hasValidTp && rrRatio < 1.5) {
            warnings += "Risk:Reward (${String.format("%.2f", rrRatio)}) is below 1:1.5, requiring a >${String.format("%.0f", breakEvenWinRate)}% win rate to break even."
        }
        if (allocationPct > 100.0) {
            warnings += "Notional exposure (${String.format("%.0f", allocationPct)}% of equity) implies leverage (${String.format("%.1fx", allocationPct / 100.0)})."
        }

        return RiskCalculationResult(
            isValid = true,
            side = side,
            riskAmountUsd = riskAmount,
            stopDistancePrice = stopDist,
            stopDistancePercent = stopDistPercent,
            positionUnits = units,
            forexStandardLots = forexLots,
            notionalValueUsd = notionalValue,
            accountAllocationPercent = allocationPct,
            potentialRewardUsd = potentialReward,
            riskRewardRatio = rrRatio,
            breakEvenWinRatePercent = breakEvenWinRate,
            warnings = warnings
        )
    }
}
