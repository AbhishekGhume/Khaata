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
import com.khaata.app.data.model.MonthSummary
import com.khaata.app.data.model.MonthlyAnalyticsPoint
import com.khaata.app.data.model.RecurringExpense
import com.khaata.app.data.model.computeStats
import com.khaata.app.data.model.currentMonthKey
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.khaata.app.util.CategoryMeta
import com.khaata.app.util.DEFAULT_CATEGORIES
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
 */
class FinanceRepository(private val uid: String) {

    private val db = FirebaseFirestore.getInstance()
    private val userRoot get() = db.collection("users").document(uid)
    private fun monthsRef() = userRoot.collection("months")
    private fun budgetsRef() = userRoot.collection("budgets")
    private fun goalsRef() = userRoot.collection("goals")
    private fun categoriesRef() = userRoot.collection("categories")
    private fun recurringRef() = userRoot.collection("recurring")

    fun observeMonthSummary(monthKey: String): Flow<MonthSummary> = callbackFlow {
        val reg = monthsRef().document(monthKey).addSnapshotListener { snap, err ->
            if (err != null) return@addSnapshotListener
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
    }

    fun observeExpenses(monthKey: String): Flow<List<Expense>> = callbackFlow {
        val reg = monthsRef().document(monthKey).collection("expenses")
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
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
    }

    fun observeAllMonths(): Flow<List<MonthSummary>> = callbackFlow {
        val reg = monthsRef().addSnapshotListener { snap, err ->
            if (err != null) return@addSnapshotListener
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
    }

    fun observeBudgets(monthKey: String): Flow<List<Budget>> = callbackFlow {
        val reg = budgetsRef().whereEqualTo("monthKey", monthKey).addSnapshotListener { snap, err ->
            if (err != null) return@addSnapshotListener
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
    }

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
                "limitAmount" to limitAmount,
                "monthKey" to monthKey
            )
        ).await()
    }

    suspend fun deleteBudget(monthKey: String, category: String) {
        budgetsRef().document("${monthKey}_$category").delete().await()
    }

    suspend fun latestBudgetProgress(monthKey: String): List<BudgetProgress> = loadBudgetProgress(monthKey)

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
            if (err != null) return@addSnapshotListener
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
    }

    fun observeContributions(goalId: String): Flow<List<Contribution>> = callbackFlow {
        val reg = goalsRef().document(goalId).collection("contributions")
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(5)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                val list = snap?.documents?.map { d ->
                    Contribution(id = d.id, amount = d.getDouble("amount") ?: 0.0, date = d.getString("date") ?: "")
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun setIncome(monthKey: String, income: Double) {
        monthsRef().document(monthKey).set(mapOf("income" to income), SetOptions.merge()).await()
    }

    suspend fun addExpense(monthKey: String, expense: Expense) {
        val monthRef = monthsRef().document(monthKey)
        val expenseRef = monthRef.collection("expenses").document()
        // One batch so the expense doc and the running `totalExpenses` can never
        // disagree: either both land or neither does. A crash/network drop
        // between two separate writes used to leave the total permanently off.
        db.batch().apply {
            set(
                expenseRef,
                mapOf(
                    "category" to expense.category,
                    "amount" to expense.amount,
                    "note" to expense.note,
                    "date" to expense.date
                )
            )
            // merge creates the month doc if absent, or atomically bumps the total.
            set(monthRef, mapOf("totalExpenses" to FieldValue.increment(expense.amount)), SetOptions.merge())
        }.commit().await()
    }

    suspend fun deleteExpense(monthKey: String, expenseId: String, amount: Double) {
        val monthRef = monthsRef().document(monthKey)
        db.batch().apply {
            delete(monthRef.collection("expenses").document(expenseId))
            set(monthRef, mapOf("totalExpenses" to FieldValue.increment(-amount)), SetOptions.merge())
        }.commit().await()
    }

    /**
     * Edits an existing expense atomically, adjusting the affected month total(s).
     * If the new date lands in a different month, the entry is moved: removed from
     * the old month (and its total) and re-created in the new month — all in one
     * batch so nothing is ever double-counted or lost midway.
     */
    suspend fun updateExpense(oldMonthKey: String, expenseId: String, oldAmount: Double, updated: Expense) {
        val newMonthKey = com.khaata.app.data.model.monthKeyFromDate(updated.date)
        val fields = mapOf(
            "category" to updated.category,
            "amount" to updated.amount,
            "note" to updated.note,
            "date" to updated.date
        )
        val batch = db.batch()
        if (newMonthKey == oldMonthKey) {
            val monthRef = monthsRef().document(oldMonthKey)
            batch.set(monthRef.collection("expenses").document(expenseId), fields)
            batch.set(monthRef, mapOf("totalExpenses" to FieldValue.increment(updated.amount - oldAmount)), SetOptions.merge())
        } else {
            val oldMonthRef = monthsRef().document(oldMonthKey)
            val newMonthRef = monthsRef().document(newMonthKey)
            batch.delete(oldMonthRef.collection("expenses").document(expenseId))
            batch.set(oldMonthRef, mapOf("totalExpenses" to FieldValue.increment(-oldAmount)), SetOptions.merge())
            batch.set(newMonthRef.collection("expenses").document(), fields)
            batch.set(newMonthRef, mapOf("totalExpenses" to FieldValue.increment(updated.amount)), SetOptions.merge())
        }
        batch.commit().await()
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
        db.batch().apply {
            set(goalRef.collection("contributions").document(), mapOf("amount" to amount, "date" to date))
            update(
                goalRef,
                mapOf(
                    "savedAmount" to FieldValue.increment(amount),
                    "monthlyContributions.$monthKey" to FieldValue.increment(amount)
                )
            )
        }.commit().await()
    }

    /**
     * Edits (or, when [newAmount] is 0, removes) the total saved for one month of a
     * goal. GoalsScreen shows contributions rolled up per month, so an edit operates
     * on that month's aggregate: `savedAmount` moves by the delta and the month's map
     * entry is set to the exact new value (or deleted). Atomic via a batch.
     */
    suspend fun editMonthlyContribution(goalId: String, monthKey: String, oldAmount: Double, newAmount: Double) {
        val goalRef = goalsRef().document(goalId)
        val monthValue: Any = if (newAmount <= 0.0) FieldValue.delete() else newAmount
        goalRef.update(
            mapOf(
                "savedAmount" to FieldValue.increment(newAmount - oldAmount),
                "monthlyContributions.$monthKey" to monthValue
            )
        ).await()
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

    // ── Categories ──────────────────────────────────────────────────────────
    // Stored as users/{uid}/categories/{key}. Built-ins are seeded once and are
    // fully editable thereafter. A deleted category needs no data migration: an
    // unknown key falls back to "Other" wherever categoryMeta() is used.

    fun observeCategories(): Flow<List<CategoryMeta>> = callbackFlow {
        val reg = categoriesRef().orderBy("order").addSnapshotListener { snap, err ->
            if (err != null) return@addSnapshotListener
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
    }

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
        categoriesRef().document(key).set(
            mapOf(
                "label" to label,
                "colorArgb" to color.toArgb().toLong(),
                "iconKey" to iconKey,
                "order" to order
            )
        ).await()
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
            if (err != null) return@addSnapshotListener
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
    }

    suspend fun addRecurring(recurring: RecurringExpense) {
        recurringRef().document().set(
            mapOf(
                "category" to recurring.category,
                "amount" to recurring.amount,
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
                "amount" to amount,
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
            val amount = d.getDouble("amount") ?: 0.0
            val category = d.getString("category") ?: "other"
            val note = d.getString("note") ?: ""
            val day = (d.getLong("dayOfMonth") ?: 1L).toInt()
            val createdAt = d.getString("createdAt") ?: ""
            val createdMonth = runCatching { YearMonth.parse(createdAt.substring(0, 7)) }.getOrNull()
            val createdDate = runCatching { LocalDate.parse(createdAt) }.getOrNull()

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
}