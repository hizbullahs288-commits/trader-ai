package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.AssetClass
import com.example.domain.model.TradeSide
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
import com.example.ui.theme.GoldDark
import com.example.ui.theme.MonoLabelSmall
import com.example.ui.theme.MonoPriceLarge
import com.example.ui.theme.MonoPriceMedium
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.viewmodel.RiskCalculatorUiState
import java.util.Locale

@Composable
fun RiskCalculatorScreen(
    uiState: RiskCalculatorUiState,
    paperAccountBalance: Double?,
    onBalanceChange: (String) -> Unit,
    onRiskPercentChange: (String) -> Unit,
    onEntryPriceChange: (String) -> Unit,
    onStopLossChange: (String) -> Unit,
    onTakeProfitChange: (String) -> Unit,
    onAssetClassChange: (AssetClass) -> Unit,
    onSendToPaperTrade: (String, AssetClass, TradeSide, Double, Double, Double?, Double?) -> Unit,
    modifier: Modifier = Modifier
) {
    val result = uiState.result

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("risk_calculator_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Risk & Position Sizing",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Institutional position sizing grounded in fixed fractional equity risk.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        // Inputs Card
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
                    // Asset Class Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssetClass.entries.forEach { ac ->
                            val selected = uiState.assetClass == ac
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) BullGreenDark else ElevatedSurface,
                                border = BorderStroke(1.dp, if (selected) BullGreen else BorderSlate),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onAssetClassChange(ac) }
                            ) {
                                Text(
                                    text = ac.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) BullGreen else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = uiState.balanceText,
                            onValueChange = onBalanceChange,
                            label = { Text("Account Equity ($)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanAccent,
                                unfocusedBorderColor = BorderSlate
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("risk_balance_input")
                        )

                        if (paperAccountBalance != null) {
                            OutlinedButton(
                                onClick = {
                                    onBalanceChange(String.format(Locale.US, "%.2f", paperAccountBalance))
                                },
                                border = BorderStroke(1.dp, CyanAccent),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(54.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Sync,
                                    contentDescription = "Sync paper equity",
                                    tint = CyanAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Paper", color = CyanAccent)
                            }
                        }
                    }

                    // Quick Risk % Chips + Custom
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Risk per Trade (%)",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("0.5", "1.0", "2.0", "3.0").forEach { pct ->
                                val active = uiState.riskPercentText == pct
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (active) BullGreenDark else ElevatedSurface,
                                    border = BorderStroke(1.dp, if (active) BullGreen else BorderSlate),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onRiskPercentChange(pct) }
                                ) {
                                    Text(
                                        text = "$pct%",
                                        style = MonoLabelSmall,
                                        color = if (active) BullGreen else TextSecondary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = uiState.riskPercentText,
                                onValueChange = onRiskPercentChange,
                                label = { Text("Custom %") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyanAccent,
                                    unfocusedBorderColor = BorderSlate
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("risk_percent_input")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = uiState.entryPriceText,
                        onValueChange = onEntryPriceChange,
                        label = { Text("Entry Price (${uiState.symbol})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = BorderSlate
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("risk_entry_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.stopLossText,
                            onValueChange = onStopLossChange,
                            label = { Text("Stop Loss") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BearRed,
                                unfocusedBorderColor = BorderSlate
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("risk_sl_input")
                        )

                        OutlinedTextField(
                            value = uiState.takeProfitText,
                            onValueChange = onTakeProfitChange,
                            label = { Text("Take Profit (Opt)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BullGreen,
                                unfocusedBorderColor = BorderSlate
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("risk_tp_input")
                        )
                    }
                }
            }
        }

        // Results Card
        item {
            if (!result.isValid) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, BorderSlate),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = result.validationError ?: "Enter Entry and Stop-Loss prices to compute position size.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "RECOMMENDED POSITION SIZE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanAccent
                                )
                                Text(
                                    text = "${String.format(Locale.US, "%.4f", result.positionUnits).trimEnd('0').trimEnd('.')} units",
                                    style = MonoPriceLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("risk_output_units")
                                )
                                result.forexStandardLots?.let { lots ->
                                    Text(
                                        text = "${String.format(Locale.US, "%.3f", lots)} standard lots",
                                        style = MonoLabelSmall,
                                        color = CyanAccent
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (result.side == TradeSide.LONG) BullGreenDark else BearRedDark
                            ) {
                                Text(
                                    text = result.side.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (result.side == TradeSide.LONG) BullGreen else BearRed,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RiskOutputTile(
                                label = "Risk Amount",
                                value = TradingFormatters.formatUsd(result.riskAmountUsd),
                                color = BearRed,
                                modifier = Modifier.weight(1f)
                            )
                            RiskOutputTile(
                                label = "Stop Distance",
                                value = "${String.format(Locale.US, "%.2f", result.stopDistancePercent)}%",
                                color = GoldAccent,
                                modifier = Modifier.weight(1f)
                            )
                            RiskOutputTile(
                                label = "Notional Value",
                                value = TradingFormatters.formatUsd(result.notionalValueUsd),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (result.riskRewardRatio > 0.0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RiskOutputTile(
                                    label = "Reward Target",
                                    value = TradingFormatters.formatUsd(result.potentialRewardUsd),
                                    color = BullGreen,
                                    modifier = Modifier.weight(1f)
                                )
                                RiskOutputTile(
                                    label = "Risk : Reward",
                                    value = "1 : ${String.format(Locale.US, "%.2f", result.riskRewardRatio)}",
                                    color = if (result.riskRewardRatio >= 1.5) BullGreen else GoldAccent,
                                    modifier = Modifier.weight(1f)
                                )
                                RiskOutputTile(
                                    label = "Breakeven WR",
                                    value = "${String.format(Locale.US, "%.1f", result.breakEvenWinRatePercent)}%",
                                    color = CyanAccent,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        result.warnings.forEach { warning ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = GoldDark,
                                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = "Risk warning",
                                        tint = GoldAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = warning,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GoldAccent
                                    )
                                }
                            }
                        }

                        val entryVal = uiState.entryPriceText.toDoubleOrNull() ?: 0.0
                        val slVal = uiState.stopLossText.toDoubleOrNull()
                        val tpVal = uiState.takeProfitText.toDoubleOrNull()
                        Button(
                            onClick = {
                                onSendToPaperTrade(
                                    uiState.symbol,
                                    uiState.assetClass,
                                    result.side,
                                    entryVal,
                                    result.positionUnits,
                                    slVal,
                                    tpVal
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (result.side == TradeSide.LONG) BullGreen else BearRed,
                                contentColor = ObsidianBg
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("risk_to_paper_trade_button")
                        ) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send Sized Order to Paper Trade", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskOutputTile(
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
            Text(text = value, style = MonoPriceMedium, color = color)
        }
    }
}
