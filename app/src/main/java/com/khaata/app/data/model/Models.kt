package com.khaata.app.data.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** A single kharcha (expense) entry. */
data class Expense(
    val id: String = "",
    val category: String = "other",
    val amount: Double = 0.0,
    val note: String = "",
    val date: String = "" // yyyy-MM-dd
)

/** Rolled-up totals for one calendar month, e.g. "2026-06". */
data class MonthSummary(
    val monthKey: String = "",
    val income: Double = 0.0,
    val totalExpenses: Double = 0.0
) {
    val netSavings: Double get() = income - totalExpenses
}

/** One "I saved ₹X toward this goal" log entry. */
data class Contribution(
    val id: String = "",
    val amount: Double = 0.0,
    val date: String = ""
)

/** A savings goal — the bike, the laptop, whatever you're saving toward. */
data class Goal(
    val id: String = "",
    val name: String = "",
    val targetAmount: Double = 0.0,
    val targetDate: String = "", // yyyy-MM-dd
    val createdAt: String = "",
    val savedAmount: Double = 0.0,
    // monthKey ("2026-06") -> total contributed that month, kept on the goal doc
    // itself so we never need a second round-trip to know this month's pace.
    val monthlyContributions: Map<String, Double> = emptyMap()
)

enum class GoalStatus { ACHIEVED, ON_TRACK, BEHIND, OVERDUE }

data class GoalStats(
    val daysLeft: Long,
    val monthsLeftRaw: Double,
    val remaining: Double,
    val achieved: Boolean,
    val overdue: Boolean,
    val requiredMonthly: Double,
    val contributedThisMonth: Double,
    val pct: Float,
    val status: GoalStatus
)

/** Same "are you on pace" math as the web version of Khaata. */
fun Goal.computeStats(currentMonthKey: String): GoalStats {
    val today = LocalDate.now()
    val target = runCatching { LocalDate.parse(targetDate) }.getOrDefault(today)
    val daysLeft = ChronoUnit.DAYS.between(today, target)
    val monthsLeftRaw = (daysLeft.toDouble() / 30.44).coerceAtLeast(0.0)
    val remaining = (targetAmount - savedAmount).coerceAtLeast(0.0)
    val achieved = remaining <= 0.0
    val overdue = !achieved && daysLeft < 0
    val effectiveMonths = if (achieved) 1.0 else monthsLeftRaw.coerceAtLeast(0.1)
    val requiredMonthly = if (achieved) 0.0 else remaining / effectiveMonths
    val contributedThisMonth = monthlyContributions[currentMonthKey] ?: 0.0
    val pct = if (targetAmount > 0) ((savedAmount / targetAmount) * 100).coerceIn(0.0, 100.0).toFloat() else 0f
    val status = when {
        achieved -> GoalStatus.ACHIEVED
        overdue -> GoalStatus.OVERDUE
        contributedThisMonth + 0.001 >= requiredMonthly -> GoalStatus.ON_TRACK
        else -> GoalStatus.BEHIND
    }
    return GoalStats(daysLeft, monthsLeftRaw, remaining, achieved, overdue, requiredMonthly, contributedThisMonth, pct, status)
}

fun currentMonthKey(): String = YearMonth.now().toString()
fun monthKeyFromDate(date: String): String = if (date.length >= 7) date.substring(0, 7) else currentMonthKey()
fun shiftMonth(key: String, delta: Int): String = YearMonth.parse(key).plusMonths(delta.toLong()).toString()
fun monthLabel(key: String): String {
    val ym = YearMonth.parse(key)
    val monthName = ym.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    return "$monthName ${ym.year}"
}
fun todayStr(): String = LocalDate.now().toString()
