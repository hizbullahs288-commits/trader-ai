package com.example.domain.model

enum class AssetClass(val label: String) {
    CRYPTO("Crypto"),
    FOREX("Forex"),
    STOCKS("Stocks"),
    GOLD("Gold");

    companion object {
        fun fromRaw(raw: String?): AssetClass =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: STOCKS
    }
}

enum class Timeframe(
    val label: String,
    val yahooInterval: String,
    val yahooRange: String,
    val coinbaseGranularitySec: Int? = null,
    val resampleTo4h: Boolean = false
) {
    M1("1m", "1m", "1d", coinbaseGranularitySec = 60),
    M5("5m", "5m", "5d", coinbaseGranularitySec = 300),
    M15("15m", "15m", "5d", coinbaseGranularitySec = 900),
    H1("1h", "1h", "1mo", coinbaseGranularitySec = 3600),
    H4("4h", "1h", "3mo", coinbaseGranularitySec = 3600, resampleTo4h = true),
    D1("1D", "1d", "1y", coinbaseGranularitySec = 86400),
    W1("1W", "1wk", "2y", coinbaseGranularitySec = null)
}

data class MarketInstrument(
    val symbol: String,
    val displayName: String,
    val shortTicker: String,
    val assetClass: AssetClass,
    val priceDecimals: Int = 2,
    val coinbasePair: String? = null,
    val frankfurterPair: Pair<String, String>? = null
)

data class Candle(
    val timestampMs: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
) {
    val isBullish: Boolean get() = close >= open
}

data class MarketQuote(
    val instrument: MarketInstrument,
    val price: Double,
    val previousClose: Double,
    val dayHigh: Double,
    val dayLow: Double,
    val changeAmount: Double,
    val changePercent: Double,
    val sourceName: String,
    val updatedAtMs: Long
)

data class MarketChartData(
    val instrument: MarketInstrument,
    val timeframe: Timeframe,
    val quote: MarketQuote,
    val candles: List<Candle>
)

object WatchlistDefaults {
    val instruments: List<MarketInstrument> = listOf(
        // Crypto
        MarketInstrument("BTC-USD", "Bitcoin / USD", "BTC", AssetClass.CRYPTO, 2, coinbasePair = "BTC-USD"),
        MarketInstrument("ETH-USD", "Ethereum / USD", "ETH", AssetClass.CRYPTO, 2, coinbasePair = "ETH-USD"),
        MarketInstrument("SOL-USD", "Solana / USD", "SOL", AssetClass.CRYPTO, 2, coinbasePair = "SOL-USD"),
        MarketInstrument("XRP-USD", "Ripple / USD", "XRP", AssetClass.CRYPTO, 4, coinbasePair = "XRP-USD"),
        // Forex
        MarketInstrument("EURUSD=X", "Euro / US Dollar", "EUR/USD", AssetClass.FOREX, 5, frankfurterPair = "EUR" to "USD"),
        MarketInstrument("GBPUSD=X", "British Pound / USD", "GBP/USD", AssetClass.FOREX, 5, frankfurterPair = "GBP" to "USD"),
        MarketInstrument("USDJPY=X", "US Dollar / Japanese Yen", "USD/JPY", AssetClass.FOREX, 3, frankfurterPair = "USD" to "JPY"),
        MarketInstrument("AUDUSD=X", "Australian Dollar / USD", "AUD/USD", AssetClass.FOREX, 5, frankfurterPair = "AUD" to "USD"),
        // Stocks
        MarketInstrument("AAPL", "Apple Inc.", "AAPL", AssetClass.STOCKS, 2),
        MarketInstrument("NVDA", "NVIDIA Corp.", "NVDA", AssetClass.STOCKS, 2),
        MarketInstrument("MSFT", "Microsoft Corp.", "MSFT", AssetClass.STOCKS, 2),
        MarketInstrument("TSLA", "Tesla Inc.", "TSLA", AssetClass.STOCKS, 2),
        MarketInstrument("SPY", "SPDR S&P 500 ETF", "SPY", AssetClass.STOCKS, 2),
        // Gold
        MarketInstrument("GC=F", "Gold Futures (COMEX)", "XAU/FUT", AssetClass.GOLD, 2),
        MarketInstrument("XAUUSD=X", "Gold Spot / USD", "XAU/USD", AssetClass.GOLD, 2),
        MarketInstrument("GLD", "SPDR Gold Shares ETF", "GLD", AssetClass.GOLD, 2)
    )

    fun findBySymbol(symbol: String): MarketInstrument? =
        instruments.firstOrNull { it.symbol.equals(symbol.trim(), ignoreCase = true) }

    fun createCustomInstrument(rawSymbol: String): MarketInstrument {
        val clean = rawSymbol.trim().uppercase()
        val inferredClass = when {
            clean.contains("XAU") || clean == "GC=F" || clean == "GLD" -> AssetClass.GOLD
            clean.endsWith("=X") -> AssetClass.FOREX
            clean.endsWith("-USD") || clean.endsWith("USDT") -> AssetClass.CRYPTO
            else -> AssetClass.STOCKS
        }
        val decimals = when (inferredClass) {
            AssetClass.FOREX -> if (clean.contains("JPY")) 3 else 5
            else -> 2
        }
        return MarketInstrument(
            symbol = clean,
            displayName = clean,
            shortTicker = clean.removeSuffix("=X"),
            assetClass = inferredClass,
            priceDecimals = decimals
        )
    }
}
