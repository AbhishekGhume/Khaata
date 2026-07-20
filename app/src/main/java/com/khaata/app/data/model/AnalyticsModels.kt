package com.khaata.app.data.model

import java.time.LocalDate
import kotlin.math.ceil

data class MonthlyAnalyticsPoint(
    val monthKey: String,
    val income: Double,
    val expenses: Double
) {
    val netSavings: Double get() = income - expenses
    val savingsRate: Double get() = if (income > 0.0) (netSavings / income) * 100.0 else 0.0
}

data class CategoryTrendItem(
    val categoryKey: String,
    val currentAmount: Double,
    val previousAmount: Double
) {
    val delta: Double get() = currentAmount - previousAmount
    val deltaPct: Double get() = when {
        previousAmount > 0.0 -> (delta / previousAmount) * 100.0
        currentAmount > 0.0 -> 100.0
        else -> 0.0
    }
}

data class AnalyticsSnapshot(
    val months: List<MonthlyAnalyticsPoint> = emptyList(),
    val categoryTrends: List<CategoryTrendItem> = emptyList(),
    val biggestExpenses: List<Expense> = emptyList(),
    val averageSavingsRate: Double = 0.0,
    val bestMonthKey: String? = null,
    val worstMonthKey: String? = null
)

data class GoalForecast(
    val goalId: String,
    val name: String,
    val remainingAmount: Double,
    val averageMonthlyContribution: Double,
    val estimatedHitDate: String?
)

/**
 * "At your recent pace, when would this land?" — averaged over the trailing 3
 * *calendar* months (zeros included), not the last 3 months that happen to have
 * entries. A goal untouched for a year must forecast "no recent pace", not a
 * near-term hit date computed from its old contribution bursts.
 *
 * The window is clamped to months since the goal was created (a goal made last
 * month isn't penalized for the two months it didn't exist), and a goal created
 * this month falls back to this month's contribution as its only signal.
 */
fun Goal.forecast(currentMonthKey: String = currentMonthKey()): GoalForecast {
    val createdMonth = monthKeyFromDate(createdAt)
    // Trailing full months only — the in-progress current month would understate
    // the pace. yyyy-MM strings compare chronologically.
    val window = (1..3).map { shiftMonth(currentMonthKey, -it) }
        .filter { it >= createdMonth }
        .ifEmpty { listOf(currentMonthKey) }
    val recentAverage = window.map { monthlyContributions[it] ?: 0.0 }.average()

    val remainingAmount = (targetAmount - savedAmount).coerceAtLeast(0.0)
    val estimatedHitDate = when {
        remainingAmount <= 0.0 -> LocalDate.now().toString()
        recentAverage > 0.0 -> LocalDate.now().plusMonths(ceil(remainingAmount / recentAverage).toLong()).toString()
        else -> null
    }

    return GoalForecast(
        goalId = id,
        name = name,
        remainingAmount = remainingAmount,
        averageMonthlyContribution = recentAverage,
        estimatedHitDate = estimatedHitDate
    )
}