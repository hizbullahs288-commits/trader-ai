package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.domain.engine.RiskCalculatorEngine
import com.example.domain.model.AssetClass
import com.example.domain.model.RiskCalculationInput
import com.example.domain.model.RiskCalculationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

data class RiskCalculatorUiState(
    val symbol: String = "BTC-USD",
    val assetClass: AssetClass = AssetClass.CRYPTO,
    val balanceText: String = "100000.00",
    val riskPercentText: String = "1.0",
    val entryPriceText: String = "",
    val stopLossText: String = "",
    val takeProfitText: String = "",
    val result: RiskCalculationResult = RiskCalculatorEngine.calculate(
        RiskCalculationInput(
            accountBalance = 100_000.0,
            riskPercent = 1.0,
            entryPrice = 0.0,
            stopLossPrice = 0.0
        )
    )
)

class RiskCalculatorViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RiskCalculatorUiState())
    val uiState: StateFlow<RiskCalculatorUiState> = _uiState.asStateFlow()

    fun prefillFromSetup(
        symbol: String,
        assetClass: AssetClass,
        entryPrice: Double,
        stopLoss: Double,
        takeProfit: Double,
        accountBalance: Double? = null
    ) {
        val decimals = if (assetClass == AssetClass.FOREX) 5 else 2
        _uiState.update { curr ->
            val updated = curr.copy(
                symbol = symbol,
                assetClass = assetClass,
                balanceText = accountBalance?.let { String.format(Locale.US, "%.2f", it) } ?: curr.balanceText,
                entryPriceText = String.format(Locale.US, "%.${decimals}f", entryPrice),
                stopLossText = String.format(Locale.US, "%.${decimals}f", stopLoss),
                takeProfitText = String.format(Locale.US, "%.${decimals}f", takeProfit)
            )
            recalculate(updated)
        }
    }

    fun setBalance(value: String) {
        _uiState.update { recalculate(it.copy(balanceText = value)) }
    }

    fun setRiskPercent(value: String) {
        _uiState.update { recalculate(it.copy(riskPercentText = value)) }
    }

    fun setEntryPrice(value: String) {
        _uiState.update { recalculate(it.copy(entryPriceText = value)) }
    }

    fun setStopLoss(value: String) {
        _uiState.update { recalculate(it.copy(stopLossText = value)) }
    }

    fun setTakeProfit(value: String) {
        _uiState.update { recalculate(it.copy(takeProfitText = value)) }
    }

    fun setAssetClass(assetClass: AssetClass) {
        _uiState.update { recalculate(it.copy(assetClass = assetClass)) }
    }

    private fun recalculate(state: RiskCalculatorUiState): RiskCalculatorUiState {
        val input = RiskCalculationInput(
            accountBalance = state.balanceText.toDoubleOrNull() ?: 0.0,
            riskPercent = state.riskPercentText.toDoubleOrNull() ?: 0.0,
            entryPrice = state.entryPriceText.toDoubleOrNull() ?: 0.0,
            stopLossPrice = state.stopLossText.toDoubleOrNull() ?: 0.0,
            takeProfitPrice = state.takeProfitText.toDoubleOrNull() ?: 0.0,
            assetClass = state.assetClass,
            symbol = state.symbol
        )
        return state.copy(result = RiskCalculatorEngine.calculate(input))
    }
}
