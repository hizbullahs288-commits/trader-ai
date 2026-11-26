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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.domain.model.AssetClass
import com.example.domain.model.JournalEntry
import com.example.domain.model.PortfolioAnalytics
import com.example.domain.model.TradeSide
import com.example.ui.components.AssetClassBadge
import com.example.ui.components.EquityCurveCanvas
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
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.viewmodel.DataStreamState
import java.util.Locale

@Composable
fun JournalAnalyticsScreen(
    journalState: DataStreamState<List<JournalEntry>>,
    analytics: PortfolioAnalytics,
    onAddEntry: (String, AssetClass, TradeSide, Double, Double, Double, String, String, String) -> Unit,
    onUpdateEntry: (String, String, String, String) -> Unit,
    onDeleteEntry: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<JournalEntry?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ObsidianBg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = BullGreen,
                contentColor = ObsidianBg,
                modifier = Modifier.testTag("add_journal_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Log trade in journal")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "Journal & Analytics",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Track execution quality, equity curve, win rate, and trading psychology.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Performance Analytics Card
            item {
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Equity Curve & Metrics",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${analytics.totalEntries} Logged",
                                style = MonoLabelSmall,
                                color = CyanAccent
                            )
                        }

                        EquityCurveCanvas(points = analytics.equityCurve)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AnalyticsStatTile(
                                label = "Win Rate",
                                value = "${String.format(Locale.US, "%.1f", analytics.winRatePercent)}%",
                                color = if (analytics.winRatePercent >= 50.0) BullGreen else GoldAccent,
                                modifier = Modifier.weight(1f)
                            )
                            AnalyticsStatTile(
                                label = "Profit Factor",
                                value = String.format(Locale.US, "%.2f", analytics.profitFactor),
                                color = if (analytics.profitFactor >= 1.2) BullGreen else BearRed,
                                modifier = Modifier.weight(1f)
                            )
                            AnalyticsStatTile(
                                label = "Net P&L",
                                value = TradingFormatters.formatSignedUsd(analytics.netRealizedPnl),
                                color = if (analytics.netRealizedPnl >= 0) BullGreen else BearRed,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AnalyticsStatTile(
                                label = "Avg Win",
                                value = TradingFormatters.formatUsd(analytics.averageWin),
                                color = BullGreen,
                                modifier = Modifier.weight(1f)
                            )
                            AnalyticsStatTile(
                                label = "Avg Loss",
                                value = TradingFormatters.formatUsd(analytics.averageLoss),
                                color = BearRed,
                                modifier = Modifier.weight(1f)
                            )
                            AnalyticsStatTile(
                                label = "Best Trade",
                                value = TradingFormatters.formatSignedUsd(analytics.bestTradePnl),
                                color = BullGreen,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (analytics.strategyBreakdown.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Strategy Performance",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextSecondary
                                )
                                analytics.strategyBreakdown.take(4).forEach { perf ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${perf.tag} (${perf.trades})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${String.format(Locale.US, "%.0f", perf.winRatePercent)}% WR • ${TradingFormatters.formatSignedUsd(perf.totalPnl)}",
                                            style = MonoLabelSmall,
                                            color = if (perf.totalPnl >= 0) BullGreen else BearRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            when (journalState) {
                DataStreamState.Loading -> {
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
                }

                is DataStreamState.Error -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BearRedDark,
                            border = BorderStroke(1.dp, BearRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = journalState.message,
                                color = BearRed,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                is DataStreamState.Ready -> {
                    val entries = journalState.data
                    if (entries.isEmpty()) {
                        item {
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
                                    Text("No journal entries yet", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "Tap + to log a trade manually, or close a Paper Trade with auto-logging enabled.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    } else {
                        items(entries, key = { it.id }) { entry ->
                            JournalEntryCard(
                                entry = entry,
                                onEdit = { editingEntry = entry },
                                onDelete = { onDeleteEntry(entry.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddJournalDialog(
            onDismiss = { showAddDialog = false },
            onSave = { sym, ac, side, entryPx, exitPx, qty, strat, emo, notes ->
                onAddEntry(sym, ac, side, entryPx, exitPx, qty, strat, emo, notes)
                showAddDialog = false
            }
        )
    }

    editingEntry?.let { entry ->
        EditJournalDialog(
            entry = entry,
            onDismiss = { editingEntry = null },
            onSave = { strat, emo, notes ->
                onUpdateEntry(entry.id, strat, emo, notes)
                editingEntry = null
            }
        )
    }
}

@Composable
private fun AnalyticsStatTile(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = ElevatedSurface,
        border = BorderStroke(0.5.dp, BorderSlate)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, style = MonoLabelSmall, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun JournalEntryCard(
    entry: JournalEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isWin = entry.pnlAmount >= 0.0
    val decimals = if (entry.parsedAssetClass == AssetClass.FOREX) 5 else 2

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, BorderSlate),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        color = if (entry.parsedSide == TradeSide.LONG) BullGreenDark else BearRedDark
                    ) {
                        Text(
                            text = entry.parsedSide.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (entry.parsedSide == TradeSide.LONG) BullGreen else BearRed,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = entry.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    AssetClassBadge(assetClass = entry.parsedAssetClass)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = TradingFormatters.formatSignedUsd(entry.pnlAmount),
                        style = MonoPriceMedium,
                        color = if (isWin) BullGreen else BearRed
                    )
                    Text(
                        text = TradingFormatters.formatSignedPercent(entry.pnlPercent),
                        style = MonoLabelSmall,
                        color = if (isWin) BullGreen else BearRed
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "In: ${TradingFormatters.formatPrice(entry.entryPrice, decimals)}  Out: ${TradingFormatters.formatPrice(entry.exitPrice, decimals)}",
                    style = MonoLabelSmall,
                    color = TextSecondary
                )
                Text(
                    text = "Qty: ${String.format("%.4f", entry.quantity).trimEnd('0').trimEnd('.')}",
                    style = MonoLabelSmall,
                    color = TextSecondary
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(6.dp), color = ElevatedSurface) {
                    Text(
                        text = entry.strategyTag,
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Surface(shape = RoundedCornerShape(6.dp), color = ElevatedSurface) {
                    Text(
                        text = entry.emotionTag,
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit journal entry", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete journal entry", tint = BearRed, modifier = Modifier.size(18.dp))
                }
            }

            if (entry.notes.isNotBlank()) {
                Text(
                    text = entry.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun AddJournalDialog(
    onDismiss: () -> Unit,
    onSave: (String, AssetClass, TradeSide, Double, Double, Double, String, String, String) -> Unit
) {
    var symbol by remember { mutableStateOf("BTC-USD") }
    var assetClass by remember { mutableStateOf(AssetClass.CRYPTO) }
    var side by remember { mutableStateOf(TradeSide.LONG) }
    var entryText by remember { mutableStateOf("") }
    var exitText by remember { mutableStateOf("") }
    var qtyText by remember { mutableStateOf("1") }
    var strategyTag by remember { mutableStateOf("EMA Pullback") }
    var emotionTag by remember { mutableStateOf("Disciplined") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Trade to Journal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it },
                    label = { Text("Symbol") },
                    singleLine = true
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssetClass.entries.forEach { ac ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (assetClass == ac) BullGreenDark else ElevatedSurface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { assetClass = ac }
                        ) {
                            Text(
                                ac.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (assetClass == ac) BullGreen else TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TradeSide.entries.forEach { s ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (side == s) ElevatedSurface else ObsidianBg,
                            border = BorderStroke(1.dp, if (side == s) CyanAccent else BorderSlate),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { side = s }
                        ) {
                            Text(
                                s.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (side == s) CyanAccent else TextSecondary,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp)
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = entryText,
                        onValueChange = { entryText = it },
                        label = { Text("Entry") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = exitText,
                        onValueChange = { exitText = it },
                        label = { Text("Exit") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = strategyTag,
                        onValueChange = { strategyTag = it },
                        label = { Text("Strategy") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = emotionTag,
                        onValueChange = { emotionTag = it },
                        label = { Text("Mindset") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Lessons") },
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            val e = entryText.toDoubleOrNull()
            val x = exitText.toDoubleOrNull()
            val q = qtyText.toDoubleOrNull()
            Button(
                enabled = symbol.isNotBlank() && e != null && e > 0 && x != null && x > 0 && q != null && q > 0,
                onClick = {
                    if (e != null && x != null && q != null) {
                        onSave(symbol, assetClass, side, e, x, q, strategyTag, emotionTag, notes)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BullGreen, contentColor = ObsidianBg)
            ) {
                Text("Save Entry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditJournalDialog(
    entry: JournalEntry,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var strategyTag by remember { mutableStateOf(entry.strategyTag) }
    var emotionTag by remember { mutableStateOf(entry.emotionTag) }
    var notes by remember { mutableStateOf(entry.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${entry.symbol} Reflection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = strategyTag,
                    onValueChange = { strategyTag = it },
                    label = { Text("Strategy Tag") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = emotionTag,
                    onValueChange = { emotionTag = it },
                    label = { Text("Mindset Tag") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(strategyTag, emotionTag, notes) }) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
