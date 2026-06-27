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

fun Goal.forecast(): GoalForecast {
    val recentAverage = monthlyContributions.entries
        .sortedByDescending { it.key }
        .take(3)
        .map { it.value }
        .filter { it > 0.0 }
        .takeIf { it.isNotEmpty() }
        ?.average() ?: 0.0

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