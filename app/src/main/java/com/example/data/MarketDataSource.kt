package com.example.data

import com.example.domain.model.AssetClass
import com.example.domain.model.Candle
import com.example.domain.model.MarketChartData
import com.example.domain.model.MarketInstrument
import com.example.domain.model.MarketQuote
import com.example.domain.model.Timeframe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class MarketDataSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    suspend fun fetchChartData(
        instrument: MarketInstrument,
        timeframe: Timeframe
    ): Result<MarketChartData> = withContext(Dispatchers.IO) {
        // Primary: Yahoo Finance v8 (supports Crypto, Forex, Stocks, Gold)
        val yahooAttempt = fetchFromYahoo(instrument, timeframe)
        if (yahooAttempt.isSuccess) return@withContext yahooAttempt

        // Secondary fallback for Crypto: Coinbase Exchange public candles
        if (instrument.assetClass == AssetClass.CRYPTO && instrument.coinbasePair != null) {
            val coinbaseAttempt = fetchFromCoinbase(instrument, timeframe)
            if (coinbaseAttempt.isSuccess) return@withContext coinbaseAttempt
        }

        // Secondary fallback for Forex daily/swing: Frankfurter ECB API
        if (instrument.assetClass == AssetClass.FOREX && instrument.frankfurterPair != null) {
            val frankfurterAttempt = fetchFromFrankfurter(instrument, timeframe)
            if (frankfurterAttempt.isSuccess) return@withContext frankfurterAttempt
        }

        val err = yahooAttempt.exceptionOrNull()
            ?: IOException("Unable to load live market data for ${instrument.symbol}")
        Result.failure(err)
    }

    private fun fetchFromYahoo(
        instrument: MarketInstrument,
        timeframe: Timeframe
    ): Result<MarketChartData> {
        val encodedSymbol = URLEncoder.encode(instrument.symbol, "UTF-8")
        val hosts = listOf("query1.finance.yahoo.com", "query2.finance.yahoo.com")
        var lastException: Exception? = null

        for (host in hosts) {
            val url =
                "https://$host/v8/finance/chart/$encodedSymbol?interval=${timeframe.yahooInterval}&range=${timeframe.yahooRange}&includePrePost=false"
            try {
                val request = Request.Builder()
                    .url(url)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                    )
                    .header("Accept", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IOException("Market feed HTTP ${response.code} from $host")
                    }
                    val parsed = parseYahooResponse(body, instrument, timeframe)
                    return Result.success(parsed)
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        return Result.failure(
            lastException ?: IOException("Market feed unavailable for ${instrument.symbol}")
        )
    }

    private fun parseYahooResponse(
        jsonString: String,
        instrument: MarketInstrument,
        timeframe: Timeframe
    ): MarketChartData {
        val root = JSONObject(jsonString)
        val chart = root.getJSONObject("chart")
        val errorObj = chart.optJSONObject("error")
        if (errorObj != null) {
            val desc = errorObj.optString("description", "Unknown ticker error")
            throw IOException("Market API error for ${instrument.symbol}: $desc")
        }

        val results = chart.optJSONArray("result")
            ?: throw IOException("No market data returned for ${instrument.symbol}")
        if (results.length() == 0) {
            throw IOException("Empty result for ${instrument.symbol}")
        }

        val resultObj = results.getJSONObject(0)
        val meta = resultObj.getJSONObject("meta")
        val timestamps = resultObj.optJSONArray("timestamp") ?: JSONArray()
        val indicators = resultObj.optJSONObject("indicators")
        val quoteArr = indicators?.optJSONArray("quote")
        val quoteObj = if (quoteArr != null && quoteArr.length() > 0) quoteArr.getJSONObject(0) else null

        val opens = quoteObj?.optJSONArray("open")
        val highs = quoteObj?.optJSONArray("high")
        val lows = quoteObj?.optJSONArray("low")
        val closes = quoteObj?.optJSONArray("close")
        val volumes = quoteObj?.optJSONArray("volume")

        val rawCandles = mutableListOf<Candle>()
        for (i in 0 until timestamps.length()) {
            val o = opens?.optDouble(i, Double.NaN) ?: Double.NaN
            val h = highs?.optDouble(i, Double.NaN) ?: Double.NaN
            val l = lows?.optDouble(i, Double.NaN) ?: Double.NaN
            val c = closes?.optDouble(i, Double.NaN) ?: Double.NaN
            val v = volumes?.optDouble(i, 0.0)?.let { if (it.isNaN()) 0.0 else it } ?: 0.0
            if (!o.isNaN() && !h.isNaN() && !l.isNaN() && !c.isNaN() && o > 0.0 && c > 0.0) {
                rawCandles.add(
                    Candle(
                        timestampMs = timestamps.getLong(i) * 1000L,
                        open = o,
                        high = h,
                        low = l,
                        close = c,
                        volume = v
                    )
                )
            }
        }

        val candles = if (timeframe.resampleTo4h && rawCandles.isNotEmpty()) {
            resampleCandles(rawCandles, bucketSize = 4)
        } else {
            rawCandles
        }

        if (candles.isEmpty()) {
            throw IOException("No valid OHLC candles available for ${instrument.symbol}")
        }

        val regularPrice = meta.optDouble("regularMarketPrice", Double.NaN)
            .let { if (it.isNaN() || it <= 0.0) candles.last().close else it }
        val prevClose = meta.optDouble("chartPreviousClose", Double.NaN)
            .let {
                if (it.isNaN() || it <= 0.0) {
                    meta.optDouble("previousClose", candles.first().open)
                } else it
            }
            .let { if (it.isNaN() || it <= 0.0) candles.first().open else it }

        val dayHigh = meta.optDouble("regularMarketDayHigh", Double.NaN)
            .let { if (it.isNaN() || it <= 0.0) candles.takeLast(24).maxOf { c -> c.high } else it }
        val dayLow = meta.optDouble("regularMarketDayLow", Double.NaN)
            .let { if (it.isNaN() || it <= 0.0) candles.takeLast(24).minOf { c -> c.low } else it }

        val changeAmt = regularPrice - prevClose
        val changePct = if (prevClose > 0.0) (changeAmt / prevClose) * 100.0 else 0.0
        val exchange = meta.optString("exchangeName", "Yahoo Finance").ifBlank { "Yahoo Finance" }

        val quote = MarketQuote(
            instrument = instrument,
            price = regularPrice,
            previousClose = prevClose,
            dayHigh = dayHigh,
            dayLow = dayLow,
            changeAmount = changeAmt,
            changePercent = changePct,
            sourceName = exchange,
            updatedAtMs = System.currentTimeMillis()
        )

        return MarketChartData(
            instrument = instrument,
            timeframe = timeframe,
            quote = quote,
            candles = candles.takeLast(160)
        )
    }

    private fun fetchFromCoinbase(
        instrument: MarketInstrument,
        timeframe: Timeframe
    ): Result<MarketChartData> {
        val pair = instrument.coinbasePair ?: return Result.failure(IOException("No Coinbase pair"))
        val gran = timeframe.coinbaseGranularitySec ?: 3600
        val url = "https://api.exchange.coinbase.com/products/$pair/candles?granularity=$gran"
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "TradePulseAndroid/1.0")
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Coinbase HTTP ${response.code}")
                }
                val arr = JSONArray(body)
                val raw = mutableListOf<Candle>()
                for (i in 0 until arr.length()) {
                    val row = arr.getJSONArray(i)
                    // Coinbase format: [ time, low, high, open, close, volume ]
                    raw.add(
                        Candle(
                            timestampMs = row.getLong(0) * 1000L,
                            low = row.getDouble(1),
                            high = row.getDouble(2),
                            open = row.getDouble(3),
                            close = row.getDouble(4),
                            volume = row.getDouble(5)
                        )
                    )
                }
                val sorted = raw.sortedBy { it.timestampMs }
                val candles = if (timeframe.resampleTo4h) resampleCandles(sorted, 4) else sorted
                if (candles.isEmpty()) throw IOException("Empty Coinbase candle response")

                val latest = candles.last().close
                val prev = candles.first().open
                val changeAmt = latest - prev
                val changePct = if (prev > 0.0) (changeAmt / prev) * 100.0 else 0.0
                val quote = MarketQuote(
                    instrument = instrument,
                    price = latest,
                    previousClose = prev,
                    dayHigh = candles.takeLast(24).maxOf { it.high },
                    dayLow = candles.takeLast(24).minOf { it.low },
                    changeAmount = changeAmt,
                    changePercent = changePct,
                    sourceName = "Coinbase Exchange",
                    updatedAtMs = System.currentTimeMillis()
                )
                Result.success(
                    MarketChartData(
                        instrument = instrument,
                        timeframe = timeframe,
                        quote = quote,
                        candles = candles.takeLast(160)
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchFromFrankfurter(
        instrument: MarketInstrument,
        timeframe: Timeframe
    ): Result<MarketChartData> {
        val (base, target) = instrument.frankfurterPair
            ?: return Result.failure(IOException("No Frankfurter pair"))
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            add(Calendar.DAY_OF_YEAR, -90)
        }
        val startDate = fmt.format(cal.time)
        val url = "https://api.frankfurter.dev/v1/$startDate..?base=$base&symbols=$target"
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "TradePulseAndroid/1.0")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException("Frankfurter HTTP ${response.code}")
                val json = JSONObject(body)
                val rates = json.getJSONObject("rates")
                val keys = rates.keys().asSequence().toList().sorted()
                val candles = mutableListOf<Candle>()
                var prevRate: Double? = null
                for (dateKey in keys) {
                    val dayRate = rates.getJSONObject(dateKey).optDouble(target, Double.NaN)
                    if (dayRate.isNaN() || dayRate <= 0.0) continue
                    val open = prevRate ?: dayRate
                    val ts = fmt.parse(dateKey)?.time ?: System.currentTimeMillis()
                    candles.add(
                        Candle(
                            timestampMs = ts,
                            open = open,
                            high = maxOf(open, dayRate),
                            low = minOf(open, dayRate),
                            close = dayRate,
                            volume = 0.0
                        )
                    )
                    prevRate = dayRate
                }
                if (candles.isEmpty()) throw IOException("No FX rates returned")
                val latest = candles.last().close
                val prev = if (candles.size >= 2) candles[candles.size - 2].close else candles.first().open
                val changeAmt = latest - prev
                val changePct = if (prev > 0.0) (changeAmt / prev) * 100.0 else 0.0
                val quote = MarketQuote(
                    instrument = instrument,
                    price = latest,
                    previousClose = prev,
                    dayHigh = candles.takeLast(14).maxOf { it.high },
                    dayLow = candles.takeLast(14).minOf { it.low },
                    changeAmount = changeAmt,
                    changePercent = changePct,
                    sourceName = "ECB / Frankfurter (Daily)",
                    updatedAtMs = System.currentTimeMillis()
                )
                Result.success(
                    MarketChartData(
                        instrument = instrument,
                        timeframe = timeframe,
                        quote = quote,
                        candles = candles
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun resampleCandles(candles: List<Candle>, bucketSize: Int): List<Candle> {
        if (candles.size <= bucketSize) return candles
        return candles.chunked(bucketSize).map { chunk ->
            Candle(
                timestampMs = chunk.first().timestampMs,
                open = chunk.first().open,
                high = chunk.maxOf { it.high },
                low = chunk.minOf { it.low },
                close = chunk.last().close,
                volume = chunk.sumOf { it.volume }
            )
        }
    }
}
