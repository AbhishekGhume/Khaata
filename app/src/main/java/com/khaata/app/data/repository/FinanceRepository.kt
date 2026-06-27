package com.khaata.app.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.khaata.app.data.model.Contribution
import com.khaata.app.data.model.Expense
import com.khaata.app.data.model.Goal
import com.khaata.app.data.model.MonthSummary
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
 *   users/{uid}/goals/{id}                       -> { name, targetAmount, targetDate,
 *                                                      createdAt, savedAmount, monthlyContributions }
 *   users/{uid}/goals/{id}/contributions/{id}    -> { amount, date }
 */
class FinanceRepository(private val uid: String) {

    private val db = FirebaseFirestore.getInstance()
    private val userRoot get() = db.collection("users").document(uid)
    private fun monthsRef() = userRoot.collection("months")
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

    suspend fun deleteGoal(goalId: String) {
        val goalRef = goalsRef().document(goalId)
        val contribs = goalRef.collection("contributions").get().await()
        val batch = db.batch()
        for (doc in contribs.documents) batch.delete(doc.reference)
        batch.delete(goalRef)
        batch.commit().await()
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
