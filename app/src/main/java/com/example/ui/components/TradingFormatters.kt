package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AssetClass
import com.example.domain.model.SignalDirection
import com.example.ui.theme.BearRed
import com.example.ui.theme.BearRedDark
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.BullGreen
import com.example.ui.theme.BullGreenDark
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanDark
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldDark
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.MonoLabelSmall
import com.example.ui.theme.TextSecondary
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TradingFormatters {
    private val usdFormat = NumberFormat.getCurrencyInstance(Locale.US).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }

    fun formatPrice(value: Double, decimals: Int = 2): String {
        val safeDecimals = decimals.coerceIn(2, 6)
        return String.format(Locale.US, "%,.${safeDecimals}f", value)
    }

    fun formatUsd(value: Double): String {
        val absFormatted = usdFormat.format(kotlin.math.abs(value))
        return if (value < 0) "-$absFormatted" else absFormatted
    }

    fun formatSignedUsd(value: Double): String {
        val absFormatted = usdFormat.format(kotlin.math.abs(value))
        return when {
            value > 0.0 -> "+$absFormatted"
            value < 0.0 -> "-$absFormatted"
            else -> absFormatted
        }
    }

    fun formatSignedPercent(value: Double): String {
        val sign = if (value > 0) "+" else ""
        return "$sign${String.format(Locale.US, "%.2f", value)}%"
    }

    fun formatVolume(vol: Double): String = when {
        vol >= 1_000_000_000.0 -> String.format(Locale.US, "%.2fB", vol / 1_000_000_000.0)
        vol >= 1_000_000.0 -> String.format(Locale.US, "%.2fM", vol / 1_000_000.0)
        vol >= 1_000.0 -> String.format(Locale.US, "%.1fK", vol / 1_000.0)
        else -> String.format(Locale.US, "%.0f", vol)
    }

    fun formatTime(timestampMs: Long): String {
        val fmt = SimpleDateFormat("MMM dd, HH:mm", Locale.US)
        return fmt.format(Date(timestampMs))
    }

    fun formatClock(timestampMs: Long): String {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        return fmt.format(Date(timestampMs))
    }
}

@Composable
fun AssetClassBadge(assetClass: AssetClass, modifier: Modifier = Modifier) {
    val (bg, fg) = when (assetClass) {
        AssetClass.CRYPTO -> CyanDark to CyanAccent
        AssetClass.FOREX -> Color(0xFF1E1B4B) to IndigoAccent
        AssetClass.STOCKS -> BullGreenDark to BullGreen
        AssetClass.GOLD -> GoldDark to GoldAccent
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = bg,
        border = BorderStroke(0.5.dp, fg.copy(alpha = 0.35f))
    ) {
        Text(
            text = assetClass.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun PriceChangePill(
    changePercent: Double,
    modifier: Modifier = Modifier
) {
    val isUp = changePercent >= 0.0
    val bg = if (isUp) BullGreenDark else BearRedDark
    val fg = if (isUp) BullGreen else BearRed
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = BorderStroke(0.5.dp, fg.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isUp) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = if (isUp) "Up" else "Down",
                tint = fg,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = TradingFormatters.formatSignedPercent(changePercent),
                style = MonoLabelSmall,
                color = fg,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SignalBadge(
    direction: SignalDirection,
    confidence: Int? = null,
    modifier: Modifier = Modifier
) {
    val (bg, fg, icon) = when (direction) {
        SignalDirection.BUY -> Triple(BullGreenDark, BullGreen, Icons.Filled.ArrowUpward)
        SignalDirection.SELL -> Triple(BearRedDark, BearRed, Icons.Filled.ArrowDownward)
        SignalDirection.HOLD -> Triple(GoldDark, GoldAccent, Icons.Filled.Pause)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = direction.name,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (confidence != null) "${direction.name} • $confidence%" else direction.name,
                style = MonoLabelSmall,
                color = fg,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun VirtualTradingSafetyBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = GoldDark.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = "Paper trading safety guard",
                tint = GoldAccent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "SIMULATED PAPER TRADING ONLY • Virtual USD balances. Live real-money order execution is strictly disabled.",
                style = MaterialTheme.typography.bodySmall,
                color = GoldAccent
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            androidx.compose.foundation.layout.Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun DividerHairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(BorderSlate.copy(alpha = 0.55f))
            .size(height = 1.dp, width = 1.dp)
    )
}
