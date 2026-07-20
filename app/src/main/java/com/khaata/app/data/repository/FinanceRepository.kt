package com.khaata.app.data.repository

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.khaata.app.data.model.AnalyticsSnapshot
import com.khaata.app.data.model.buildBudgetProgress
import com.khaata.app.data.model.CategoryTrendItem
import com.khaata.app.data.model.Budget
import com.khaata.app.data.model.BudgetProgress
import com.khaata.app.data.model.Contribution
import com.khaata.app.data.model.Expense
import com.khaata.app.data.model.Goal
import com.khaata.app.data.model.GoalStats
import com.khaata.app.data.model.LedgerEntry
import com.khaata.app.data.model.MonthSummary
import com.khaata.app.data.model.Person
import com.khaata.app.data.model.MonthlyAnalyticsPoint
import com.khaata.app.data.model.RecurringExpense
import com.khaata.app.data.model.Template
import com.khaata.app.data.model.computeStats
import com.khaata.app.data.model.currentMonthKey
import com.khaata.app.data.model.roundMoney
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.khaata.app.util.CategoryMeta
import com.khaata.app.util.DEFAULT_CATEGORIES
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.tasks.await

/**
 * Everything Khaata stores lives under users/{uid}/... so each signed-in
 * (anonymous) user only ever sees their own ledger.
 *
 * Firestore layout:
 *   users/{uid}/months/{yyyy-MM}                -> { income, totalExpenses }
 *   users/{uid}/months/{yyyy-MM}/expenses/{id}   -> { category, amount, note, date }
 *   users/{uid}/budgets/{yyyy-MM_category}       -> { category, limitAmount, monthKey }
 *   users/{uid}/goals/{id}                       -> { name, targetAmount, targetDate,
 *                                                      createdAt, savedAmount, monthlyContributions }
 *   users/{uid}/goals/{id}/contributions/{id}    -> { amount, date }
 *   users/{uid}/contacts/{id}                    -> { name, note, balance, createdAt }
 *   users/{uid}/contacts/{id}/ledger/{id}        -> { amount, note, date }  (amount signed)
 */
class FinanceRepository(private val uid: String) {

    /**
     * Fails the flow on a listener error, then resubscribes with linear backoff
     * (capped at 30s). Firestore stops delivering snapshots once a listener errors
     * (e.g. a permission blip during token refresh) — swallowing the error would
     * leave the UI rendering the last value forever with no signal and no recovery.
     */
    private fun <T> Flow<T>.retryOnSnapshotError(): Flow<T> = retryWhen { _, attempt ->
        delay(((attempt + 1) * 1000L).coerceAtMost(30_000L))
        true
    }

    private val db = FirebaseFirestore.getInstance()
    private val userRoot get() = db.collection("users").document(uid)
    private fun monthsRef() = userRoot.collection("months")
    private fun budgetsRef() = userRoot.collection("budgets")
    private fun goalsRef() = userRoot.collection("goals")
    private fun categoriesRef() = userRoot.collection("categories")
    private fun recurringRef() = userRoot.collection("recurring")
    private fun templatesRef() = userRoot.collection("templates")
    private fun contactsRef() = userRoot.collection("contacts")

    fun observeMonthSummary(monthKey: String): Flow<MonthSummary> = callbackFlow {
        val reg = monthsRef().document(monthKey).addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            if (snap != null && snap.exists()) {
                trySend(
                    MonthSummary(
                        monthKey = monthKey,
                        income = snap.getDouble("income") ?: 0.0,
                        totalExpenses = snap.getDouble("totalExpenses") ?: 0.0
                    )
                )
            } else {
                trySend(MonthSummary(monthKey = monthKey))
            }
        }
        awaitClose { reg.remove() }
    }.retryOnSnapshotError()

    fun observeExpenses(monthKey: String): Flow<List<Expense>> = callbackFlow {
        val reg = monthsRef().document(monthKey).collection("expenses")
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                val list = snap?.documents?.map { d ->
                    Expense(
                        id = d.id,
                        category = d.getString("category") ?: "other",
                        amount = d.getDouble("amount") ?: 0.0,
                        note = d.getString("note") ?: "",
                        date = d.getString("date") ?: ""
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }.retryOnSnapshotError()

    fun observeAllMonths(): Flow<List<MonthSummary>> = callbackFlow {
        val reg = monthsRef().addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            val list = snap?.documents?.map { d ->
                MonthSummary(
                    monthKey = d.id,
                    income = d.getDouble("income") ?: 0.0,
                    totalExpenses = d.getDouble("totalExpenses") ?: 0.0
                )
            }?.sortedByDescending { it.monthKey } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }.retryOnSnapshotError()

    fun observeBudgets(monthKey: String): Flow<List<Budget>> = callbackFlow {
        val reg = budgetsRef().whereEqualTo("monthKey", monthKey).addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            val list = snap?.documents?.map { d ->
                Budget(
                    id = d.id,
                    category = d.getString("category") ?: "other",
                    limitAmount = d.getDouble("limitAmount") ?: 0.0,
                    monthKey = d.getString("monthKey") ?: monthKey
                )
            } ?: emptyList()
            trySend(list.sortedBy { it.category })
        }
        awaitClose { reg.remove() }
    }.retryOnSnapshotError()

    suspend fun loadBudgetProgress(monthKey: String): List<BudgetProgress> {
        val budgets = budgetsRef().whereEqualTo("monthKey", monthKey).get().await().documents.map { d ->
            Budget(
                id = d.id,
                category = d.getString("category") ?: "other",
                limitAmount = d.getDouble("limitAmount") ?: 0.0,
                monthKey = d.getString("monthKey") ?: monthKey
            )
        }
        if (budgets.isEmpty()) return emptyList()

        val expenses = monthsRef().document(monthKey).collection("expenses").get().await().documents.map { d ->
            Expense(
                id = d.id,
                category = d.getString("category") ?: "other",
                amount = d.getDouble("amount") ?: 0.0,
                note = d.getString("note") ?: "",
                date = d.getString("date") ?: ""
            )
        }
        val spentByCategory = expenses.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }
        return budgets.map { budget -> buildBudgetProgress(budget, spentByCategory[budget.category] ?: 0.0) }
            .sortedByDescending { it.pct }
    }

    suspend fun setBudget(monthKey: String, category: String, limitAmount: Double) {
        budgetsRef().document("${monthKey}_$category").set(
            mapOf(
                "category" to category,
                "limitAmount" to roundMoney(limitAmount),
                "monthKey" to monthKey
            )
        ).await()
    }

    suspend fun deleteBudget(monthKey: String, category: String) {
        budgetsRef().document("${monthKey}_$category").delete().await()
    }

    /**
     * Copies every budget cap from [sourceMonthKey] into [targetMonthKey] (one flat doc
     * per category, keyed `{month}_{category}`), overwriting any existing cap for the
     * same category in the target month. Returns how many were copied (0 if the source
     * month had none). One batch — all caps land together or none do.
     */
    suspend fun copyBudgets(sourceMonthKey: String, targetMonthKey: String): Int {
        val source = budgetsRef().whereEqualTo("monthKey", sourceMonthKey).get().await().documents
        if (source.isEmpty()) return 0
        val batch = db.batch()
        var copied = 0
        source.forEach { d ->
            val category = d.getString("category") ?: return@forEach
            val limit = d.getDouble("limitAmount") ?: 0.0
            batch.set(
                budgetsRef().document("${targetMonthKey}_$category"),
                mapOf("category" to category, "limitAmount" to limit, "monthKey" to targetMonthKey)
            )
            copied++
        }
        batch.commit().await()
        return copied
    }

    suspend fun latestBudgetProgress(monthKey: String): List<BudgetProgress> = loadBudgetProgress(monthKey)

    // ── New-month rollover ──────────────────────────────────────────────────
    // Backs the "new month — copy last month's setup?" offer on the Dashboard.
    // `rolloverHandled` lives on the month doc (not device-local prefs) so a
    // dismissal sticks across devices and reinstalls.

    /** One-shot read of a month's income and whether its rollover offer was already handled. */
    suspend fun loadMonthMeta(monthKey: String): Pair<Double, Boolean> {
        val snap = monthsRef().document(monthKey).get().await()
        return (snap.getDouble("income") ?: 0.0) to (snap.getBoolean("rolloverHandled") ?: false)
    }

    suspend fun countBudgets(monthKey: String): Int =
        budgetsRef().whereEqualTo("monthKey", monthKey).get().await().size()

    /** Marks the month's rollover offer as consumed (applied or dismissed). */
    suspend fun markRolloverHandled(monthKey: String) {
        monthsRef().document(monthKey).set(mapOf("rolloverHandled" to true), SetOptions.merge()).await()
    }

    suspend fun loadLatestExpenseDate(monthKey: String): String? {
        val snapshot = monthsRef().document(monthKey).collection("expenses")
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()
        return snapshot.documents.firstOrNull()?.getString("date")
    }

    suspend fun loadLatestMonthKey(): String? {
        val snapshot = monthsRef().get().await()
        return snapshot.documents.map { it.id }.sorted().lastOrNull()
    }

    suspend fun loadGoalsOnce(): List<Goal> {
        val snapshot = goalsRef().orderBy("createdAt").get().await()
        return snapshot.documents.map { d ->
            @Suppress("UNCHECKED_CAST")
            val rawContrib = d.get("monthlyContributions") as? Map<String, Any?> ?: emptyMap()
            Goal(
                id = d.id,
                name = d.getString("name") ?: "",
                targetAmount = d.getDouble("targetAmount") ?: 0.0,
                targetDate = d.getString("targetDate") ?: "",
                createdAt = d.getString("createdAt") ?: "",
                savedAmount = d.getDouble("savedAmount") ?: 0.0,
                monthlyContributions = rawContrib.mapValues { (_, v) -> (v as? Number)?.toDouble() ?: 0.0 }
            )
        }
    }

    suspend fun currentGoalStats(): List<Pair<Goal, GoalStats>> {
        val monthKey = currentMonthKey()
        return loadGoalsOnce().map { goal -> goal to goal.computeStats(monthKey) }
    }

    private suspend fun loadExpensesForMonth(monthKey: String): List<Expense> {
        return monthsRef().document(monthKey).collection("expenses").get().await().documents.map { d ->
            Expense(
                id = d.id,
                category = d.getString("category") ?: "other",
                amount = d.getDouble("amount") ?: 0.0,
                note = d.getString("note") ?: "",
                date = d.getString("date") ?: ""
            )
        }
    }

    private fun categoryTotals(expenses: List<Expense>): Map<String, Double> {
        return expenses.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }
    }

    suspend fun buildAnalyticsSnapshot(months: List<MonthSummary>, recentMonthCount: Int = 12): AnalyticsSnapshot {
        val recentMonths = months.sortedBy { it.monthKey }.takeLast(recentMonthCount)
        if (recentMonths.isEmpty()) return AnalyticsSnapshot()

        val monthlyPoints = recentMonths.map { month ->
            MonthlyAnalyticsPoint(
                monthKey = month.monthKey,
                income = month.income,
                expenses = month.totalExpenses
            )
        }

        val latestMonthKey = recentMonths.last().monthKey
        val previousMonthKey = recentMonths.getOrNull(recentMonths.lastIndex - 1)?.monthKey

        val latestExpenses = loadExpensesForMonth(latestMonthKey)
        val previousExpenses = if (previousMonthKey != null) loadExpensesForMonth(previousMonthKey) else emptyList()

        val latestTotals = categoryTotals(latestExpenses)
        val previousTotals = categoryTotals(previousExpenses)

        val categoryTrends = (latestTotals.keys + previousTotals.keys)
            .distinct()
            .map { key ->
                CategoryTrendItem(
                    categoryKey = key,
                    currentAmount = latestTotals[key] ?: 0.0,
                    previousAmount = previousTotals[key] ?: 0.0
                )
            }
            .sortedByDescending { kotlin.math.abs(it.delta) }
            .take(6)

        val savingsRates = monthlyPoints.mapNotNull { if (it.income > 0.0) it.savingsRate else null }

        return AnalyticsSnapshot(
            months = monthlyPoints,
            categoryTrends = categoryTrends,
            biggestExpenses = latestExpenses.sortedByDescending { it.amount }.take(3),
            averageSavingsRate = if (savingsRates.isEmpty()) 0.0 else savingsRates.average(),
            bestMonthKey = monthlyPoints.maxByOrNull { it.netSavings }?.monthKey,
            worstMonthKey = monthlyPoints.minByOrNull { it.netSavings }?.monthKey
        )
    }

    fun observeGoals(): Flow<List<Goal>> = callbackFlow {
        val reg = goalsRef().orderBy("createdAt").addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            val list = snap?.documents?.map { d ->
                @Suppress("UNCHECKED_CAST")
                val rawContrib = d.get("monthlyContributions") as? Map<String, Any?> ?: emptyMap()
                Goal(
                    id = d.id,
                    name = d.getString("name") ?: "",
                    targetAmount = d.getDouble("targetAmount") ?: 0.0,
                    targetDate = d.getString("targetDate") ?: "",
                    createdAt = d.getString("createdAt") ?: "",
                    savedAmount = d.getDouble("savedAmount") ?: 0.0,
                    monthlyContributions = rawContrib.mapValues { (_, v) -> (v as? Number)?.toDouble() ?: 0.0 }
                )
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }.retryOnSnapshotError()

    fun observeContributions(goalId: String): Flow<List<Contribution>> = callbackFlow {
        val reg = goalsRef().document(goalId).collection("contributions")
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(5)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                val list = snap?.documents?.map { d ->
                    Contribution(id = d.id, amount = d.getDouble("amount") ?: 0.0, date = d.getString("date") ?: "")
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }.retryOnSnapshotError()

    suspend fun setIncome(monthKey: String, income: Double) {
        monthsRef().document(monthKey).set(mapOf("income" to roundMoney(income)), SetOptions.merge()).await()
    }

    suspend fun addExpense(monthKey: String, expense: Expense) {
        val monthRef = monthsRef().document(monthKey)
        val expenseRef = monthRef.collection("expenses").document()
        // Amounts are rounded to whole paise at this boundary (and every other
        // write boundary in this class): totals are maintained by repeated
        // FieldValue.increment on Doubles, so an unrounded 10/3 would accumulate
        // float error across increments and drift the total from its rows.
        val amount = roundMoney(expense.amount)
        // One batch so the expense doc and the running `totalExpenses` can never
        // disagree: either both land or neither does. A crash/network drop
        // between two separate writes used to leave the total permanently off.
        db.batch().apply {
            set(
                expenseRef,
                mapOf(
                    "category" to expense.category,
                    "amount" to amount,
                    "note" to expense.note,
                    "date" to expense.date
                )
            )
            // merge creates the month doc if absent, or atomically bumps the total.
            set(monthRef, mapOf("totalExpenses" to FieldValue.increment(amount)), SetOptions.merge())
        }.commit().await()
    }

    /**
     * Deletes an expense and reverses its effect on the month total. Runs as a
     * transaction that re-reads the doc first: a blind batch (delete + unconditional
     * decrement) corrupts `totalExpenses` forever if the doc was already deleted from
     * another device — the delete silently "succeeds" while the total drops twice.
     * If the doc is already gone this is a no-op (whoever deleted it adjusted the
     * total). The decrement uses the *stored* amount, not the caller's possibly-stale
     * snapshot of it, for the same reason.
     */
    suspend fun deleteExpense(monthKey: String, expenseId: String, amount: Double) {
        val monthRef = monthsRef().document(monthKey)
        val expenseRef = monthRef.collection("expenses").document(expenseId)
        db.runTransaction { txn ->
            val snap = txn.get(expenseRef)
            if (snap.exists()) {
                val storedAmount = snap.getDouble("amount") ?: amount
                txn.delete(expenseRef)
                txn.set(monthRef, mapOf("totalExpenses" to FieldValue.increment(-storedAmount)), SetOptions.merge())
            }
            null
        }.await()
    }

    /**
     * Edits an existing expense atomically, adjusting the affected month total(s).
     * If the new date lands in a different month, the entry is moved: removed from
     * the old month (and its total) and re-created in the new month — all in one
     * transaction so nothing is ever double-counted or lost midway.
     *
     * The transaction re-reads the doc and throws if it no longer exists (deleted
     * from another device): a blind `set` would silently resurrect the deleted doc
     * while the month total stays short by the old amount. The failure surfaces as
     * the ViewModel's normal "couldn't save" message.
     */
    suspend fun updateExpense(oldMonthKey: String, expenseId: String, oldAmount: Double, updated: Expense) {
        val newMonthKey = com.khaata.app.data.model.monthKeyFromDate(updated.date)
        val newAmount = roundMoney(updated.amount)
        val fields = mapOf(
            "category" to updated.category,
            "amount" to newAmount,
            "note" to updated.note,
            "date" to updated.date
        )
        val oldMonthRef = monthsRef().document(oldMonthKey)
        val oldExpenseRef = oldMonthRef.collection("expenses").document(expenseId)
        // Generated outside the transaction body so a retry reuses the same id.
        val movedRef = monthsRef().document(newMonthKey).collection("expenses").document()
        db.runTransaction { txn ->
            val snap = txn.get(oldExpenseRef)
            check(snap.exists()) { "Expense no longer exists" }
            val storedOldAmount = snap.getDouble("amount") ?: oldAmount
            if (newMonthKey == oldMonthKey) {
                txn.set(oldExpenseRef, fields)
                txn.set(oldMonthRef, mapOf("totalExpenses" to FieldValue.increment(newAmount - storedOldAmount)), SetOptions.merge())
            } else {
                val newMonthRef = monthsRef().document(newMonthKey)
                txn.delete(oldExpenseRef)
                txn.set(oldMonthRef, mapOf("totalExpenses" to FieldValue.increment(-storedOldAmount)), SetOptions.merge())
                txn.set(movedRef, fields)
                txn.set(newMonthRef, mapOf("totalExpenses" to FieldValue.increment(newAmount)), SetOptions.merge())
            }
            null
        }.await()
    }

    suspend fun addGoal(goal: Goal) {
        goalsRef().document().set(
            mapOf(
                "name" to goal.name,
                "targetAmount" to goal.targetAmount,
                "targetDate" to goal.targetDate,
                "createdAt" to goal.createdAt,
                "savedAmount" to 0.0,
                "monthlyContributions" to emptyMap<String, Double>()
            )
        ).await()
    }

    suspend fun updateGoalTarget(goalId: String, targetAmount: Double, targetDate: String) {
        goalsRef().document(goalId).update(
            mapOf(
                "targetAmount" to targetAmount,
                "targetDate" to targetDate
            )
        ).await()
    }

    /**
     * Firestore doesn't cascade-delete subcollections when a parent document is
     * deleted, and the client SDK has no way to list a document's subcollections
     * (that's only available server-side, e.g. via the Admin SDK in a Cloud
     * Function). So every place that deletes a document with known subcollections
     * must explicitly delete those subcollections' documents itself — which is
     * what this helper centralizes.
     *
     * A single Firestore batch caps out at 500 write operations, so for goals with
     * a lot of logged contributions we split the deletes into chunks of 450 and
     * commit each chunk as its own batch. Each chunk is still atomic; a goal with
     * more than 500 contributions just won't delete as a single all-or-nothing
     * operation across chunks (an acceptable tradeoff — Firestore has no way to
     * make a >500-op delete atomic in one shot).
     */
    private suspend fun deleteInChunks(refs: List<DocumentReference>, chunkSize: Int = 450) {
        refs.chunked(chunkSize).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it) }
            batch.commit().await()
        }
    }

    suspend fun deleteGoal(goalId: String) {
        val goalRef = goalsRef().document(goalId)
        val contribs = goalRef.collection("contributions").get().await()
        val refsToDelete = contribs.documents.map { it.reference } + goalRef
        deleteInChunks(refsToDelete)
    }

    suspend fun logContribution(goalId: String, monthKey: String, amount: Double, date: String) {
        val goalRef = goalsRef().document(goalId)
        val rounded = roundMoney(amount)
        db.batch().apply {
            set(goalRef.collection("contributions").document(), mapOf("amount" to rounded, "date" to date))
            update(
                goalRef,
                mapOf(
                    "savedAmount" to FieldValue.increment(rounded),
                    "monthlyContributions.$monthKey" to FieldValue.increment(rounded)
                )
            )
        }.commit().await()
    }

    /**
     * Edits (or, when [newAmount] is <= 0, removes) the total saved for one month of
     * a goal. GoalsScreen shows contributions rolled up per month, so an edit
     * operates on that month's aggregate: `savedAmount` moves by the delta, the
     * month's map entry is set to the exact new value (or deleted), and the month's
     * raw contribution docs are replaced by a single doc holding the new total — so
     * the subcollection (which feeds the recent-contributions list and delete/undo
     * snapshots) always sums back to the aggregates. All in one batch.
     *
     * A negative [newAmount] is clamped to "remove": the effective value the month
     * ends up with is what drives the `savedAmount` delta, never the raw input —
     * otherwise a stray "-100" would move `savedAmount` by more than the month map,
     * and the two would disagree forever.
     */
    suspend fun editMonthlyContribution(goalId: String, monthKey: String, oldAmount: Double, newAmount: Double) {
        val goalRef = goalsRef().document(goalId)
        val effectiveNew = roundMoney(newAmount.coerceAtLeast(0.0))
        val monthDocs = goalRef.collection("contributions")
            .whereGreaterThanOrEqualTo("date", "$monthKey-01")
            .whereLessThanOrEqualTo("date", "$monthKey-31")
            .get().await().documents
        // Keep the month's latest entry date on the replacement doc so the recent
        // list stays plausibly ordered; fall back to the 1st for an empty month.
        val replacementDate = monthDocs.mapNotNull { it.getString("date") }.maxOrNull() ?: "$monthKey-01"
        db.batch().apply {
            monthDocs.forEach { delete(it.reference) }
            if (effectiveNew > 0.0) {
                set(goalRef.collection("contributions").document(), mapOf("amount" to effectiveNew, "date" to replacementDate))
            }
            update(
                goalRef,
                mapOf(
                    "savedAmount" to FieldValue.increment(effectiveNew - oldAmount),
                    "monthlyContributions.$monthKey" to if (effectiveNew <= 0.0) FieldValue.delete() else effectiveNew
                )
            )
        }.commit().await()
    }

    /** All contribution docs for a goal — captured before a delete so it can be undone. */
    suspend fun getAllContributions(goalId: String): List<Contribution> {
        val snapshot = goalsRef().document(goalId).collection("contributions").get().await()
        return snapshot.documents.map { d ->
            Contribution(id = d.id, amount = d.getDouble("amount") ?: 0.0, date = d.getString("date") ?: "")
        }
    }

    /**
     * Re-creates a previously deleted goal (undo). Restores the goal doc with its
     * saved total and per-month map intact, and re-adds each captured contribution
     * doc under its original id. Chunked into batches to respect the 500-op cap.
     */
    suspend fun restoreGoal(goal: Goal, contributions: List<Contribution>) {
        val goalRef = goalsRef().document(goal.id)
        contributions.chunked(450).forEachIndexed { index, chunk ->
            val batch = db.batch()
            if (index == 0) {
                batch.set(
                    goalRef,
                    mapOf(
                        "name" to goal.name,
                        "targetAmount" to goal.targetAmount,
                        "targetDate" to goal.targetDate,
                        "createdAt" to goal.createdAt,
                        "savedAmount" to goal.savedAmount,
                        "monthlyContributions" to goal.monthlyContributions
                    )
                )
            }
            chunk.forEach { c ->
                batch.set(goalRef.collection("contributions").document(c.id), mapOf("amount" to c.amount, "date" to c.date))
            }
            batch.commit().await()
        }
        // A goal with no contributions still needs its doc written.
        if (contributions.isEmpty()) {
            goalRef.set(
                mapOf(
                    "name" to goal.name,
                    "targetAmount" to goal.targetAmount,
                    "targetDate" to goal.targetDate,
                    "createdAt" to goal.createdAt,
                    "savedAmount" to goal.savedAmount,
                    "monthlyContributions" to goal.monthlyContributions
                )
            ).await()
        }
    }

    // ── Udhaar / people ledger ──────────────────────────────────────────────
    // Stored as users/{uid}/contacts/{id} with a running `balance` kept on the doc
    // (mirrors a goal's savedAmount) and per-transaction rows under
    // users/{uid}/contacts/{id}/ledger/{id}. balance > 0 → they owe you.

    fun observePeople(): Flow<List<Person>> = callbackFlow {
        val reg = contactsRef().orderBy("createdAt").addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            val list = snap?.documents?.map { d ->
                Person(
                    id = d.id,
                    name = d.getString("name") ?: "",
                    note = d.getString("note") ?: "",
                    balance = d.getDouble("balance") ?: 0.0,
                    createdAt = d.getString("createdAt") ?: ""
                )
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }.retryOnSnapshotError()

    fun observeLedger(personId: String): Flow<List<LedgerEntry>> = callbackFlow {
        val reg = contactsRef().document(personId).collection("ledger")
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                val list = snap?.documents?.map { d ->
                    LedgerEntry(
                        id = d.id,
                        amount = d.getDouble("amount") ?: 0.0,
                        note = d.getString("note") ?: "",
                        date = d.getString("date") ?: ""
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }.retryOnSnapshotError()

    suspend fun addPerson(person: Person) {
        contactsRef().document().set(
            mapOf(
                "name" to person.name,
                "note" to person.note,
                "balance" to 0.0,
                "createdAt" to person.createdAt
            )
        ).await()
    }

    /**
     * Records one ledger entry and atomically moves the person's running balance by
     * the same signed [amount], in a single batch so the two can never disagree.
     * A "settle up" is just this with amount = -currentBalance.
     */
    suspend fun recordLedgerEntry(personId: String, amount: Double, note: String, date: String) {
        val personRef = contactsRef().document(personId)
        val rounded = roundMoney(amount)
        db.batch().apply {
            set(personRef.collection("ledger").document(), mapOf("amount" to rounded, "note" to note, "date" to date))
            update(personRef, mapOf("balance" to FieldValue.increment(rounded)))
        }.commit().await()
    }

    /**
     * Deletes one ledger entry and reverses its effect on the balance. A transaction
     * (not a blind batch) so a double-delete from two devices can't decrement the
     * balance twice — the udhaar balance is the answer to "who owes whom", so drift
     * here is worse than anywhere else. Already-gone entry → no-op. The reversal uses
     * the stored signed amount, not the caller's snapshot of it.
     */
    suspend fun deleteLedgerEntry(personId: String, entryId: String, amount: Double) {
        val personRef = contactsRef().document(personId)
        val entryRef = personRef.collection("ledger").document(entryId)
        db.runTransaction { txn ->
            val snap = txn.get(entryRef)
            if (snap.exists()) {
                val storedAmount = snap.getDouble("amount") ?: amount
                txn.delete(entryRef)
                txn.update(personRef, mapOf("balance" to FieldValue.increment(-storedAmount)))
            }
            null
        }.await()
    }

    suspend fun deletePerson(personId: String) {
        val personRef = contactsRef().document(personId)
        val entries = personRef.collection("ledger").get().await()
        val refsToDelete = entries.documents.map { it.reference } + personRef
        deleteInChunks(refsToDelete)
    }

    /** All ledger docs for a person — captured before a delete so it can be undone. */
    suspend fun getPersonLedger(personId: String): List<LedgerEntry> {
        val snapshot = contactsRef().document(personId).collection("ledger").get().await()
        return snapshot.documents.map { d ->
            LedgerEntry(
                id = d.id,
                amount = d.getDouble("amount") ?: 0.0,
                note = d.getString("note") ?: "",
                date = d.getString("date") ?: ""
            )
        }
    }

    /** One-shot read of everyone in the udhaar ledger — used by the reminder worker. */
    suspend fun loadPeopleOnce(): List<Person> {
        return contactsRef().get().await().documents.map { d ->
            Person(
                id = d.id,
                name = d.getString("name") ?: "",
                note = d.getString("note") ?: "",
                balance = d.getDouble("balance") ?: 0.0,
                createdAt = d.getString("createdAt") ?: ""
            )
        }
    }

    /**
     * The date of a person's most recent ledger entry, or null when the ledger is
     * empty. "How long has this balance been sitting" is measured from the last
     * movement, not from the first loan — any partial payment resets the clock,
     * which is the fair reading of an active ledger.
     */
    suspend fun lastLedgerDate(personId: String): String? {
        val snapshot = contactsRef().document(personId).collection("ledger")
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()
        return snapshot.documents.firstOrNull()?.getString("date")
    }

    /**
     * Re-creates a previously deleted person (undo): restores the contact doc with its
     * balance intact and re-adds each captured ledger entry under its original id.
     * Chunked into batches to respect the 500-op cap.
     */
    suspend fun restorePerson(person: Person, entries: List<LedgerEntry>) {
        val personRef = contactsRef().document(person.id)
        val personFields = mapOf(
            "name" to person.name,
            "note" to person.note,
            "balance" to person.balance,
            "createdAt" to person.createdAt
        )
        if (entries.isEmpty()) {
            personRef.set(personFields).await()
            return
        }
        entries.chunked(450).forEachIndexed { index, chunk ->
            val batch = db.batch()
            if (index == 0) batch.set(personRef, personFields)
            chunk.forEach { e ->
                batch.set(
                    personRef.collection("ledger").document(e.id),
                    mapOf("amount" to e.amount, "note" to e.note, "date" to e.date)
                )
            }
            batch.commit().await()
        }
    }

    // ── Categories ──────────────────────────────────────────────────────────
    // Stored as users/{uid}/categories/{key}. Built-ins are seeded once and are
    // fully editable thereafter. A deleted category needs no data migration: an
    // unknown key falls back to "Other" wherever categoryMeta() is used.

    fun observeCategories(): Flow<List<CategoryMeta>> = callbackFlow {
        val reg = categoriesRef().orderBy("order").addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            val list = snap?.documents?.mapNotNull { d ->
                val label = d.getString("label") ?: return@mapNotNull null
                CategoryMeta(
                    key = d.id,
                    label = label,
                    color = Color((d.getLong("colorArgb") ?: 0xFF6B6357L).toInt()),
                    iconKey = d.getString("iconKey") ?: "category"
                )
            }.orEmpty()
            // Until the first seed lands, fall back to the built-in defaults so
            // pickers are never empty.
            trySend(list.ifEmpty { DEFAULT_CATEGORIES })
        }
        awaitClose { reg.remove() }
    }.retryOnSnapshotError()

    /** Writes the built-in defaults the first time, if the collection is empty. */
    suspend fun ensureCategoriesSeeded() {
        if (!categoriesRef().limit(1).get().await().isEmpty) return
        val batch = db.batch()
        DEFAULT_CATEGORIES.forEachIndexed { index, meta ->
            batch.set(
                categoriesRef().document(meta.key),
                mapOf(
                    "label" to meta.label,
                    "colorArgb" to meta.color.toArgb().toLong(),
                    "iconKey" to meta.iconKey,
                    "order" to index
                )
            )
        }
        batch.commit().await()
    }

    suspend fun upsertCategory(key: String, label: String, color: Color, iconKey: String, order: Int) {
        // An edit keeps the doc's stored `order`; [order] only seeds brand-new
        // categories. The caller derives it from the list *index*, which disagrees
        // with stored orders once deletions leave gaps — writing it back on a rename
        // could collide with another category's order and visibly swap the two.
        val existingOrder = categoriesRef().document(key).get().await()
            .takeIf { it.exists() }?.getLong("order")?.toInt()
        categoriesRef().document(key).set(
            mapOf(
                "label" to label,
                "colorArgb" to color.toArgb().toLong(),
                "iconKey" to iconKey,
                "order" to (existingOrder ?: order)
            )
        ).await()
    }

    /**
     * Persists a new manual ordering of categories. Each category's `order` is set to
     * its index in [orderedKeys], in one batch, so `observeCategories` (which sorts by
     * `order`) re-emits in exactly this sequence everywhere the list is shown — Add
     * Entry, budgets, the split dialog, the quick-add popup, all of it.
     */
    suspend fun reorderCategories(orderedKeys: List<String>) {
        val batch = db.batch()
        orderedKeys.forEachIndexed { index, key ->
            batch.update(categoriesRef().document(key), "order", index)
        }
        batch.commit().await()
    }

    /**
     * Deletes a category and cleans up everything keyed to it, so nothing dangles
     * under a category that no longer exists:
     *
     *  - **Budget docs** (`{yyyy-MM}_{key}`) are deleted outright. A budget for a
     *    gone category is pure noise — it would otherwise keep showing in budget
     *    progress mislabelled "Other", and budgets are cheap to re-create.
     *  - **Recurring templates** are *reassigned* to "other" rather than deleted.
     *    A template is a standing instruction (rent, a subscription); silently
     *    dropping it would stop real expenses from posting. Reassigning mirrors how
     *    past expenses fall back to "Other" at display time, and keeps them posting
     *    under a category that actually exists.
     *
     * Past expense docs are intentionally left as-is — they already fall back to
     * "Other" via categoryMeta() and rewriting historical ledger rows would be both
     * unnecessary and unbounded in cost.
     */
    suspend fun deleteCategory(key: String) {
        val orphanBudgets = budgetsRef().whereEqualTo("category", key).get().await().documents
        val staleRecurring = recurringRef().whereEqualTo("category", key).get().await().documents

        val batch = db.batch()
        batch.delete(categoriesRef().document(key))
        orphanBudgets.forEach { batch.delete(it.reference) }
        staleRecurring.forEach { batch.update(it.reference, "category", "other") }
        batch.commit().await()
    }

    // ── Recurring expenses ──────────────────────────────────────────────────

    fun observeRecurring(): Flow<List<RecurringExpense>> = callbackFlow {
        val reg = recurringRef().orderBy("createdAt").addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            val list = snap?.documents?.map { d ->
                RecurringExpense(
                    id = d.id,
                    category = d.getString("category") ?: "other",
                    amount = d.getDouble("amount") ?: 0.0,
                    note = d.getString("note") ?: "",
                    dayOfMonth = (d.getLong("dayOfMonth") ?: 1L).toInt(),
                    active = d.getBoolean("active") ?: true,
                    createdAt = d.getString("createdAt") ?: "",
                    lastPostedMonth = d.getString("lastPostedMonth") ?: ""
                )
            }.orEmpty()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }.retryOnSnapshotError()

    suspend fun addRecurring(recurring: RecurringExpense) {
        recurringRef().document().set(
            mapOf(
                "category" to recurring.category,
                "amount" to roundMoney(recurring.amount),
                "note" to recurring.note,
                "dayOfMonth" to recurring.dayOfMonth,
                "active" to recurring.active,
                "createdAt" to recurring.createdAt,
                "lastPostedMonth" to recurring.lastPostedMonth
            )
        ).await()
    }

    suspend fun updateRecurring(id: String, category: String, amount: Double, note: String, dayOfMonth: Int) {
        recurringRef().document(id).update(
            mapOf(
                "category" to category,
                "amount" to roundMoney(amount),
                "note" to note,
                "dayOfMonth" to dayOfMonth
            )
        ).await()
    }

    suspend fun setRecurringActive(id: String, active: Boolean) {
        recurringRef().document(id).update("active", active).await()
    }

    suspend fun deleteRecurring(id: String) {
        recurringRef().document(id).delete().await()
    }

    /**
     * Materializes every occurrence of every active template that is *due but not yet
     * posted* into a real expense, up to and including [currentMonthKey].
     *
     * Two things this deliberately gets right:
     *
     *  - **Back-fill.** A template posts once per calendar month, but the app may not
     *    be opened every month. So rather than only ever touching the current month,
     *    this walks forward from the month after `lastPostedMonth` (or the template's
     *    creation month on first run) up to now, posting each month it missed. Skip
     *    July and open the app in August and July's rent still lands.
     *
     *  - **Correct date / no future posts.** An occurrence is posted only once its
     *    `dayOfMonth` has actually arrived. Rent due on the 28th isn't logged three
     *    weeks early just because you opened the app on the 1st — the current month is
     *    left untouched until the 28th, then picked up on the next run.
     *
     * Idempotent AND race-safe: all of one template's due months are posted inside a
     * single Firestore transaction that re-reads `lastPostedMonth` first. The two
     * callers — the on-open pass in FinanceViewModel and the 24h ReminderWorker — can
     * therefore run concurrently without double-posting: whichever transaction commits
     * second finds its read of the template stale (the guard already advanced), retries,
     * sees nothing due, and writes nothing. Advancing the guard and incrementing the
     * month total live in the same atomic unit, so a total can never drift from its
     * expenses.
     */
    suspend fun postDueRecurring(currentMonthKey: String) {
        val today = LocalDate.now()
        val currentMonth = runCatching { YearMonth.parse(currentMonthKey) }.getOrElse { YearMonth.now() }

        val actives = recurringRef()
            .whereEqualTo("active", true)
            .get().await().documents

        for (d in actives) {
            val templateRef = recurringRef().document(d.id)

            // One transaction per template. Isolating each template means one
            // template's failure can't abort another's, while the transaction's
            // optimistic lock on the template doc still closes the double-post race.
            runCatching {
                db.runTransaction { txn ->
                    val snap = txn.get(templateRef)
                    // Re-check inside the txn: the template may have been switched
                    // off between the query above and now.
                    if (!(snap.getBoolean("active") ?: true)) return@runTransaction null
                    val lastPosted = snap.getString("lastPostedMonth") ?: ""
                    // All posting fields come from the in-transaction read, not the
                    // pre-transaction query snapshot `d` — otherwise a concurrent
                    // edit (rent ₹10k → ₹12k) between the query and the commit would
                    // post at the stale amount while the retry logic happily commits
                    // (only `lastPostedMonth` would be re-checked).
                    val amount = roundMoney(snap.getDouble("amount") ?: 0.0)
                    val category = snap.getString("category") ?: "other"
                    val note = snap.getString("note") ?: ""
                    val day = (snap.getLong("dayOfMonth") ?: 1L).toInt()
                    val createdAt = snap.getString("createdAt") ?: ""
                    val createdMonth = runCatching { YearMonth.parse(createdAt.substring(0, 7)) }.getOrNull()
                    val createdDate = runCatching { LocalDate.parse(createdAt) }.getOrNull()

                    // Earliest month we might owe an expense for: the month after the
                    // last one posted, or the creation month on the very first run so
                    // we never back-post before the template existed.
                    val startMonth = when {
                        lastPosted.isNotEmpty() -> runCatching { YearMonth.parse(lastPosted).plusMonths(1) }.getOrNull()
                        else -> createdMonth
                    } ?: currentMonth

                    var month = startMonth
                    var lastWritten: String? = null
                    while (!month.isAfter(currentMonth)) {
                        val clampedDay = day.coerceIn(1, month.lengthOfMonth())
                        val dueDate = month.atDay(clampedDay)

                        // Day hasn't arrived yet (only possible for the current month —
                        // every earlier month is fully past). Stop; a later run picks
                        // it up on/after the due day, so nothing posts early.
                        if (dueDate.isAfter(today)) break

                        // Skip occurrences dated before the template was created.
                        if (createdDate != null && dueDate.isBefore(createdDate)) {
                            month = month.plusMonths(1)
                            continue
                        }

                        val monthRef = monthsRef().document(month.toString())
                        txn.set(
                            monthRef.collection("expenses").document(),
                            mapOf("category" to category, "amount" to amount, "note" to note, "date" to dueDate.toString())
                        )
                        txn.set(monthRef, mapOf("totalExpenses" to FieldValue.increment(amount)), SetOptions.merge())
                        lastWritten = month.toString()
                        month = month.plusMonths(1)
                    }

                    // Advance the guard only to the last month we actually posted, so a
                    // not-yet-due current month is retried next run.
                    if (lastWritten != null) txn.update(templateRef, "lastPostedMonth", lastWritten)
                    lastWritten
                }.await()
            }
        }
    }

    // ── Quick-add templates ─────────────────────────────────────────────────
    // Stored as users/{uid}/templates/{id}. One-tap chips on Add Entry that
    // prefill category + amount + note. Ordered newest-first so a freshly saved
    // template surfaces at the front of the row.

    fun observeTemplates(): Flow<List<Template>> = callbackFlow {
        val reg = templatesRef().orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                val list = snap?.documents?.map { d ->
                    Template(
                        id = d.id,
                        label = d.getString("label") ?: "",
                        category = d.getString("category") ?: "other",
                        amount = d.getDouble("amount") ?: 0.0,
                        note = d.getString("note") ?: "",
                        createdAt = d.getString("createdAt") ?: ""
                    )
                }.orEmpty()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }.retryOnSnapshotError()

    suspend fun addTemplate(template: Template) {
        templatesRef().document().set(
            mapOf(
                "label" to template.label,
                "category" to template.category,
                "amount" to template.amount,
                "note" to template.note,
                "createdAt" to template.createdAt
            )
        ).await()
    }

    suspend fun deleteTemplate(id: String) {
        templatesRef().document(id).delete().await()
    }

    /**
     * A complete, portable JSON snapshot of everything this user owns — months and their
     * expenses, budgets, goals and their contributions, people and their ledgers, plus
     * custom categories, recurring templates and quick-add templates. This is the real
     * "backup": unlike the expenses-only CSV/PDF, losing an account and restoring from
     * this file would preserve the udhaar ledger and savings goals too.
     *
     * Built with Android's bundled org.json (no extra dependency). Every document is read
     * once here; it's an explicit user action on a bounded dataset, so the fan-out is fine.
     */
    suspend fun buildBackupJson(generatedAt: String): String {
        val root = org.json.JSONObject()
        root.put("schema", "khaata-backup")
        root.put("version", 1)
        root.put("uid", uid)
        root.put("generatedAt", generatedAt)

        // Months + their expenses.
        val monthsArr = org.json.JSONArray()
        for (m in monthsRef().get().await().documents) {
            val monthObj = org.json.JSONObject()
                .put("monthKey", m.id)
                .put("income", m.getDouble("income") ?: 0.0)
                .put("totalExpenses", m.getDouble("totalExpenses") ?: 0.0)
            val expArr = org.json.JSONArray()
            for (e in m.reference.collection("expenses").get().await().documents) {
                expArr.put(
                    org.json.JSONObject()
                        .put("id", e.id)
                        .put("category", e.getString("category") ?: "other")
                        .put("amount", e.getDouble("amount") ?: 0.0)
                        .put("note", e.getString("note") ?: "")
                        .put("date", e.getString("date") ?: "")
                )
            }
            monthObj.put("expenses", expArr)
            monthsArr.put(monthObj)
        }
        root.put("months", monthsArr)

        // Budgets (flat collection).
        val budgetsArr = org.json.JSONArray()
        for (b in budgetsRef().get().await().documents) {
            budgetsArr.put(
                org.json.JSONObject()
                    .put("id", b.id)
                    .put("category", b.getString("category") ?: "other")
                    .put("limitAmount", b.getDouble("limitAmount") ?: 0.0)
                    .put("monthKey", b.getString("monthKey") ?: "")
            )
        }
        root.put("budgets", budgetsArr)

        // Goals + their contributions.
        val goalsArr = org.json.JSONArray()
        for (g in goalsRef().get().await().documents) {
            @Suppress("UNCHECKED_CAST")
            val rawContrib = g.get("monthlyContributions") as? Map<String, Any?> ?: emptyMap()
            val monthly = org.json.JSONObject()
            rawContrib.forEach { (k, v) -> monthly.put(k, (v as? Number)?.toDouble() ?: 0.0) }
            val goalObj = org.json.JSONObject()
                .put("id", g.id)
                .put("name", g.getString("name") ?: "")
                .put("targetAmount", g.getDouble("targetAmount") ?: 0.0)
                .put("targetDate", g.getString("targetDate") ?: "")
                .put("createdAt", g.getString("createdAt") ?: "")
                .put("savedAmount", g.getDouble("savedAmount") ?: 0.0)
                .put("monthlyContributions", monthly)
            val contribArr = org.json.JSONArray()
            for (c in g.reference.collection("contributions").get().await().documents) {
                contribArr.put(
                    org.json.JSONObject()
                        .put("id", c.id)
                        .put("amount", c.getDouble("amount") ?: 0.0)
                        .put("date", c.getString("date") ?: "")
                )
            }
            goalObj.put("contributions", contribArr)
            goalsArr.put(goalObj)
        }
        root.put("goals", goalsArr)

        // People (udhaar) + their ledgers.
        val peopleArr = org.json.JSONArray()
        for (p in contactsRef().get().await().documents) {
            val personObj = org.json.JSONObject()
                .put("id", p.id)
                .put("name", p.getString("name") ?: "")
                .put("note", p.getString("note") ?: "")
                .put("balance", p.getDouble("balance") ?: 0.0)
                .put("createdAt", p.getString("createdAt") ?: "")
            val ledgerArr = org.json.JSONArray()
            for (l in p.reference.collection("ledger").get().await().documents) {
                ledgerArr.put(
                    org.json.JSONObject()
                        .put("id", l.id)
                        .put("amount", l.getDouble("amount") ?: 0.0)
                        .put("note", l.getString("note") ?: "")
                        .put("date", l.getString("date") ?: "")
                )
            }
            personObj.put("ledger", ledgerArr)
            peopleArr.put(personObj)
        }
        root.put("people", peopleArr)

        // Custom categories, recurring templates, quick-add templates.
        val categoriesArr = org.json.JSONArray()
        for (c in categoriesRef().get().await().documents) {
            categoriesArr.put(
                org.json.JSONObject()
                    .put("key", c.id)
                    .put("label", c.getString("label") ?: "")
                    .put("colorArgb", c.getLong("colorArgb") ?: 0L)
                    .put("iconKey", c.getString("iconKey") ?: "category")
                    .put("order", c.getLong("order") ?: 0L)
            )
        }
        root.put("categories", categoriesArr)

        val recurringArr = org.json.JSONArray()
        for (r in recurringRef().get().await().documents) {
            recurringArr.put(
                org.json.JSONObject()
                    .put("id", r.id)
                    .put("category", r.getString("category") ?: "other")
                    .put("amount", r.getDouble("amount") ?: 0.0)
                    .put("note", r.getString("note") ?: "")
                    .put("dayOfMonth", r.getLong("dayOfMonth") ?: 1L)
                    .put("active", r.getBoolean("active") ?: true)
                    .put("createdAt", r.getString("createdAt") ?: "")
                    .put("lastPostedMonth", r.getString("lastPostedMonth") ?: "")
            )
        }
        root.put("recurring", recurringArr)

        val templatesArr = org.json.JSONArray()
        for (t in templatesRef().get().await().documents) {
            templatesArr.put(
                org.json.JSONObject()
                    .put("id", t.id)
                    .put("label", t.getString("label") ?: "")
                    .put("category", t.getString("category") ?: "other")
                    .put("amount", t.getDouble("amount") ?: 0.0)
                    .put("note", t.getString("note") ?: "")
                    .put("createdAt", t.getString("createdAt") ?: "")
            )
        }
        root.put("templates", templatesArr)

        return root.toString(2)
    }

    /**
     * Restores everything from a [buildBackupJson] file back into this user's account.
     *
     * Semantics: **overwrite by original id**. Every document is written with `set()`
     * under the same id it had in the backup, so restoring into an empty account
     * rebuilds it exactly (running totals like savedAmount/balance/totalExpenses are
     * stored absolute values in the backup, so they land consistent with the
     * subcollection rows). Restoring over an account that still has data merges the two
     * by id — matching ids are overwritten, and any local docs NOT in the backup are
     * left untouched (which can leave those months' totals inconsistent; the intended
     * use is rebuilding a lost/empty account, and the UI says so).
     *
     * Writes are committed in ≤450-op chunks to respect Firestore's 500-op batch cap.
     * Returns the number of documents written. Throws IllegalArgumentException on a file
     * that isn't a Khaata backup so the caller can show a clear message.
     */
    suspend fun restoreFromBackup(json: String): Int {
        val root = runCatching { org.json.JSONObject(json) }
            .getOrElse { throw IllegalArgumentException("Not a valid backup file.") }
        if (root.optString("schema") != "khaata-backup") {
            throw IllegalArgumentException("This file isn't a Khaata backup.")
        }

        // (reference, data) pairs collected first, then flushed in bounded batches.
        val writes = mutableListOf<Pair<DocumentReference, Map<String, Any?>>>()

        // Months + expenses.
        val months = root.optJSONArray("months") ?: org.json.JSONArray()
        for (i in 0 until months.length()) {
            val m = months.getJSONObject(i)
            val monthKey = m.getString("monthKey")
            val monthRef = monthsRef().document(monthKey)
            writes += monthRef to mapOf(
                "income" to m.optDouble("income", 0.0),
                "totalExpenses" to m.optDouble("totalExpenses", 0.0)
            )
            val expenses = m.optJSONArray("expenses") ?: org.json.JSONArray()
            for (j in 0 until expenses.length()) {
                val e = expenses.getJSONObject(j)
                writes += monthRef.collection("expenses").document(e.getString("id")) to mapOf(
                    "category" to e.optString("category", "other"),
                    "amount" to e.optDouble("amount", 0.0),
                    "note" to e.optString("note", ""),
                    "date" to e.optString("date", "")
                )
            }
        }

        // Budgets.
        val budgets = root.optJSONArray("budgets") ?: org.json.JSONArray()
        for (i in 0 until budgets.length()) {
            val b = budgets.getJSONObject(i)
            writes += budgetsRef().document(b.getString("id")) to mapOf(
                "category" to b.optString("category", "other"),
                "limitAmount" to b.optDouble("limitAmount", 0.0),
                "monthKey" to b.optString("monthKey", "")
            )
        }

        // Goals + contributions.
        val goals = root.optJSONArray("goals") ?: org.json.JSONArray()
        for (i in 0 until goals.length()) {
            val g = goals.getJSONObject(i)
            val goalRef = goalsRef().document(g.getString("id"))
            val monthly = g.optJSONObject("monthlyContributions") ?: org.json.JSONObject()
            val monthlyMap = mutableMapOf<String, Double>()
            monthly.keys().forEach { k -> monthlyMap[k] = monthly.optDouble(k, 0.0) }
            writes += goalRef to mapOf(
                "name" to g.optString("name", ""),
                "targetAmount" to g.optDouble("targetAmount", 0.0),
                "targetDate" to g.optString("targetDate", ""),
                "createdAt" to g.optString("createdAt", ""),
                "savedAmount" to g.optDouble("savedAmount", 0.0),
                "monthlyContributions" to monthlyMap
            )
            val contribs = g.optJSONArray("contributions") ?: org.json.JSONArray()
            for (j in 0 until contribs.length()) {
                val c = contribs.getJSONObject(j)
                writes += goalRef.collection("contributions").document(c.getString("id")) to mapOf(
                    "amount" to c.optDouble("amount", 0.0),
                    "date" to c.optString("date", "")
                )
            }
        }

        // People + ledgers.
        val people = root.optJSONArray("people") ?: org.json.JSONArray()
        for (i in 0 until people.length()) {
            val p = people.getJSONObject(i)
            val personRef = contactsRef().document(p.getString("id"))
            writes += personRef to mapOf(
                "name" to p.optString("name", ""),
                "note" to p.optString("note", ""),
                "balance" to p.optDouble("balance", 0.0),
                "createdAt" to p.optString("createdAt", "")
            )
            val ledger = p.optJSONArray("ledger") ?: org.json.JSONArray()
            for (j in 0 until ledger.length()) {
                val l = ledger.getJSONObject(j)
                writes += personRef.collection("ledger").document(l.getString("id")) to mapOf(
                    "amount" to l.optDouble("amount", 0.0),
                    "note" to l.optString("note", ""),
                    "date" to l.optString("date", "")
                )
            }
        }

        // Categories.
        val categories = root.optJSONArray("categories") ?: org.json.JSONArray()
        for (i in 0 until categories.length()) {
            val c = categories.getJSONObject(i)
            writes += categoriesRef().document(c.getString("key")) to mapOf(
                "label" to c.optString("label", ""),
                "colorArgb" to c.optLong("colorArgb", 0L),
                "iconKey" to c.optString("iconKey", "category"),
                "order" to c.optLong("order", 0L)
            )
        }

        // Recurring templates.
        val recurring = root.optJSONArray("recurring") ?: org.json.JSONArray()
        for (i in 0 until recurring.length()) {
            val r = recurring.getJSONObject(i)
            writes += recurringRef().document(r.getString("id")) to mapOf(
                "category" to r.optString("category", "other"),
                "amount" to r.optDouble("amount", 0.0),
                "note" to r.optString("note", ""),
                "dayOfMonth" to r.optInt("dayOfMonth", 1),
                "active" to r.optBoolean("active", true),
                "createdAt" to r.optString("createdAt", ""),
                "lastPostedMonth" to r.optString("lastPostedMonth", "")
            )
        }

        // Quick-add templates.
        val templates = root.optJSONArray("templates") ?: org.json.JSONArray()
        for (i in 0 until templates.length()) {
            val t = templates.getJSONObject(i)
            writes += templatesRef().document(t.getString("id")) to mapOf(
                "label" to t.optString("label", ""),
                "category" to t.optString("category", "other"),
                "amount" to t.optDouble("amount", 0.0),
                "note" to t.optString("note", ""),
                "createdAt" to t.optString("createdAt", "")
            )
        }

        writes.chunked(450).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (ref, data) -> batch.set(ref, data) }
            batch.commit().await()
        }
        return writes.size
    }

    /**
     * Every expense across every month, newest first. Reads each month's subcollection
     * (bounded by month count) so it stays within the per-user security rules rather
     * than needing a collectionGroup index. monthKey is recoverable from each date.
     */
    suspend fun loadAllExpenses(): List<Expense> {
        val monthDocs = monthsRef().get().await().documents
        val all = mutableListOf<Expense>()
        for (m in monthDocs) {
            val expenses = m.reference.collection("expenses").get().await().documents.map { d ->
                Expense(
                    id = d.id,
                    category = d.getString("category") ?: "other",
                    amount = d.getDouble("amount") ?: 0.0,
                    note = d.getString("note") ?: "",
                    date = d.getString("date") ?: ""
                )
            }
            all += expenses
        }
        return all.sortedByDescending { it.date }
    }

    // ── Reconciliation ──────────────────────────────────────────────────────
    // The stored running totals (months.totalExpenses, goals.savedAmount +
    // monthlyContributions, contacts.balance) are derived-but-stored: maintained
    // by FieldValue.increment at write time so the UI never sums subcollections.
    // Any historical drift (pre-transaction double deletes, float error from
    // unrounded increments) is permanent until something recomputes them. This
    // pass is that something: recompute each total from its rows and repair any
    // that disagree. Safe to run repeatedly — a clean ledger writes nothing.

    /** What a reconciliation run found/fixed, for the Settings screen to report. */
    data class ReconcileReport(
        val monthsChecked: Int = 0,
        val monthsRepaired: Int = 0,
        val goalsChecked: Int = 0,
        val goalsRepaired: Int = 0,
        val peopleChecked: Int = 0,
        val peopleRepaired: Int = 0,
    ) {
        val repaired: Int get() = monthsRepaired + goalsRepaired + peopleRepaired
        val checked: Int get() = monthsChecked + goalsChecked + peopleChecked
    }

    /**
     * Repairs one aggregate doc if its stored value(s) disagree with [expected]
     * beyond half a paisa. A transaction, not a blind write: a legitimate concurrent
     * write (an expense added on another device mid-scan) bumps the doc between our
     * subcollection read and this commit, and overwriting blindly would erase it.
     * Instead the transaction re-derives "still wrong by exactly the drift we
     * measured?" from a fresh read: it re-reads the doc, recomputes the delta
     * against [expected] + [driftTolerance], and only writes when the fresh value
     * still matches the stale one we computed [expected] from.
     */
    private suspend fun repairIfDrifted(
        ref: DocumentReference,
        staleValue: Double,
        expected: Double,
        fields: (Double) -> Map<String, Any>,
        read: (com.google.firebase.firestore.DocumentSnapshot) -> Double,
    ): Boolean {
        if (kotlin.math.abs(staleValue - expected) < 0.005) return false
        return db.runTransaction { txn ->
            val snap = txn.get(ref)
            if (!snap.exists()) return@runTransaction false
            // The doc moved since we computed `expected` (concurrent legitimate
            // write) — skip; the next run re-checks against the new state.
            if (kotlin.math.abs(read(snap) - staleValue) >= 0.005) return@runTransaction false
            txn.set(ref, fields(expected), SetOptions.merge())
            true
        }.await()
    }

    /**
     * Recomputes every stored running total from its subcollection rows and repairs
     * drift. Read cost is bounded: one full read of expenses/contributions/ledger
     * rows per run — same order as a backup export. Meant to be triggered from
     * Settings ("Verify ledger"), not on every app open.
     */
    suspend fun reconcileTotals(): ReconcileReport {
        var report = ReconcileReport()

        // Months: totalExpenses vs the sum of the month's expense docs.
        val monthDocs = monthsRef().get().await().documents
        for (m in monthDocs) {
            val stored = m.getDouble("totalExpenses") ?: 0.0
            val actual = roundMoney(
                m.reference.collection("expenses").get().await()
                    .documents.sumOf { it.getDouble("amount") ?: 0.0 }
            )
            val repaired = repairIfDrifted(
                ref = m.reference,
                staleValue = stored,
                expected = actual,
                fields = { mapOf("totalExpenses" to it) },
                read = { it.getDouble("totalExpenses") ?: 0.0 },
            )
            report = report.copy(
                monthsChecked = report.monthsChecked + 1,
                monthsRepaired = report.monthsRepaired + if (repaired) 1 else 0
            )
        }

        // Goals: savedAmount + the monthlyContributions map vs the contribution docs.
        val goalDocs = goalsRef().get().await().documents
        for (g in goalDocs) {
            val storedSaved = g.getDouble("savedAmount") ?: 0.0
            @Suppress("UNCHECKED_CAST")
            val storedMonths = (g.get("monthlyContributions") as? Map<String, Number>).orEmpty()
            val rows = g.reference.collection("contributions").get().await().documents
            val actualSaved = roundMoney(rows.sumOf { it.getDouble("amount") ?: 0.0 })
            val actualMonths = rows
                .groupBy { com.khaata.app.data.model.monthKeyFromDate(it.getString("date") ?: "") }
                .mapValues { (_, docs) -> roundMoney(docs.sumOf { it.getDouble("amount") ?: 0.0 }) }
                .filterValues { it != 0.0 }
            val monthsDrifted = actualMonths.keys.union(storedMonths.keys).any { key ->
                kotlin.math.abs((storedMonths[key]?.toDouble() ?: 0.0) - (actualMonths[key] ?: 0.0)) >= 0.005
            }
            val repaired = if (monthsDrifted) {
                // Map drift can't reuse repairIfDrifted's scalar compare — repair the
                // whole aggregate in one transaction guarded on savedAmount instead.
                db.runTransaction { txn ->
                    val snap = txn.get(g.reference)
                    if (!snap.exists()) return@runTransaction false
                    if (kotlin.math.abs((snap.getDouble("savedAmount") ?: 0.0) - storedSaved) >= 0.005) return@runTransaction false
                    txn.set(
                        g.reference,
                        mapOf("savedAmount" to actualSaved, "monthlyContributions" to actualMonths),
                        SetOptions.merge()
                    )
                    true
                }.await()
            } else {
                repairIfDrifted(
                    ref = g.reference,
                    staleValue = storedSaved,
                    expected = actualSaved,
                    fields = { mapOf("savedAmount" to it) },
                    read = { it.getDouble("savedAmount") ?: 0.0 },
                )
            }
            report = report.copy(
                goalsChecked = report.goalsChecked + 1,
                goalsRepaired = report.goalsRepaired + if (repaired) 1 else 0
            )
        }

        // People: balance vs the sum of signed ledger amounts.
        val peopleDocs = contactsRef().get().await().documents
        for (p in peopleDocs) {
            val stored = p.getDouble("balance") ?: 0.0
            val actual = roundMoney(
                p.reference.collection("ledger").get().await()
                    .documents.sumOf { it.getDouble("amount") ?: 0.0 }
            )
            val repaired = repairIfDrifted(
                ref = p.reference,
                staleValue = stored,
                expected = actual,
                fields = { mapOf("balance" to it) },
                read = { it.getDouble("balance") ?: 0.0 },
            )
            report = report.copy(
                peopleChecked = report.peopleChecked + 1,
                peopleRepaired = report.peopleRepaired + if (repaired) 1 else 0
            )
        }

        return report
    }
}