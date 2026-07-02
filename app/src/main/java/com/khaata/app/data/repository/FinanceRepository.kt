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
import com.khaata.app.data.model.computeStats
import com.khaata.app.data.model.currentMonthKey
import java.time.LocalDate
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
        expenseRef.set(
            mapOf(
                "category" to expense.category,
                "amount" to expense.amount,
                "note" to expense.note,
                "date" to expense.date
            )
        ).await()
        // set + merge with FieldValue.increment creates the month doc if it
        // doesn't exist yet, or atomically bumps the running total if it does.
        monthRef.set(mapOf("totalExpenses" to FieldValue.increment(expense.amount)), SetOptions.merge()).await()
    }

    suspend fun deleteExpense(monthKey: String, expenseId: String, amount: Double) {
        val monthRef = monthsRef().document(monthKey)
        monthRef.collection("expenses").document(expenseId).delete().await()
        monthRef.set(mapOf("totalExpenses" to FieldValue.increment(-amount)), SetOptions.merge()).await()
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
        goalRef.collection("contributions").document().set(mapOf("amount" to amount, "date" to date)).await()
        goalRef.update(
            mapOf(
                "savedAmount" to FieldValue.increment(amount),
                "monthlyContributions.$monthKey" to FieldValue.increment(amount)
            )
        ).await()
    }
}