package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AiAnalysisRepository
import com.example.data.MarketRepository
import com.example.domain.engine.TechnicalAnalysisEngine
import com.example.domain.model.AiAnalysisResult
import com.example.domain.model.AssetClass
import com.example.domain.model.MarketChartData
import com.example.domain.model.MarketInstrument
import com.example.domain.model.MarketQuote
import com.example.domain.model.TechnicalAnalysisBundle
import com.example.domain.model.Timeframe
import com.example.domain.model.WatchlistDefaults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface ChartLoadState {
    data object Loading : ChartLoadState
    data class Ready(
        val chartData: MarketChartData,
        val analysis: TechnicalAnalysisBundle
    ) : ChartLoadState
    data class Error(val message: String) : ChartLoadState
}

sealed interface AiUiState {
    data object Idle : AiUiState
    data object Loading : AiUiState
    data class Ready(val result: AiAnalysisResult) : AiUiState
    data class Error(val message: String) : AiUiState
}

sealed interface AiFollowUpState {
    data object Idle : AiFollowUpState
    data object Loading : AiFollowUpState
    data class Streaming(val text: String) : AiFollowUpState
    data class Complete(val text: String) : AiFollowUpState
    data class Error(val message: String) : AiFollowUpState
}

data class OverlayPreferences(
    val showEma20: Boolean = true,
    val showEma50: Boolean = true,
    val showBollinger: Boolean = false,
    val showSupportResistance: Boolean = true,
    val showVolume: Boolean = true,
    val showRsi: Boolean = true,
    val showMacd: Boolean = false
)

data class MarketDashboardUiState(
    val instruments: List<MarketInstrument> = WatchlistDefaults.instruments,
    val selectedCategory: AssetClass? = null, // null = All
    val searchQuery: String = "",
    val quotes: Map<String, MarketQuote> = emptyMap(),
    val isRefreshingQuotes: Boolean = false,
    val quotesError: String? = null,
    val autoRefreshEnabled: Boolean = true,
    val lastSyncedAtMs: Long? = null,
    val selectedInstrument: MarketInstrument = WatchlistDefaults.instruments.first(),
    val selectedTimeframe: Timeframe = Timeframe.H1,
    val chartState: ChartLoadState = ChartLoadState.Loading,
    val overlays: OverlayPreferences = OverlayPreferences(),
    val aiState: AiUiState = AiUiState.Idle,
    val aiFollowUpState: AiFollowUpState = AiFollowUpState.Idle
)

class MarketChartViewModel(
    private val marketRepository: MarketRepository = MarketRepository(),
    private val aiRepository: AiAnalysisRepository = AiAnalysisRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MarketDashboardUiState())
    val uiState: StateFlow<MarketDashboardUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var chartFetchJob: Job? = null
    private var aiJob: Job? = null
    private var followUpJob: Job? = null

    init {
        refreshWatchlist()
        loadSelectedChart()
        startLivePolling()
    }

    fun selectCategory(category: AssetClass?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun lookupCustomSymbol(rawSymbol: String) {
        val clean = rawSymbol.trim().uppercase()
        if (clean.isBlank()) return
        val existing = _uiState.value.instruments.firstOrNull {
            it.symbol.equals(clean, ignoreCase = true) || it.shortTicker.equals(clean, ignoreCase = true)
        }
        val target = existing ?: WatchlistDefaults.createCustomInstrument(clean)
        _uiState.update { state ->
            val updatedList = if (existing == null) listOf(target) + state.instruments else state.instruments
            state.copy(
                instruments = updatedList,
                selectedInstrument = target,
                searchQuery = "",
                aiState = AiUiState.Idle,
                aiFollowUpState = AiFollowUpState.Idle
            )
        }
        loadSelectedChart()
        refreshWatchlist()
    }

    fun selectInstrument(instrument: MarketInstrument) {
        if (_uiState.value.selectedInstrument.symbol == instrument.symbol &&
            _uiState.value.chartState is ChartLoadState.Ready
        ) return
        _uiState.update {
            it.copy(
                selectedInstrument = instrument,
                chartState = ChartLoadState.Loading,
                aiState = AiUiState.Idle,
                aiFollowUpState = AiFollowUpState.Idle
            )
        }
        loadSelectedChart()
    }

    fun selectTimeframe(timeframe: Timeframe) {
        if (_uiState.value.selectedTimeframe == timeframe) return
        _uiState.update {
            it.copy(
                selectedTimeframe = timeframe,
                chartState = ChartLoadState.Loading,
                aiState = AiUiState.Idle,
                aiFollowUpState = AiFollowUpState.Idle
            )
        }
        loadSelectedChart()
    }

    fun toggleAutoRefresh() {
        val next = !_uiState.value.autoRefreshEnabled
        _uiState.update { it.copy(autoRefreshEnabled = next) }
        if (next) startLivePolling() else pollingJob?.cancel()
    }

    fun updateOverlays(transform: (OverlayPreferences) -> OverlayPreferences) {
        _uiState.update { it.copy(overlays = transform(it.overlays)) }
    }

    fun refreshAllNow() {
        refreshWatchlist()
        loadSelectedChart(preserveReadyDuringRefresh = true)
    }

    fun refreshWatchlist() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingQuotes = true, quotesError = null) }
            val result = marketRepository.fetchWatchlistQuotes(_uiState.value.instruments)
            _uiState.update { state ->
                val merged = state.quotes.toMutableMap().apply { putAll(result.quotes) }
                state.copy(
                    quotes = merged,
                    isRefreshingQuotes = false,
                    quotesError = result.errorMessage,
                    lastSyncedAtMs = if (result.quotes.isNotEmpty()) System.currentTimeMillis() else state.lastSyncedAtMs
                )
            }
        }
    }

    fun loadSelectedChart(preserveReadyDuringRefresh: Boolean = false) {
        chartFetchJob?.cancel()
        chartFetchJob = viewModelScope.launch {
            val instrument = _uiState.value.selectedInstrument
            val timeframe = _uiState.value.selectedTimeframe
            if (!preserveReadyDuringRefresh || _uiState.value.chartState !is ChartLoadState.Ready) {
                _uiState.update { it.copy(chartState = ChartLoadState.Loading) }
            }
            val result = marketRepository.fetchChart(instrument, timeframe)
            result.onSuccess { chartData ->
                val analysis = TechnicalAnalysisEngine.analyze(chartData.candles)
                _uiState.update { state ->
                    val updatedQuotes = state.quotes.toMutableMap().apply {
                        put(instrument.symbol, chartData.quote)
                    }
                    state.copy(
                        quotes = updatedQuotes,
                        chartState = ChartLoadState.Ready(chartData, analysis),
                        lastSyncedAtMs = System.currentTimeMillis()
                    )
                }
            }.onFailure { err ->
                if (!preserveReadyDuringRefresh || _uiState.value.chartState !is ChartLoadState.Ready) {
                    _uiState.update {
                        it.copy(
                            chartState = ChartLoadState.Error(
                                err.localizedMessage ?: "Failed to load live market candles."
                            )
                        )
                    }
                }
            }
        }
    }

    fun runAiAnalysis() {
        val ready = _uiState.value.chartState as? ChartLoadState.Ready ?: return
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _uiState.update { it.copy(aiState = AiUiState.Loading) }
            val result = aiRepository.analyzeMarketSetup(
                instrument = ready.chartData.instrument,
                timeframe = ready.chartData.timeframe,
                candles = ready.chartData.candles,
                bundle = ready.analysis
            )
            result.onSuccess { aiResult ->
                _uiState.update { it.copy(aiState = AiUiState.Ready(aiResult)) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        aiState = AiUiState.Error(
                            err.localizedMessage ?: "Gemini AI analysis failed."
                        )
                    )
                }
            }
        }
    }

    fun askAiFollowUp(question: String) {
        val clean = question.trim()
        if (clean.isBlank()) return
        val ready = _uiState.value.chartState as? ChartLoadState.Ready ?: return

        followUpJob?.cancel()
        followUpJob = viewModelScope.launch {
            _uiState.update { it.copy(aiFollowUpState = AiFollowUpState.Loading) }
            var lastText = ""
            try {
                aiRepository.askFollowUpStream(
                    instrument = ready.chartData.instrument,
                    timeframe = ready.chartData.timeframe,
                    bundle = ready.analysis,
                    question = clean
                ).collect { chunk ->
                    lastText = chunk
                    _uiState.update { it.copy(aiFollowUpState = AiFollowUpState.Streaming(chunk)) }
                }
                _uiState.update { it.copy(aiFollowUpState = AiFollowUpState.Complete(lastText)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        aiFollowUpState = AiFollowUpState.Error(
                            e.localizedMessage ?: "Failed to answer question."
                        )
                    )
                }
            }
        }
    }

    private fun startLivePolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive && _uiState.value.autoRefreshEnabled) {
                delay(15_000L)
                if (_uiState.value.autoRefreshEnabled) {
                    loadSelectedChart(preserveReadyDuringRefresh = true)
                    refreshWatchlist()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        chartFetchJob?.cancel()
        aiJob?.cancel()
        followUpJob?.cancel()
    }
}
