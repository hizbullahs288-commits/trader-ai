package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.MarketInstrument
import com.example.domain.model.MarketQuote
import com.example.domain.model.PaperPosition
import com.example.domain.model.PositionStatus
import com.example.domain.model.TradeSide
import com.example.domain.model.UserAccount
import com.example.ui.components.AssetClassBadge
import com.example.ui.components.TradingFormatters
import com.example.ui.components.VirtualTradingSafetyBanner
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
import com.example.ui.theme.MonoPriceLarge
import com.example.ui.theme.MonoPriceMedium
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.viewmodel.ActionBanner
import com.example.ui.viewmodel.DataStreamState
import com.example.ui.viewmodel.OrderDraft

@Composable
fun PaperTradingScreen(
    accountState: DataStreamState<UserAccount>,
    positionsState: DataStreamState<List<PaperPosition>>,
    instruments: List<MarketInstrument>,
    quotes: Map<String, MarketQuote>,
    orderDraft: OrderDraft,
    isSubmitting: Boolean,
    banner: ActionBanner?,
    onClearBanner: () -> Unit,
    onUpdateOrderDraft: ((OrderDraft) -> OrderDraft) -> Unit,
    onSelectOrderInstrument: (MarketInstrument, Double?) -> Unit,
    onSubmitOrder: (Double?) -> Unit,
    onClosePosition: (PaperPosition, Double, Boolean, String, String) -> Unit,
    onUpdateSlTp: (String, Double?, Double?, String) -> Unit,
    onResetAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Open, 1=New Order, 2=Closed
    var showResetConfirm by remember { mutableStateOf(false) }
    var editingPosition by remember { mutableStateOf<PaperPosition?>(null) }
    var closingPosition by remember { mutableStateOf<PaperPosition?>(null) }

    val account = (accountState as? DataStreamState.Ready)?.data ?: UserAccount()
    val positions = (positionsState as? DataStreamState.Ready)?.data ?: emptyList()
    val openPositions = positions.filter { it.parsedStatus == PositionStatus.OPEN }
    val closedPositions = positions.filter { it.parsedStatus == PositionStatus.CLOSED }

    val totalUnrealized = openPositions.sumOf { pos ->
        val mark = quotes[pos.symbol]?.price ?: pos.entryPrice
        pos.computeUnrealizedPnl(mark)
    }
    val openPositionNotional = openPositions.sumOf { it.quantity * it.entryPrice }
    val totalEquity = account.virtualBalance + openPositionNotional + totalUnrealized

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            VirtualTradingSafetyBanner()
        }

        // Banner feedback
        if (banner != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (banner.isError) BearRedDark else BullGreenDark,
                    border = BorderStroke(
                        1.dp,
                        if (banner.isError) BearRed.copy(alpha = 0.6f) else BullGreen.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = banner.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (banner.isError) BearRed else BullGreen,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearBanner) {
                            Text("Dismiss", color = TextSecondary)
                        }
                    }
                }
            }
        }

        // Virtual Portfolio Summary Card
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardSurface,
                border = BorderStroke(1.dp, BorderSlate),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Virtual Portfolio Equity",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextSecondary
                            )
                            Text(
                                text = TradingFormatters.formatUsd(totalEquity),
                                style = MonoPriceLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { showResetConfirm = true },
                            modifier = Modifier.testTag("reset_paper_account_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.RestartAlt,
                                contentDescription = "Reset virtual account",
                                tint = TextSecondary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryStatChip(
                            label = "Cash Available",
                            value = TradingFormatters.formatUsd(account.virtualBalance),
                            valueColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatChip(
                            label = "Unrealized P&L",
                            value = TradingFormatters.formatSignedUsd(totalUnrealized),
                            valueColor = if (totalUnrealized >= 0) BullGreen else BearRed,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatChip(
                            label = "Realized P&L",
                            value = TradingFormatters.formatSignedUsd(account.realizedPnl),
                            valueColor = if (account.realizedPnl >= 0) BullGreen else BearRed,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Tabs
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tabs = listOf(
                    "Open (${openPositions.size})",
                    "New Order",
                    "History (${closedPositions.size})"
                )
                tabs.forEachIndexed { index, title ->
                    val selected = selectedTab == index
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) BullGreenDark else CardSurface,
                        border = BorderStroke(1.dp, if (selected) BullGreen else BorderSlate),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedTab = index }
                            .testTag("paper_tab_$index")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) BullGreen else TextSecondary
                            )
                        }
                    }
                }
            }
        }

        when (selectedTab) {
            0 -> {
                if (positionsState is DataStreamState.Loading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = CyanAccent)
                        }
                    }
                } else if (openPositions.isEmpty()) {
                    item {
                        EmptyPaperState(
                            title = "No open paper positions",
                            subtitle = "Place a virtual LONG or SHORT order from the New Order tab or directly from any Chart.",
                            ctaText = "Create Virtual Order",
                            onCtaClick = { selectedTab = 1 }
                        )
                    }
                } else {
                    items(openPositions, key = { it.id }) { pos ->
                        val markPrice = quotes[pos.symbol]?.price ?: pos.entryPrice
                        OpenPositionCard(
                            position = pos,
                            markPrice = markPrice,
                            onEditSlTp = { editingPosition = pos },
                            onCloseClick = { closingPosition = pos }
                        )
                    }
                }
            }

            1 -> {
                item {
                    NewOrderTicketCard(
                        instruments = instruments,
                        quotes = quotes,
                        orderDraft = orderDraft,
                        availableBalance = account.virtualBalance,
                        isSubmitting = isSubmitting,
                        onUpdateOrderDraft = onUpdateOrderDraft,
                        onSelectInstrument = onSelectOrderInstrument,
                        onSubmit = onSubmitOrder
                    )
                }
            }

            2 -> {
                if (closedPositions.isEmpty()) {
                    item {
                        EmptyPaperState(
                            title = "No closed paper trades yet",
                            subtitle = "Close an open position to see your realized trade history here.",
                            ctaText = "View Open Positions",
                            onCtaClick = { selectedTab = 0 }
                        )
                    }
                } else {
                    items(closedPositions, key = { it.id }) { pos ->
                        ClosedPositionCard(position = pos)
                    }
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset Virtual Account?") },
            text = {
                Text("This resets your virtual cash balance to $100,000.00 and clears realized P&L counters. Existing trade logs remain visible.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        onResetAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = ObsidianBg)
                ) {
                    Text("Reset to $100k")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    editingPosition?.let { pos ->
        EditSlTpDialog(
            position = pos,
            onDismiss = { editingPosition = null },
            onSave = { sl, tp, notes ->
                onUpdateSlTp(pos.id, sl, tp, notes)
                editingPosition = null
            }
        )
    }

    closingPosition?.let { pos ->
        val markPrice = quotes[pos.symbol]?.price ?: pos.entryPrice
        ClosePositionDialog(
            position = pos,
            markPrice = markPrice,
            onDismiss = { closingPosition = null },
            onConfirmClose = { exitPx, autoLog, strat, emo ->
                onClosePosition(pos, exitPx, autoLog, strat, emo)
                closingPosition = null
            }
        )
    }
}

@Composable
private fun SummaryStatChip(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = ElevatedSurface.copy(alpha = 0.7f),
        border = BorderStroke(0.5.dp, BorderSlate)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
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
private fun OpenPositionCard(
    position: PaperPosition,
    markPrice: Double,
    onEditSlTp: () -> Unit,
    onCloseClick: () -> Unit
) {
    val pnl = position.computeUnrealizedPnl(markPrice)
    val pnlPct = position.computeUnrealizedPercent(markPrice)
    val isProfit = pnl >= 0.0
    val decimals = if (position.parsedAssetClass == com.example.domain.model.AssetClass.FOREX) 5 else 2

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (position.parsedSide == TradeSide.LONG) BullGreenDark else BearRedDark
                    ) {
                        Text(
                            text = position.parsedSide.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (position.parsedSide == TradeSide.LONG) BullGreen else BearRed,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = position.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    AssetClassBadge(assetClass = position.parsedAssetClass)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = TradingFormatters.formatSignedUsd(pnl),
                        style = MonoPriceMedium,
                        color = if (isProfit) BullGreen else BearRed
                    )
                    Text(
                        text = TradingFormatters.formatSignedPercent(pnlPct),
                        style = MonoLabelSmall,
                        color = if (isProfit) BullGreen else BearRed
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Qty: ${String.format("%.4f", position.quantity).trimEnd('0').trimEnd('.')}",
                    style = MonoLabelSmall,
                    color = TextSecondary
                )
                Text(
                    text = "Entry: ${TradingFormatters.formatPrice(position.entryPrice, decimals)}",
                    style = MonoLabelSmall,
                    color = TextSecondary
                )
                Text(
                    text = "Mark: ${TradingFormatters.formatPrice(markPrice, decimals)}",
                    style = MonoLabelSmall,
                    color = CyanAccent
                )
            }

            if (position.stopLoss != null || position.takeProfit != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    position.stopLoss?.let {
                        Text(
                            text = "SL: ${TradingFormatters.formatPrice(it, decimals)}",
                            style = MonoLabelSmall,
                            color = BearRed
                        )
                    }
                    position.takeProfit?.let {
                        Text(
                            text = "TP: ${TradingFormatters.formatPrice(it, decimals)}",
                            style = MonoLabelSmall,
                            color = BullGreen
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEditSlTp,
                    border = BorderStroke(1.dp, BorderSlate),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit SL/TP")
                }

                Button(
                    onClick = onCloseClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BearRedDark,
                        contentColor = BearRed
                    ),
                    border = BorderStroke(1.dp, BearRed.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("close_position_${position.id}")
                ) {
                    Text("Close Position", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ClosedPositionCard(position: PaperPosition) {
    val pnl = position.realizedPnl ?: 0.0
    val isWin = pnl >= 0.0
    val decimals = if (position.parsedAssetClass == com.example.domain.model.AssetClass.FOREX) 5 else 2

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, BorderSlate.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${position.parsedSide.name} ${position.symbol}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    AssetClassBadge(assetClass = position.parsedAssetClass)
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Entry ${TradingFormatters.formatPrice(position.entryPrice, decimals)} -> Exit ${TradingFormatters.formatPrice(position.exitPrice ?: position.entryPrice, decimals)}",
                    style = MonoLabelSmall,
                    color = TextSecondary
                )
            }
            Text(
                text = TradingFormatters.formatSignedUsd(pnl),
                style = MonoPriceMedium,
                color = if (isWin) BullGreen else BearRed
            )
        }
    }
}

@Composable
private fun NewOrderTicketCard(
    instruments: List<MarketInstrument>,
    quotes: Map<String, MarketQuote>,
    orderDraft: OrderDraft,
    availableBalance: Double,
    isSubmitting: Boolean,
    onUpdateOrderDraft: ((OrderDraft) -> OrderDraft) -> Unit,
    onSelectInstrument: (MarketInstrument, Double?) -> Unit,
    onSubmit: (Double?) -> Unit
) {
    val liveQuote = quotes[orderDraft.symbol]
    val livePrice = liveQuote?.price
    val effectiveEntry = orderDraft.entryPriceText.toDoubleOrNull() ?: livePrice ?: 0.0
    val qty = orderDraft.quantityText.toDoubleOrNull() ?: 0.0
    val estNotional = qty * effectiveEntry

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, BorderSlate),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Virtual Order Ticket",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Instrument selector pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                instruments.forEach { inst ->
                    val selected = inst.symbol == orderDraft.symbol
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) ElevatedSurface else ObsidianBg,
                        border = BorderStroke(1.dp, if (selected) CyanAccent else BorderSlate),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectInstrument(inst, quotes[inst.symbol]?.price) }
                    ) {
                        Text(
                            text = inst.shortTicker,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) CyanAccent else TextSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            // Side Selector (LONG / SHORT)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TradeSide.entries.forEach { side ->
                    val selected = orderDraft.side == side
                    val accent = if (side == TradeSide.LONG) BullGreen else BearRed
                    val bg = if (side == TradeSide.LONG) BullGreenDark else BearRedDark
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) bg else ElevatedSurface,
                        border = BorderStroke(1.dp, if (selected) accent else BorderSlate),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onUpdateOrderDraft { it.copy(side = side) } }
                            .testTag("order_side_${side.name}")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 11.dp)
                        ) {
                            Text(
                                text = side.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) accent else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = orderDraft.quantityText,
                    onValueChange = { txt -> onUpdateOrderDraft { it.copy(quantityText = txt) } },
                    label = { Text("Quantity (Units)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = BorderSlate
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("order_quantity_input")
                )

                OutlinedTextField(
                    value = orderDraft.entryPriceText,
                    onValueChange = { txt -> onUpdateOrderDraft { it.copy(entryPriceText = txt) } },
                    label = {
                        Text(
                            if (livePrice != null) "Price (Live ${TradingFormatters.formatPrice(livePrice, 2)})"
                            else "Entry Price"
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = BorderSlate
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("order_entry_input")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = orderDraft.stopLossText,
                    onValueChange = { txt -> onUpdateOrderDraft { it.copy(stopLossText = txt) } },
                    label = { Text("Stop Loss (Opt)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BearRed,
                        unfocusedBorderColor = BorderSlate
                    ),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = orderDraft.takeProfitText,
                    onValueChange = { txt -> onUpdateOrderDraft { it.copy(takeProfitText = txt) } },
                    label = { Text("Take Profit (Opt)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BullGreen,
                        unfocusedBorderColor = BorderSlate
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = orderDraft.notes,
                onValueChange = { txt -> onUpdateOrderDraft { it.copy(notes = txt) } },
                label = { Text("Setup Thesis / Notes") },
                maxLines = 2,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanAccent,
                    unfocusedBorderColor = BorderSlate
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Est. Notional: ${TradingFormatters.formatUsd(estNotional)}",
                    style = MonoLabelSmall,
                    color = if (estNotional > availableBalance) BearRed else TextSecondary
                )
                Text(
                    text = "Cash: ${TradingFormatters.formatUsd(availableBalance)}",
                    style = MonoLabelSmall,
                    color = TextSecondary
                )
            }

            Button(
                onClick = { onSubmit(null) },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (orderDraft.side == TradeSide.LONG) BullGreen else BearRed,
                    contentColor = ObsidianBg
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("submit_paper_order_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text = "Execute Virtual ${orderDraft.side.name} Order",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPaperState(
    title: String,
    subtitle: String,
    ctaText: String,
    onCtaClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, BorderSlate),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(onClick = onCtaClick) {
                Text(ctaText)
            }
        }
    }
}

@Composable
private fun EditSlTpDialog(
    position: PaperPosition,
    onDismiss: () -> Unit,
    onSave: (Double?, Double?, String) -> Unit
) {
    var slText by remember { mutableStateOf(position.stopLoss?.toString().orEmpty()) }
    var tpText by remember { mutableStateOf(position.takeProfit?.toString().orEmpty()) }
    var notes by remember { mutableStateOf(position.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modify ${position.symbol}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = slText,
                    onValueChange = { slText = it },
                    label = { Text("Stop Loss") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = tpText,
                    onValueChange = { tpText = it },
                    label = { Text("Take Profit") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        slText.toDoubleOrNull(),
                        tpText.toDoubleOrNull(),
                        notes
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ClosePositionDialog(
    position: PaperPosition,
    markPrice: Double,
    onDismiss: () -> Unit,
    onConfirmClose: (Double, Boolean, String, String) -> Unit
) {
    var exitText by remember { mutableStateOf(String.format("%.4f", markPrice)) }
    var autoLog by remember { mutableStateOf(true) }
    var strategyTag by remember { mutableStateOf("AI Signal") }
    var emotionTag by remember { mutableStateOf("Disciplined") }

    val parsedExit = exitText.toDoubleOrNull() ?: markPrice
    val estPnl = position.computeUnrealizedPnl(parsedExit)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Close ${position.symbol} ${position.parsedSide.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Estimated Realized P&L: ${TradingFormatters.formatSignedUsd(estPnl)}",
                    style = MonoPriceMedium,
                    color = if (estPnl >= 0) BullGreen else BearRed
                )
                OutlinedTextField(
                    value = exitText,
                    onValueChange = { exitText = it },
                    label = { Text("Exit Price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = autoLog, onCheckedChange = { autoLog = it })
                    Text("Auto-log closed trade to Journal", style = MaterialTheme.typography.bodySmall)
                }
                if (autoLog) {
                    OutlinedTextField(
                        value = strategyTag,
                        onValueChange = { strategyTag = it },
                        label = { Text("Strategy Tag") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = emotionTag,
                        onValueChange = { emotionTag = it },
                        label = { Text("Mindset / Emotion Tag") },
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmClose(parsedExit, autoLog, strategyTag, emotionTag) },
                colors = ButtonDefaults.buttonColors(containerColor = BearRed)
            ) {
                Text("Confirm Close")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
