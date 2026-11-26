package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.AssetClass
import com.example.domain.model.MarketInstrument
import com.example.domain.model.MarketQuote
import com.example.ui.components.AssetClassBadge
import com.example.ui.components.PriceChangePill
import com.example.ui.components.TradingFormatters
import com.example.ui.theme.BearRed
import com.example.ui.theme.BearRedDark
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.BullGreen
import com.example.ui.theme.BullGreenDark
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.MonoLabelSmall
import com.example.ui.theme.MonoPriceMedium
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.viewmodel.MarketDashboardUiState

@Composable
fun MarketsScreen(
    uiState: MarketDashboardUiState,
    userEmail: String?,
    onSelectCategory: (AssetClass?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onLookupCustomSymbol: (String) -> Unit,
    onToggleAutoRefresh: () -> Unit,
    onRefreshNow: () -> Unit,
    onOpenChart: (MarketInstrument) -> Unit,
    onQuickPaperTrade: (MarketInstrument, MarketQuote?) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredInstruments = uiState.instruments.filter { inst ->
        val matchesCat = uiState.selectedCategory == null || inst.assetClass == uiState.selectedCategory
        val q = uiState.searchQuery.trim()
        val matchesQuery = q.isBlank() ||
            inst.symbol.contains(q, ignoreCase = true) ||
            inst.displayName.contains(q, ignoreCase = true) ||
            inst.shortTicker.contains(q, ignoreCase = true)
        matchesCat && matchesQuery
    }

    val loadedQuotes = uiState.quotes.values.toList()
    val topGainer = loadedQuotes.maxByOrNull { it.changePercent }
    val topLoser = loadedQuotes.minByOrNull { it.changePercent }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Live Markets",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    val syncText = uiState.lastSyncedAtMs?.let {
                        "Feed synced ${TradingFormatters.formatClock(it)}"
                    } ?: "Connecting to live market feeds..."
                    Text(
                        text = syncText,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (uiState.autoRefreshEnabled) BullGreenDark else ElevatedSurface,
                        border = BorderStroke(
                            1.dp,
                            if (uiState.autoRefreshEnabled) BullGreen.copy(alpha = 0.5f) else BorderSlate
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(onClick = onToggleAutoRefresh)
                            .testTag("toggle_live_feed_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (uiState.autoRefreshEnabled) BullGreen else TextTertiary)
                            )
                            Text(
                                text = if (uiState.autoRefreshEnabled) "LIVE 15s" else "PAUSED",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (uiState.autoRefreshEnabled) BullGreen else TextSecondary
                            )
                            Icon(
                                imageVector = if (uiState.autoRefreshEnabled) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Toggle auto refresh",
                                tint = if (uiState.autoRefreshEnabled) BullGreen else TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onRefreshNow,
                        modifier = Modifier.testTag("refresh_markets_button")
                    ) {
                        if (uiState.isRefreshingQuotes) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = CyanAccent
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh quotes",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign out ${userEmail ?: ""}",
                            tint = TextSecondary
                        )
                    }
                }
            }
        }

        // Top Market Movers Strip
        if (topGainer != null && topLoser != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MoverCard(
                        label = "TOP GAINER",
                        quote = topGainer,
                        isPositive = true,
                        onClick = { onOpenChart(topGainer.instrument) },
                        modifier = Modifier.weight(1f)
                    )
                    MoverCard(
                        label = "TOP MOVER DOWN",
                        quote = topLoser,
                        isPositive = false,
                        onClick = { onOpenChart(topLoser.instrument) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Search & Custom Ticker Input
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search or lookup ticker (e.g. AMZN, DOGE-USD)") },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = "Search tickers", tint = TextSecondary)
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = TextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (uiState.searchQuery.isNotBlank()) onLookupCustomSymbol(uiState.searchQuery)
                        }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = BorderSlate,
                        focusedContainerColor = CardSurface,
                        unfocusedContainerColor = CardSurface
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("market_search_input")
                )

                if (uiState.searchQuery.isNotBlank()) {
                    Button(
                        onClick = { onLookupCustomSymbol(uiState.searchQuery) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanAccent,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("lookup_ticker_button")
                    ) {
                        Text("Load", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Asset Class Category Tabs
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val categories: List<Pair<AssetClass?, String>> = listOf(
                    null to "All Markets",
                    AssetClass.CRYPTO to "Crypto",
                    AssetClass.FOREX to "Forex",
                    AssetClass.STOCKS to "Stocks",
                    AssetClass.GOLD to "Gold"
                )
                categories.forEach { (cat, label) ->
                    val selected = uiState.selectedCategory == cat
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectCategory(cat) },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BullGreenDark,
                            selectedLabelColor = BullGreen,
                            containerColor = CardSurface,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = BorderSlate,
                            selectedBorderColor = BullGreen.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.testTag("category_chip_${cat?.name ?: "ALL"}")
                    )
                }
            }
        }

        // Error Banner (if all live feeds failed)
        if (!uiState.quotesError.isNullOrBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BearRedDark,
                    border = BorderStroke(1.dp, BearRed.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Live Market Feed Error",
                                style = MaterialTheme.typography.titleSmall,
                                color = BearRed,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = uiState.quotesError,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onRefreshNow,
                            colors = ButtonDefaults.buttonColors(containerColor = BearRed)
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
        }

        // Watchlist Items
        if (filteredInstruments.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, BorderSlate),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No matching watchlist symbol",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap \"Load\" above to query Yahoo Finance directly for \"${uiState.searchQuery.uppercase()}\".",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(filteredInstruments, key = { it.symbol }) { instrument ->
                val quote = uiState.quotes[instrument.symbol]
                InstrumentMarketCard(
                    instrument = instrument,
                    quote = quote,
                    isSelected = uiState.selectedInstrument.symbol == instrument.symbol,
                    onOpenChart = { onOpenChart(instrument) },
                    onQuickTrade = { onQuickPaperTrade(instrument, quote) }
                )
            }
        }
    }
}

@Composable
private fun MoverCard(
    label: String,
    quote: MarketQuote,
    isPositive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (quote.changePercent >= 0) BullGreen else BearRed
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, BorderSlate)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isPositive) BullGreen else GoldAccent
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = quote.instrument.shortTicker,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = TradingFormatters.formatSignedPercent(quote.changePercent),
                    style = MonoLabelSmall,
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = TradingFormatters.formatPrice(quote.price, quote.instrument.priceDecimals),
                style = MonoLabelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun InstrumentMarketCard(
    instrument: MarketInstrument,
    quote: MarketQuote?,
    isSelected: Boolean,
    onOpenChart: () -> Unit,
    onQuickTrade: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpenChart)
            .testTag("instrument_card_${instrument.symbol}"),
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) CyanAccent.copy(alpha = 0.7f) else BorderSlate.copy(alpha = 0.75f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = instrument.shortTicker,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        AssetClassBadge(assetClass = instrument.assetClass)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = instrument.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (quote != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = TradingFormatters.formatPrice(quote.price, instrument.priceDecimals),
                            style = MonoPriceMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        PriceChangePill(changePercent = quote.changePercent)
                    }
                } else {
                    Text(
                        text = "Loading...",
                        style = MonoLabelSmall,
                        color = TextTertiary
                    )
                }
            }

            if (quote != null) {
                Spacer(modifier = Modifier.height(10.dp))
                val range = (quote.dayHigh - quote.dayLow).coerceAtLeast(1e-9)
                val pos = ((quote.price - quote.dayLow) / range).toFloat().coerceIn(0f, 1f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "L: ${TradingFormatters.formatPrice(quote.dayLow, instrument.priceDecimals)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                    Text(
                        text = quote.sourceName,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                    Text(
                        text = "H: ${TradingFormatters.formatPrice(quote.dayHigh, instrument.priceDecimals)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { pos },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (quote.changePercent >= 0) BullGreen else BearRed,
                    trackColor = ElevatedSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ElevatedSurface,
                    border = BorderStroke(1.dp, BorderSlate),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenChart)
                        .testTag("open_chart_${instrument.symbol}")
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CandlestickChart,
                            contentDescription = "Open chart",
                            tint = CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Chart & AI",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BullGreenDark,
                    border = BorderStroke(1.dp, BullGreen.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onQuickTrade)
                        .testTag("paper_trade_${instrument.symbol}")
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.SwapHoriz,
                            contentDescription = "Paper trade",
                            tint = BullGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Paper Trade",
                            style = MaterialTheme.typography.labelLarge,
                            color = BullGreen
                        )
                    }
                }
            }
        }
    }
}
