package com.example.data

import com.example.domain.model.MarketChartData
import com.example.domain.model.MarketInstrument
import com.example.domain.model.MarketQuote
import com.example.domain.model.Timeframe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class WatchlistFetchResult(
    val quotes: Map<String, MarketQuote>,
    val errorMessage: String?
)

class MarketRepository(
    private val dataSource: MarketDataSource = MarketDataSource()
) {
    suspend fun fetchChart(
        instrument: MarketInstrument,
        timeframe: Timeframe
    ): Result<MarketChartData> {
        return dataSource.fetchChartData(instrument, timeframe)
    }

    suspend fun fetchWatchlistQuotes(instruments: List<MarketInstrument>): WatchlistFetchResult =
        coroutineScope {
            val jobs = instruments.map { instrument ->
                async {
                    instrument.symbol to dataSource.fetchChartData(instrument, Timeframe.D1)
                }
            }
            val outcomes = jobs.awaitAll()
            val quotesMap = linkedMapOf<String, MarketQuote>()
            var firstError: String? = null

            for ((symbol, result) in outcomes) {
                result.onSuccess { chart ->
                    quotesMap[symbol] = chart.quote
                }.onFailure { err ->
                    if (firstError == null) {
                        firstError = err.localizedMessage ?: "Failed to load market quote for $symbol"
                    }
                }
            }
            WatchlistFetchResult(
                quotes = quotesMap,
                errorMessage = if (quotesMap.isEmpty()) firstError else null
            )
        }
}
