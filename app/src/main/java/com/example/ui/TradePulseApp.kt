package com.example.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.TradingRepository
import com.example.domain.model.TradeSide
import com.example.firebase.signOut
import com.example.ui.screens.ChartAnalysisScreen
import com.example.ui.screens.JournalAnalyticsScreen
import com.example.ui.screens.MarketsScreen
import com.example.ui.screens.PaperTradingScreen
import com.example.ui.screens.RiskCalculatorScreen
import com.example.ui.theme.BullGreen
import com.example.ui.theme.BullGreenDark
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.DataStreamState
import com.example.ui.viewmodel.MarketChartViewModel
import com.example.ui.viewmodel.PortfolioJournalViewModel
import com.example.ui.viewmodel.RiskCalculatorViewModel
import com.google.firebase.firestore.FirebaseFirestore

object TradePulseRoutes {
    const val MARKETS = "markets"
    const val CHART_AI = "chart_ai"
    const val PAPER_TRADE = "paper_trade"
    const val RISK_CALC = "risk_calc"
    const val JOURNAL = "journal"
}

private data class NavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val destinations = listOf(
    NavDestination(TradePulseRoutes.MARKETS, "Markets", Icons.Filled.ShowChart),
    NavDestination(TradePulseRoutes.CHART_AI, "Chart & AI", Icons.Filled.CandlestickChart),
    NavDestination(TradePulseRoutes.PAPER_TRADE, "Paper", Icons.Filled.SwapHoriz),
    NavDestination(TradePulseRoutes.RISK_CALC, "Risk", Icons.Filled.Calculate),
    NavDestination(TradePulseRoutes.JOURNAL, "Journal", Icons.Filled.Book)
)

@Composable
fun TradePulseApp(
    currentUserId: String,
    userEmail: String?,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val marketVm: MarketChartViewModel = viewModel(key = "market_$currentUserId")
    val riskVm: RiskCalculatorViewModel = viewModel(key = "risk_$currentUserId")
    val portfolioVm: PortfolioJournalViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                PortfolioJournalViewModel(TradingRepository(db), currentUserId)
            }
        }
    )

    val marketState by marketVm.uiState.collectAsStateWithLifecycle()
    val riskState by riskVm.uiState.collectAsStateWithLifecycle()
    val accountState by portfolioVm.accountState.collectAsStateWithLifecycle()
    val positionsState by portfolioVm.positionsState.collectAsStateWithLifecycle()
    val journalState by portfolioVm.journalState.collectAsStateWithLifecycle()
    val analytics by portfolioVm.analyticsState.collectAsStateWithLifecycle()
    val orderDraft by portfolioVm.orderDraft.collectAsStateWithLifecycle()
    val isSubmitting by portfolioVm.isSubmitting.collectAsStateWithLifecycle()
    val banner by portfolioVm.banner.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: TradePulseRoutes.MARKETS

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 680.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ObsidianBg,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(containerColor = ObsidianSurface) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(dest.icon, contentDescription = dest.label)
                                },
                                label = { Text(dest.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = BullGreen,
                                    selectedTextColor = BullGreen,
                                    indicatorColor = BullGreenDark,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(containerColor = ObsidianSurface) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = BullGreen,
                                    selectedTextColor = BullGreen,
                                    indicatorColor = BullGreenDark,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = TradePulseRoutes.MARKETS,
                        modifier = Modifier
                            .widthIn(max = 1100.dp)
                            .fillMaxSize()
                    ) {
                        composable(TradePulseRoutes.MARKETS) {
                            MarketsScreen(
                                uiState = marketState,
                                userEmail = userEmail,
                                onSelectCategory = marketVm::selectCategory,
                                onSearchQueryChange = marketVm::updateSearchQuery,
                                onLookupCustomSymbol = marketVm::lookupCustomSymbol,
                                onToggleAutoRefresh = marketVm::toggleAutoRefresh,
                                onRefreshNow = marketVm::refreshAllNow,
                                onOpenChart = { inst ->
                                    marketVm.selectInstrument(inst)
                                    navigateTo(TradePulseRoutes.CHART_AI)
                                },
                                onQuickPaperTrade = { inst, quote ->
                                    val price = quote?.price ?: 0.0
                                    portfolioVm.prefillOrderDraft(
                                        symbol = inst.symbol,
                                        displayName = inst.displayName,
                                        assetClass = inst.assetClass,
                                        side = TradeSide.LONG,
                                        entryPrice = price
                                    )
                                    navigateTo(TradePulseRoutes.PAPER_TRADE)
                                },
                                onSignOut = {
                                    signOut(credentialManager, scope, onSignedOut)
                                }
                            )
                        }

                        composable(TradePulseRoutes.CHART_AI) {
                            ChartAnalysisScreen(
                                uiState = marketState,
                                onSelectInstrument = marketVm::selectInstrument,
                                onSelectTimeframe = marketVm::selectTimeframe,
                                onUpdateOverlays = marketVm::updateOverlays,
                                onRefreshChart = { marketVm.loadSelectedChart(preserveReadyDuringRefresh = false) },
                                onRunAiAnalysis = marketVm::runAiAnalysis,
                                onAskAiFollowUp = marketVm::askAiFollowUp,
                                onSendToRiskCalculator = { inst, entry, sl, tp ->
                                    val balance = (accountState as? DataStreamState.Ready)?.data?.virtualBalance
                                    riskVm.prefillFromSetup(
                                        symbol = inst.symbol,
                                        assetClass = inst.assetClass,
                                        entryPrice = entry,
                                        stopLoss = sl,
                                        takeProfit = tp,
                                        accountBalance = balance
                                    )
                                    navigateTo(TradePulseRoutes.RISK_CALC)
                                },
                                onSendToPaperTrade = { inst, side, entry, sl, tp, notes ->
                                    portfolioVm.prefillOrderDraft(
                                        symbol = inst.symbol,
                                        displayName = inst.displayName,
                                        assetClass = inst.assetClass,
                                        side = side,
                                        entryPrice = entry,
                                        stopLoss = sl,
                                        takeProfit = tp,
                                        notes = notes
                                    )
                                    navigateTo(TradePulseRoutes.PAPER_TRADE)
                                }
                            )
                        }

                        composable(TradePulseRoutes.PAPER_TRADE) {
                            PaperTradingScreen(
                                accountState = accountState,
                                positionsState = positionsState,
                                instruments = marketState.instruments,
                                quotes = marketState.quotes,
                                orderDraft = orderDraft,
                                isSubmitting = isSubmitting,
                                banner = banner,
                                onClearBanner = portfolioVm::clearBanner,
                                onUpdateOrderDraft = portfolioVm::updateOrderDraft,
                                onSelectOrderInstrument = { inst, price ->
                                    portfolioVm.prefillOrderDraft(
                                        symbol = inst.symbol,
                                        displayName = inst.displayName,
                                        assetClass = inst.assetClass,
                                        side = orderDraft.side,
                                        entryPrice = price ?: 0.0
                                    )
                                },
                                onSubmitOrder = portfolioVm::submitPaperOrder,
                                onClosePosition = portfolioVm::closePaperPosition,
                                onUpdateSlTp = portfolioVm::updateSlTp,
                                onResetAccount = portfolioVm::resetVirtualAccount
                            )
                        }

                        composable(TradePulseRoutes.RISK_CALC) {
                            val paperBalance = (accountState as? DataStreamState.Ready)?.data?.virtualBalance
                            RiskCalculatorScreen(
                                uiState = riskState,
                                paperAccountBalance = paperBalance,
                                onBalanceChange = riskVm::setBalance,
                                onRiskPercentChange = riskVm::setRiskPercent,
                                onEntryPriceChange = riskVm::setEntryPrice,
                                onStopLossChange = riskVm::setStopLoss,
                                onTakeProfitChange = riskVm::setTakeProfit,
                                onAssetClassChange = riskVm::setAssetClass,
                                onSendToPaperTrade = { symbol, ac, side, entry, qty, sl, tp ->
                                    val displayName = marketState.instruments
                                        .firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }
                                        ?.displayName ?: symbol
                                    portfolioVm.prefillOrderDraft(
                                        symbol = symbol,
                                        displayName = displayName,
                                        assetClass = ac,
                                        side = side,
                                        entryPrice = entry,
                                        quantity = qty,
                                        stopLoss = sl,
                                        takeProfit = tp,
                                        notes = "Sized via Risk Calculator"
                                    )
                                    navigateTo(TradePulseRoutes.PAPER_TRADE)
                                }
                            )
                        }

                        composable(TradePulseRoutes.JOURNAL) {
                            JournalAnalyticsScreen(
                                journalState = journalState,
                                analytics = analytics,
                                onAddEntry = portfolioVm::addJournalEntry,
                                onUpdateEntry = portfolioVm::updateJournalEntry,
                                onDeleteEntry = portfolioVm::deleteJournalEntry
                            )
                        }
                    }
                }
            }
        }
    }
}
