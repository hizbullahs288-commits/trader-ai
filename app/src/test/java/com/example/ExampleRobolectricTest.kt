package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.AssetClass
import com.example.domain.model.MarketQuote
import com.example.domain.model.WatchlistDefaults
import com.example.ui.screens.MarketsScreen
import com.example.ui.screens.RiskCalculatorScreen
import com.example.ui.theme.TradePulseTheme
import com.example.ui.viewmodel.MarketDashboardUiState
import com.example.ui.viewmodel.RiskCalculatorViewModel
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read app config strings from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("TradePulse", context.getString(R.string.app_name))
        assertTrue(context.getString(R.string.firestore_database_id).isNotBlank())
    }

    @Test
    fun `risk calculator screen computes live position sizing on input`() {
        val vm = RiskCalculatorViewModel()
        composeTestRule.setContent {
            TradePulseTheme {
                val state by vm.uiState.collectAsStateWithLifecycle()
                RiskCalculatorScreen(
                    uiState = state,
                    paperAccountBalance = 100_000.0,
                    onBalanceChange = vm::setBalance,
                    onRiskPercentChange = vm::setRiskPercent,
                    onEntryPriceChange = vm::setEntryPrice,
                    onStopLossChange = vm::setStopLoss,
                    onTakeProfitChange = vm::setTakeProfit,
                    onAssetClassChange = vm::setAssetClass,
                    onSendToPaperTrade = { _, _, _, _, _, _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithTag("risk_entry_input").performTextReplacement("100")
        composeTestRule.onNodeWithTag("risk_sl_input").performTextReplacement("90")
        composeTestRule.onNodeWithTag("risk_tp_input").performTextReplacement("130")
        composeTestRule.waitForIdle()

        // Risk = 1% of 100,000 = $1,000; stop distance = 10 -> 100 units
        composeTestRule.onNodeWithTag("risk_calculator_list").performScrollToIndex(2)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("risk_output_units").assertIsDisplayed()
        composeTestRule.onNodeWithText("100 units").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 : 3.00").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `markets screen filters across Crypto Forex Stocks and Gold`() {
        val sampleQuotes = mapOf(
            "BTC-USD" to MarketQuote(
                instrument = WatchlistDefaults.findBySymbol("BTC-USD")!!,
                price = 92500.0,
                previousClose = 90000.0,
                dayHigh = 93000.0,
                dayLow = 89500.0,
                changeAmount = 2500.0,
                changePercent = 2.78,
                sourceName = "Coinbase",
                updatedAtMs = System.currentTimeMillis()
            ),
            "GC=F" to MarketQuote(
                instrument = WatchlistDefaults.findBySymbol("GC=F")!!,
                price = 2650.0,
                previousClose = 2630.0,
                dayHigh = 2660.0,
                dayLow = 2620.0,
                changeAmount = 20.0,
                changePercent = 0.76,
                sourceName = "COMEX",
                updatedAtMs = System.currentTimeMillis()
            )
        )
        var selectedCategory: AssetClass? = null

        composeTestRule.setContent {
            TradePulseTheme {
                MarketsScreen(
                    uiState = MarketDashboardUiState(
                        quotes = sampleQuotes,
                        selectedCategory = selectedCategory
                    ),
                    userEmail = "trader@example.com",
                    onSelectCategory = { selectedCategory = it },
                    onSearchQueryChange = {},
                    onLookupCustomSymbol = {},
                    onToggleAutoRefresh = {},
                    onRefreshNow = {},
                    onOpenChart = {},
                    onQuickPaperTrade = { _, _ -> },
                    onSignOut = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("instrument_card_BTC-USD").assertIsDisplayed()
        composeTestRule.onNodeWithTag("category_chip_GOLD").performClick()
        assertEquals(AssetClass.GOLD, selectedCategory)
    }
}
