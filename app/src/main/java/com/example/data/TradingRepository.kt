package com.example.data

import android.content.Context
import com.example.R
import com.example.domain.model.AssetClass
import com.example.domain.model.JournalEntry
import com.example.domain.model.PaperPosition
import com.example.domain.model.PositionStatus
import com.example.domain.model.TradeSide
import com.example.domain.model.UserAccount
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class TradingRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun ensureUserAccount(userId: String): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            val userRef = db.collection("users").document(uid)
            val existing = userRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "virtualBalance" to 100_000.0,
                    "initialCapital" to 100_000.0,
                    "realizedPnl" to 0.0,
                    "totalTrades" to 0L,
                    "winningTrades" to 0L,
                    "defaultRiskPercent" to 1.0,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    fun observeUserAccount(userId: String): Flow<UserAccount?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserAccount::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observePositions(userId: String): Flow<List<PaperPosition>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "users/$uid/positions"
        emitAll(
            db.collection("users").document(uid).collection("positions")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PaperPosition::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeJournal(userId: String): Flow<List<JournalEntry>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "users/$uid/journal"
        emitAll(
            db.collection("users").document(uid).collection("journal")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        JournalEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun openPaperPosition(
        userId: String,
        symbol: String,
        displayName: String,
        assetClass: AssetClass,
        side: TradeSide,
        quantity: Double,
        entryPrice: Double,
        stopLoss: Double?,
        takeProfit: Double?,
        notes: String,
        currentBalance: Double
    ): Result<String> {
        var docPath = "users/$userId/positions"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed." }
            val cleanSymbol = symbol.trim().uppercase()
            require(cleanSymbol.length in 1..24) { "Invalid ticker symbol." }
            require(quantity > 0.0 && quantity <= 100_000_000.0) { "Quantity must be positive." }
            require(entryPrice > 0.0 && entryPrice <= 100_000_000.0) { "Entry price must be positive." }
            if (stopLoss != null) require(stopLoss > 0.0) { "Stop-loss must be positive." }
            if (takeProfit != null) require(takeProfit > 0.0) { "Take-profit must be positive." }

            val requiredMargin = quantity * entryPrice
            require(requiredMargin <= currentBalance) {
                "Insufficient virtual cash ($${String.format("%.2f", currentBalance)}) for $${String.format("%.2f", requiredMargin)} order."
            }

            val posRef = db.collection("users").document(uid).collection("positions").document()
            docPath = posRef.path
            val userRef = db.collection("users").document(uid)

            val payload = mapOf(
                "userId" to uid,
                "symbol" to cleanSymbol,
                "displayName" to displayName.trim().take(60).ifBlank { cleanSymbol },
                "assetClass" to assetClass.name,
                "side" to side.name,
                "status" to PositionStatus.OPEN.name,
                "quantity" to quantity,
                "entryPrice" to entryPrice,
                "stopLoss" to stopLoss,
                "takeProfit" to takeProfit,
                "notes" to notes.trim().take(500),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            val newBalance = (currentBalance - requiredMargin).coerceAtLeast(0.0)
            val batch = db.batch()
            batch.set(posRef, payload)
            batch.update(
                userRef,
                mapOf(
                    "virtualBalance" to newBalance,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(posRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun closePaperPosition(
        userId: String,
        position: PaperPosition,
        exitPrice: Double,
        account: UserAccount,
        autoLogToJournal: Boolean,
        strategyTag: String = "Paper Trade",
        emotionTag: String = "Disciplined"
    ): Result<Unit> {
        val docPath = "users/$userId/positions/${position.id}"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed." }
            require(position.id.isNotBlank()) { "Position ID missing." }
            require(position.parsedStatus == PositionStatus.OPEN) { "Position is already closed." }
            require(exitPrice > 0.0) { "Exit price must be positive." }

            val pnlAmount = position.computeUnrealizedPnl(exitPrice)
            val pnlPercent = position.computeUnrealizedPercent(exitPrice)
            val originalNotional = position.quantity * position.entryPrice
            val returnedCash = (originalNotional + pnlAmount).coerceAtLeast(0.0)
            val newBalance = account.virtualBalance + returnedCash
            val newRealized = account.realizedPnl + pnlAmount
            val newTotalTrades = account.totalTrades + 1L
            val newWinningTrades = account.winningTrades + if (pnlAmount > 0.0) 1L else 0L

            val posRef = db.collection("users").document(uid)
                .collection("positions").document(position.id)
            val userRef = db.collection("users").document(uid)

            val batch = db.batch()
            batch.update(
                posRef,
                mapOf(
                    "status" to PositionStatus.CLOSED.name,
                    "exitPrice" to exitPrice,
                    "realizedPnl" to pnlAmount,
                    "closedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                userRef,
                mapOf(
                    "virtualBalance" to newBalance,
                    "realizedPnl" to newRealized,
                    "totalTrades" to newTotalTrades,
                    "winningTrades" to newWinningTrades,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            if (autoLogToJournal) {
                val journalRef = db.collection("users").document(uid).collection("journal").document()
                val journalPayload = mapOf(
                    "userId" to uid,
                    "symbol" to position.symbol,
                    "assetClass" to position.assetClass,
                    "side" to position.side,
                    "entryPrice" to position.entryPrice,
                    "exitPrice" to exitPrice,
                    "quantity" to position.quantity,
                    "pnlAmount" to pnlAmount,
                    "pnlPercent" to pnlPercent,
                    "strategyTag" to strategyTag.trim().take(40).ifBlank { "Paper Trade" },
                    "emotionTag" to emotionTag.trim().take(40).ifBlank { "Disciplined" },
                    "notes" to position.notes.ifBlank { "Closed paper position at ${String.format("%.4f", exitPrice)}." }.take(1000),
                    "linkedPositionId" to position.id,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(journalRef, journalPayload)
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updatePositionSlTp(
        userId: String,
        positionId: String,
        stopLoss: Double?,
        takeProfit: Double?,
        notes: String
    ): Result<Unit> {
        val docPath = "users/$userId/positions/$positionId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed." }
            require(positionId.isNotBlank()) { "Position ID missing." }
            if (stopLoss != null) require(stopLoss > 0.0) { "Stop-loss must be > 0." }
            if (takeProfit != null) require(takeProfit > 0.0) { "Take-profit must be > 0." }

            val updates = mutableMapOf<String, Any>(
                "notes" to notes.trim().take(500),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (stopLoss != null) updates["stopLoss"] = stopLoss
            if (takeProfit != null) updates["takeProfit"] = takeProfit

            db.collection("users").document(uid)
                .collection("positions").document(positionId)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun resetPaperAccount(userId: String): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed." }
            db.collection("users").document(uid).update(
                mapOf(
                    "virtualBalance" to 100_000.0,
                    "initialCapital" to 100_000.0,
                    "realizedPnl" to 0.0,
                    "totalTrades" to 0L,
                    "winningTrades" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addJournalEntry(
        userId: String,
        symbol: String,
        assetClass: AssetClass,
        side: TradeSide,
        entryPrice: Double,
        exitPrice: Double,
        quantity: Double,
        strategyTag: String,
        emotionTag: String,
        notes: String
    ): Result<Unit> {
        var docPath = "users/$userId/journal"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed." }
            val cleanSymbol = symbol.trim().uppercase()
            require(cleanSymbol.length in 1..24) { "Ticker symbol is required." }
            require(entryPrice > 0.0 && exitPrice > 0.0 && quantity > 0.0) {
                "Entry, exit, and quantity must be positive."
            }
            val pnlAmount = when (side) {
                TradeSide.LONG -> (exitPrice - entryPrice) * quantity
                TradeSide.SHORT -> (entryPrice - exitPrice) * quantity
            }
            val pnlPercent = when (side) {
                TradeSide.LONG -> ((exitPrice - entryPrice) / entryPrice) * 100.0
                TradeSide.SHORT -> ((entryPrice - exitPrice) / entryPrice) * 100.0
            }

            val ref = db.collection("users").document(uid).collection("journal").document()
            docPath = ref.path
            val payload = mapOf(
                "userId" to uid,
                "symbol" to cleanSymbol,
                "assetClass" to assetClass.name,
                "side" to side.name,
                "entryPrice" to entryPrice,
                "exitPrice" to exitPrice,
                "quantity" to quantity,
                "pnlAmount" to pnlAmount,
                "pnlPercent" to pnlPercent,
                "strategyTag" to strategyTag.trim().take(40).ifBlank { "Discretionary" },
                "emotionTag" to emotionTag.trim().take(40).ifBlank { "Disciplined" },
                "notes" to notes.trim().take(1000),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            ref.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateJournalEntry(
        userId: String,
        entryId: String,
        strategyTag: String,
        emotionTag: String,
        notes: String
    ): Result<Unit> {
        val docPath = "users/$userId/journal/$entryId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed." }
            require(entryId.isNotBlank()) { "Entry ID required." }
            db.collection("users").document(uid)
                .collection("journal").document(entryId)
                .update(
                    mapOf(
                        "strategyTag" to strategyTag.trim().take(40).ifBlank { "Discretionary" },
                        "emotionTag" to emotionTag.trim().take(40).ifBlank { "Disciplined" },
                        "notes" to notes.trim().take(1000),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteJournalEntry(userId: String, entryId: String): Result<Unit> {
        val docPath = "users/$userId/journal/$entryId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed." }
            require(entryId.isNotBlank()) { "Entry ID required." }
            db.collection("users").document(uid)
                .collection("journal").document(entryId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
