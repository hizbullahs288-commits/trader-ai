package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.TradingRepository
import com.example.domain.engine.AnalyticsEngine
import com.example.domain.model.AssetClass
import com.example.domain.model.JournalEntry
import com.example.domain.model.PaperPosition
import com.example.domain.model.PortfolioAnalytics
import com.example.domain.model.PositionStatus
import com.example.domain.model.TradeSide
import com.example.domain.model.UserAccount
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DataStreamState<out T> {
    data object Loading : DataStreamState<Nothing>
    data class Ready<T>(val data: T) : DataStreamState<T>
    data class Error(val message: String) : DataStreamState<Nothing>
}

data class OrderDraft(
    val symbol: String = "BTC-USD",
    val displayName: String = "Bitcoin / USD",
    val assetClass: AssetClass = AssetClass.CRYPTO,
    val side: TradeSide = TradeSide.LONG,
    val quantityText: String = "",
    val entryPriceText: String = "",
    val stopLossText: String = "",
    val takeProfitText: String = "",
    val notes: String = ""
)

data class ActionBanner(
    val isError: Boolean,
    val message: String
)

class PortfolioJournalViewModel(
    private val repository: TradingRepository,
    private val currentUserId: String
) : ViewModel() {

    init {
        viewModelScope.launch {
            repository.ensureUserAccount(currentUserId)
        }
    }

    val accountState: StateFlow<DataStreamState<UserAccount>> = repository.observeUserAccount(currentUserId)
        .map<UserAccount?, DataStreamState<UserAccount>> { account ->
            DataStreamState.Ready(account ?: UserAccount(userId = currentUserId))
        }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            val root = fsErr ?: error
            Log.w(TAG, "Account stream error", root)
            emit(DataStreamState.Error(root.message ?: "Failed to load virtual account."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataStreamState.Loading
        )

    val positionsState: StateFlow<DataStreamState<List<PaperPosition>>> = repository.observePositions(currentUserId)
        .map<List<PaperPosition>, DataStreamState<List<PaperPosition>>> { DataStreamState.Ready(it) }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            val root = fsErr ?: error
            Log.w(TAG, "Positions stream error", root)
            emit(DataStreamState.Error(root.message ?: "Failed to load paper positions."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataStreamState.Loading
        )

    val journalState: StateFlow<DataStreamState<List<JournalEntry>>> = repository.observeJournal(currentUserId)
        .map<List<JournalEntry>, DataStreamState<List<JournalEntry>>> { DataStreamState.Ready(it) }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            val root = fsErr ?: error
            Log.w(TAG, "Journal stream error", root)
            emit(DataStreamState.Error(root.message ?: "Failed to load trading journal."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataStreamState.Loading
        )

    val analyticsState: StateFlow<PortfolioAnalytics> = journalState
        .map { state ->
            when (state) {
                is DataStreamState.Ready -> AnalyticsEngine.computeJournalAnalytics(state.data)
                else -> PortfolioAnalytics()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = PortfolioAnalytics()
        )

    private val _orderDraft = MutableStateFlow(OrderDraft())
    val orderDraft: StateFlow<OrderDraft> = _orderDraft.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _banner = MutableStateFlow<ActionBanner?>(null)
    val banner: StateFlow<ActionBanner?> = _banner.asStateFlow()

    fun updateOrderDraft(transform: (OrderDraft) -> OrderDraft) {
        _orderDraft.value = transform(_orderDraft.value)
    }

    fun prefillOrderDraft(
        symbol: String,
        displayName: String,
        assetClass: AssetClass,
        side: TradeSide,
        entryPrice: Double,
        quantity: Double? = null,
        stopLoss: Double? = null,
        takeProfit: Double? = null,
        notes: String = ""
    ) {
        _orderDraft.value = OrderDraft(
            symbol = symbol,
            displayName = displayName,
            assetClass = assetClass,
            side = side,
            quantityText = quantity?.let { formatQty(it) } ?: _orderDraft.value.quantityText,
            entryPriceText = formatPriceInput(entryPrice, assetClass),
            stopLossText = stopLoss?.let { formatPriceInput(it, assetClass) } ?: "",
            takeProfitText = takeProfit?.let { formatPriceInput(it, assetClass) } ?: "",
            notes = notes
        )
    }

    fun submitPaperOrder(livePriceOverride: Double? = null) {
        val account = (accountState.value as? DataStreamState.Ready)?.data ?: UserAccount(userId = currentUserId)
        val draft = _orderDraft.value
        val qty = draft.quantityText.toDoubleOrNull()
        val entry = livePriceOverride ?: draft.entryPriceText.toDoubleOrNull()
        val sl = draft.stopLossText.takeIf { it.isNotBlank() }?.toDoubleOrNull()
        val tp = draft.takeProfitText.takeIf { it.isNotBlank() }?.toDoubleOrNull()

        if (qty == null || qty <= 0.0) {
            _banner.value = ActionBanner(true, "Enter a valid positive quantity.")
            return
        }
        if (entry == null || entry <= 0.0) {
            _banner.value = ActionBanner(true, "Wait for live quote or enter a valid entry price.")
            return
        }
        if (draft.stopLossText.isNotBlank() && sl == null) {
            _banner.value = ActionBanner(true, "Stop-loss must be a valid number.")
            return
        }
        if (draft.takeProfitText.isNotBlank() && tp == null) {
            _banner.value = ActionBanner(true, "Take-profit must be a valid number.")
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.openPaperPosition(
                userId = currentUserId,
                symbol = draft.symbol,
                displayName = draft.displayName,
                assetClass = draft.assetClass,
                side = draft.side,
                quantity = qty,
                entryPrice = entry,
                stopLoss = sl,
                takeProfit = tp,
                notes = draft.notes,
                currentBalance = account.virtualBalance
            )
            _isSubmitting.value = false
            res.onSuccess {
                _banner.value = ActionBanner(
                    false,
                    "Virtual ${draft.side.name} order opened for ${draft.symbol} @ ${String.format("%.4f", entry)}."
                )
            }.onFailure { err ->
                _banner.value = ActionBanner(true, err.localizedMessage ?: "Failed to open paper position.")
            }
        }
    }

    fun closePaperPosition(
        position: PaperPosition,
        exitPrice: Double,
        autoLogToJournal: Boolean,
        strategyTag: String = "Paper Trade",
        emotionTag: String = "Disciplined"
    ) {
        val account = (accountState.value as? DataStreamState.Ready)?.data ?: UserAccount(userId = currentUserId)
        if (position.parsedStatus != PositionStatus.OPEN) return

        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.closePaperPosition(
                userId = currentUserId,
                position = position,
                exitPrice = exitPrice,
                account = account,
                autoLogToJournal = autoLogToJournal,
                strategyTag = strategyTag,
                emotionTag = emotionTag
            )
            _isSubmitting.value = false
            res.onSuccess {
                val pnl = position.computeUnrealizedPnl(exitPrice)
                val sign = if (pnl >= 0) "+" else ""
                _banner.value = ActionBanner(
                    false,
                    "Closed ${position.symbol} ($sign$${String.format("%.2f", pnl)})${if (autoLogToJournal) " & logged to journal." else "."}"
                )
            }.onFailure { err ->
                _banner.value = ActionBanner(true, err.localizedMessage ?: "Failed to close position.")
            }
        }
    }

    fun updateSlTp(positionId: String, stopLoss: Double?, takeProfit: Double?, notes: String) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.updatePositionSlTp(currentUserId, positionId, stopLoss, takeProfit, notes)
            _isSubmitting.value = false
            res.onSuccess {
                _banner.value = ActionBanner(false, "Position SL/TP updated.")
            }.onFailure { err ->
                _banner.value = ActionBanner(true, err.localizedMessage ?: "Failed to update position.")
            }
        }
    }

    fun resetVirtualAccount() {
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.resetPaperAccount(currentUserId)
            _isSubmitting.value = false
            res.onSuccess {
                _banner.value = ActionBanner(false, "Virtual account reset to $100,000.00.")
            }.onFailure { err ->
                _banner.value = ActionBanner(true, err.localizedMessage ?: "Reset failed.")
            }
        }
    }

    fun addJournalEntry(
        symbol: String,
        assetClass: AssetClass,
        side: TradeSide,
        entryPrice: Double,
        exitPrice: Double,
        quantity: Double,
        strategyTag: String,
        emotionTag: String,
        notes: String
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.addJournalEntry(
                userId = currentUserId,
                symbol = symbol,
                assetClass = assetClass,
                side = side,
                entryPrice = entryPrice,
                exitPrice = exitPrice,
                quantity = quantity,
                strategyTag = strategyTag,
                emotionTag = emotionTag,
                notes = notes
            )
            _isSubmitting.value = false
            res.onSuccess {
                _banner.value = ActionBanner(false, "Trade logged to journal.")
            }.onFailure { err ->
                _banner.value = ActionBanner(true, err.localizedMessage ?: "Failed to save journal entry.")
            }
        }
    }

    fun updateJournalEntry(entryId: String, strategyTag: String, emotionTag: String, notes: String) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.updateJournalEntry(currentUserId, entryId, strategyTag, emotionTag, notes)
            _isSubmitting.value = false
            res.onSuccess {
                _banner.value = ActionBanner(false, "Journal entry updated.")
            }.onFailure { err ->
                _banner.value = ActionBanner(true, err.localizedMessage ?: "Failed to update entry.")
            }
        }
    }

    fun deleteJournalEntry(entryId: String) {
        viewModelScope.launch {
            val res = repository.deleteJournalEntry(currentUserId, entryId)
            res.onSuccess {
                _banner.value = ActionBanner(false, "Journal entry deleted.")
            }.onFailure { err ->
                _banner.value = ActionBanner(true, err.localizedMessage ?: "Failed to delete entry.")
            }
        }
    }

    fun clearBanner() {
        _banner.value = null
    }

    private fun formatQty(q: Double): String =
        if (q >= 1.0) String.format("%.2f", q) else String.format("%.4f", q)

    private fun formatPriceInput(price: Double, assetClass: AssetClass): String =
        when (assetClass) {
            AssetClass.FOREX -> String.format("%.5f", price)
            else -> String.format("%.2f", price)
        }

    private companion object {
        const val TAG = "PortfolioJournalVM"
    }
}
