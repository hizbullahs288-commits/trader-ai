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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AiAnalysisResult
import com.example.domain.model.LevelType
import com.example.domain.model.MarketInstrument
import com.example.domain.model.SignalDirection
import com.example.domain.model.TechnicalAnalysisBundle
import com.example.domain.model.Timeframe
import com.example.domain.model.TradeSide
import com.example.ui.components.AssetClassBadge
import com.example.ui.components.CandlestickChartSection
import com.example.ui.components.PriceChangePill
import com.example.ui.components.SignalBadge
import com.example.ui.components.TradingFormatters
import com.example.ui.theme.BearRed
import com.example.ui.theme.BearRedDark
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.BullGreen
import com.example.ui.theme.BullGreenDark
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanDark
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.MonoLabelSmall
import com.example.ui.theme.MonoPriceLarge
import com.example.ui.theme.MonoPriceMedium
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.viewmodel.AiFollowUpState
import com.example.ui.viewmodel.AiUiState
import com.example.ui.viewmodel.ChartLoadState
import com.example.ui.viewmodel.MarketDashboardUiState
import com.example.ui.viewmodel.OverlayPreferences
import java.util.Locale

@Composable
fun ChartAnalysisScreen(
    uiState: MarketDashboardUiState,
    onSelectInstrument: (MarketInstrument) -> Unit,
    onSelectTimeframe: (Timeframe) -> Unit,
    onUpdateOverlays: ((OverlayPreferences) -> OverlayPreferences) -> Unit,
    onRefreshChart: () -> Unit,
    onRunAiAnalysis: () -> Unit,
    onAskAiFollowUp: (String) -> Unit,
    onSendToRiskCalculator: (MarketInstrument, Double, Double, Double) -> Unit,
    onSendToPaperTrade: (MarketInstrument, TradeSide, Double, Double?, Double?, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var visibleCandleCount by remember { mutableIntStateOf(60) }
    var followUpInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Quick Instrument Switcher Strip
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                uiState.instruments.forEach { inst ->
                    val selected = inst.symbol == uiState.selectedInstrument.symbol
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) CyanDark else CardSurface,
                        border = BorderStroke(
                            1.dp,
                            if (selected) CyanAccent else BorderSlate
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectInstrument(inst) }
                    ) {
                        Text(
                            text = inst.shortTicker,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) CyanAccent else TextSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Price Header
        item {
            val inst = uiState.selectedInstrument
            val ready = uiState.chartState as? ChartLoadState.Ready
            val quote = ready?.chartData?.quote ?: uiState.quotes[inst.symbol]

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardSurface,
                border = BorderStroke(1.dp, BorderSlate),
                modifier = Modifier.fillMaxWidth()
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
                                    text = inst.shortTicker,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                AssetClassBadge(assetClass = inst.assetClass)
                            }
                            Text(
                                text = "${inst.displayName} • ${quote?.sourceName ?: "Live Feed"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        IconButton(onClick = onRefreshChart) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh chart",
                                tint = CyanAccent
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (quote != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = TradingFormatters.formatPrice(quote.price, inst.priceDecimals),
                                style = MonoPriceLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            PriceChangePill(changePercent = quote.changePercent)
                        }
                    }
                }
            }
        }

        // Timeframe & Zoom Bar
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Timeframe.entries.forEach { tf ->
                        val selected = uiState.selectedTimeframe == tf
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) BullGreenDark else CardSurface,
                            border = BorderStroke(1.dp, if (selected) BullGreen else BorderSlate),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectTimeframe(tf) }
                                .testTag("tf_chip_${tf.label}")
                        ) {
                            Text(
                                text = tf.label,
                                style = MonoLabelSmall,
                                color = if (selected) BullGreen else TextSecondary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                // Overlay & Sub-pane Toggles + Zoom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OverlayToggleChip("EMA20", uiState.overlays.showEma20, CyanAccent) {
                        onUpdateOverlays { it.copy(showEma20 = !it.showEma20) }
                    }
                    OverlayToggleChip("EMA50", uiState.overlays.showEma50, GoldAccent) {
                        onUpdateOverlays { it.copy(showEma50 = !it.showEma50) }
                    }
                    OverlayToggleChip("BB", uiState.overlays.showBollinger, IndigoAccent) {
                        onUpdateOverlays { it.copy(showBollinger = !it.showBollinger) }
                    }
                    OverlayToggleChip("S/R", uiState.overlays.showSupportResistance, BullGreen) {
                        onUpdateOverlays { it.copy(showSupportResistance = !it.showSupportResistance) }
                    }
                    OverlayToggleChip("VOL", uiState.overlays.showVolume, TextSecondary) {
                        onUpdateOverlays { it.copy(showVolume = !it.showVolume) }
                    }
                    OverlayToggleChip("RSI", uiState.overlays.showRsi, CyanAccent) {
                        onUpdateOverlays { it.copy(showRsi = !it.showRsi) }
                    }
                    OverlayToggleChip("MACD", uiState.overlays.showMacd, GoldAccent) {
                        onUpdateOverlays { it.copy(showMacd = !it.showMacd) }
                    }
                    listOf(35, 60, 100, 160).forEach { count ->
                        val active = visibleCandleCount == count
                        FilterChip(
                            selected = active,
                            onClick = { visibleCandleCount = count },
                            label = { Text("${count}c", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElevatedSurface,
                                selectedLabelColor = CyanAccent
                            )
                        )
                    }
                }
            }
        }

        // Main Chart Body
        when (val state = uiState.chartState) {
            ChartLoadState.Loading -> {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, BorderSlate),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = CyanAccent)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Fetching live OHLCV candles...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            is ChartLoadState.Error -> {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = BearRedDark,
                        border = BorderStroke(1.dp, BearRed.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Chart Data Unavailable",
                                style = MaterialTheme.typography.titleMedium,
                                color = BearRed,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onRefreshChart,
                                colors = ButtonDefaults.buttonColors(containerColor = BearRed)
                            ) {
                                Text("Retry Live Feed")
                            }
                        }
                    }
                }
            }

            is ChartLoadState.Ready -> {
                val chartData = state.chartData
                val analysis = state.analysis
                val decimals = chartData.instrument.priceDecimals

                item {
                    CandlestickChartSection(
                        candles = chartData.candles,
                        analysis = analysis,
                        overlays = uiState.overlays,
                        priceDecimals = decimals,
                        visibleCount = visibleCandleCount
                    )
                }

                // Quick Trade & Risk Sizing Bar
                item {
                    val qSig = analysis.quantitativeSignal
                    val aiSig = (uiState.aiState as? AiUiState.Ready)?.result
                    val entry = qSig.suggestedEntry
                    val sl = aiSig?.stopLoss ?: qSig.suggestedStopLoss
                    val tp = aiSig?.takeProfit ?: qSig.suggestedTakeProfit
                    val dir = aiSig?.signal ?: qSig.direction
                    val tradeSide = if (dir == SignalDirection.SELL) TradeSide.SHORT else TradeSide.LONG

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                onSendToPaperTrade(
                                    chartData.instrument,
                                    tradeSide,
                                    entry,
                                    sl,
                                    tp,
                                    aiSig?.headline ?: qSig.reasons.firstOrNull().orEmpty()
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (tradeSide == TradeSide.LONG) BullGreen else BearRed,
                                contentColor = ObsidianBg
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("chart_to_paper_trade_button")
                        ) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paper ${tradeSide.name}", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                onSendToRiskCalculator(chartData.instrument, entry, sl, tp)
                            },
                            border = BorderStroke(1.dp, CyanAccent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("chart_to_risk_calc_button")
                        ) {
                            Icon(Icons.Filled.Calculate, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Position Size", color = CyanAccent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Quantitative Indicators & Support/Resistance Breakdown
                item {
                    IndicatorsBreakdownCard(
                        analysis = analysis,
                        decimals = decimals
                    )
                }

                // Gemini AI Technical Analysis Card
                item {
                    AiTechnicalAnalysisCard(
                        aiState = uiState.aiState,
                        followUpState = uiState.aiFollowUpState,
                        decimals = decimals,
                        followUpInput = followUpInput,
                        onFollowUpInputChange = { followUpInput = it },
                        onRunAi = onRunAiAnalysis,
                        onSendFollowUp = {
                            if (followUpInput.isNotBlank()) {
                                onAskAiFollowUp(followUpInput)
                                followUpInput = ""
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayToggleChip(
    label: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (active) accent.copy(alpha = 0.16f) else CardSurface,
        border = BorderStroke(1.dp, if (active) accent.copy(alpha = 0.7f) else BorderSlate),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) accent else TextTertiary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun IndicatorsBreakdownCard(
    analysis: TechnicalAnalysisBundle,
    decimals: Int
) {
    val s = analysis.summary
    val q = analysis.quantitativeSignal

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, BorderSlate),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Technical Confluence",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Quantitative score: ${q.score}/100 • R:R 1:${String.format(Locale.US, "%.2f", q.riskRewardRatio)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                SignalBadge(direction = q.direction, confidence = q.confidencePercent)
            }

            // Key indicator grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val rsiVal = s.rsi14
                val rsiColor = when {
                    rsiVal == null -> TextSecondary
                    rsiVal >= 70.0 -> BearRed
                    rsiVal <= 30.0 -> BullGreen
                    else -> CyanAccent
                }
                MetricBox(
                    label = "RSI (14)",
                    value = rsiVal?.let { String.format(Locale.US, "%.1f", it) } ?: "N/A",
                    valueColor = rsiColor,
                    modifier = Modifier.weight(1f)
                )
                val macdHist = s.macd?.histogram
                MetricBox(
                    label = "MACD Hist",
                    value = macdHist?.let { String.format(Locale.US, "%+.4f", it) } ?: "N/A",
                    valueColor = if ((macdHist ?: 0.0) >= 0.0) BullGreen else BearRed,
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    label = "ATR (14)",
                    value = s.atr14?.let { TradingFormatters.formatPrice(it, decimals) } ?: "N/A",
                    valueColor = GoldAccent,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricBox(
                    label = "EMA 20",
                    value = s.ema20?.let { TradingFormatters.formatPrice(it, decimals) } ?: "N/A",
                    valueColor = CyanAccent,
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    label = "EMA 50",
                    value = s.ema50?.let { TradingFormatters.formatPrice(it, decimals) } ?: "N/A",
                    valueColor = GoldAccent,
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    label = "BB %B",
                    value = s.bollingerPercentB?.let { "${String.format(Locale.US, "%.0f", it * 100)}%" } ?: "N/A",
                    valueColor = IndigoAccent,
                    modifier = Modifier.weight(1f)
                )
            }

            // Support & Resistance Pills
            if (analysis.supportResistanceLevels.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Detected Support & Resistance",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        analysis.supportResistanceLevels.forEach { lvl ->
                            val isSup = lvl.type == LevelType.SUPPORT
                            val col = if (isSup) BullGreen else BearRed
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = col.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, col.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "${if (isSup) "SUP" else "RES"} ${TradingFormatters.formatPrice(lvl.price, decimals)} (${lvl.touches}x)",
                                    style = MonoLabelSmall,
                                    color = col,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Reasons
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                q.reasons.forEach { reason ->
                    Text(
                        text = "• $reason",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricBox(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = ElevatedSurface.copy(alpha = 0.7f),
        border = BorderStroke(0.5.dp, BorderSlate)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MonoLabelSmall,
                color = valueColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AiTechnicalAnalysisCard(
    aiState: AiUiState,
    followUpState: AiFollowUpState,
    decimals: Int,
    followUpInput: String,
    onFollowUpInputChange: (String) -> Unit,
    onRunAi: () -> Unit,
    onSendFollowUp: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = "AI Analysis",
                        tint = CyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "AI Technical Analyst",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Powered by Gemini 3.8 Flash • Live indicator telemetry",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )
                    }
                }

                Button(
                    onClick = onRunAi,
                    enabled = aiState !is AiUiState.Loading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanAccent,
                        contentColor = ObsidianBg
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("run_ai_analysis_button")
                ) {
                    Text(
                        text = if (aiState is AiUiState.Ready) "Re-Analyze" else "Analyze",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            when (aiState) {
                AiUiState.Idle -> {
                    Text(
                        text = "Tap \"Analyze\" to generate an AI-powered BUY / SELL / HOLD thesis grounded in the current chart's RSI, MACD, EMA, Bollinger Bands, and Support/Resistance levels.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                AiUiState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = CyanAccent
                        )
                        Text(
                            text = "Evaluating indicator confluence and price structure...",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyanAccent
                        )
                    }
                }

                is AiUiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BearRedDark,
                        border = BorderStroke(1.dp, BearRed.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = aiState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = BearRed,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                is AiUiState.Ready -> {
                    AiResultContent(result = aiState.result, decimals = decimals)
                }
            }

            // Streaming AI Q&A Follow-Up
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = followUpInput,
                    onValueChange = onFollowUpInputChange,
                    placeholder = { Text("Ask AI about this chart setup...") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = BorderSlate
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_followup_input")
                )
                IconButton(
                    onClick = onSendFollowUp,
                    enabled = followUpInput.isNotBlank() && followUpState !is AiFollowUpState.Loading,
                    modifier = Modifier
                        .size(48.dp)
                        .background(CyanDark, RoundedCornerShape(10.dp))
                        .testTag("ai_followup_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Ask AI",
                        tint = CyanAccent
                    )
                }
            }

            when (followUpState) {
                AiFollowUpState.Idle -> Unit
                AiFollowUpState.Loading -> {
                    Text("Thinking...", style = MaterialTheme.typography.bodySmall, color = CyanAccent)
                }
                is AiFollowUpState.Streaming -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ElevatedSurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = followUpState.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                is AiFollowUpState.Complete -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ElevatedSurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = followUpState.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                is AiFollowUpState.Error -> {
                    Text(
                        text = followUpState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = BearRed
                    )
                }
            }
        }
    }
}

@Composable
private fun AiResultContent(result: AiAnalysisResult, decimals: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SignalBadge(direction = result.signal, confidence = result.confidence)
            Text(
                text = "${result.symbol} • ${result.timeframeLabel}",
                style = MonoLabelSmall,
                color = TextSecondary
            )
        }

        Text(
            text = result.headline,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = result.summary,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricBox(
                label = "Entry Zone",
                value = result.entryZone,
                valueColor = CyanAccent,
                modifier = Modifier.weight(1f)
            )
            MetricBox(
                label = "Stop Loss",
                value = TradingFormatters.formatPrice(result.stopLoss, decimals),
                valueColor = BearRed,
                modifier = Modifier.weight(1f)
            )
            MetricBox(
                label = "Take Profit",
                value = TradingFormatters.formatPrice(result.takeProfit, decimals),
                valueColor = BullGreen,
                modifier = Modifier.weight(1f)
            )
        }

        if (result.keyDrivers.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Key Technical Drivers",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                result.keyDrivers.forEach { driver ->
                    Text(
                        text = "• $driver",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        if (result.invalidationCondition.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ObsidianBg.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, BorderSlate),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Invalidation: ${result.invalidationCondition}",
                    style = MaterialTheme.typography.bodySmall,
                    color = GoldAccent,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}
