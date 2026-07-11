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

/**
 * A recurring expense template (rent, a subscription). Auto-posts one expense per
 * month on [dayOfMonth]; [lastPostedMonth] ("2026-07") guards against double-posting.
 */
data class RecurringExpense(
    val id: String = "",
    val category: String = "other",
    val amount: Double = 0.0,
    val note: String = "",
    val dayOfMonth: Int = 1,
    val active: Boolean = true,
    val createdAt: String = "",
    val lastPostedMonth: String = ""
)

/**
 * A one-tap expense template ("Chai ₹10"). Prefills the Add Entry form with a
 * saved category + amount + note; [label] is the chip text shown to the user.
 */
data class Template(
    val id: String = "",
    val label: String = "",
    val category: String = "other",
    val amount: Double = 0.0,
    val note: String = "",
    val createdAt: String = ""
)

/** A monthly category budget, e.g. Food = ₹3,000 for 2026-06. */
data class Budget(
    val id: String = "",
    val category: String = "other",
    val limitAmount: Double = 0.0,
    val monthKey: String = ""
)

enum class BudgetStatus { ON_TRACK, WATCHING, OVER }

data class BudgetProgress(
    val category: String,
    val limitAmount: Double,
    val spentAmount: Double,
    val remainingAmount: Double,
    val pct: Float,
    val status: BudgetStatus,
    val daysLeftInMonth: Int,
    val requiredDailySpend: Double,
    val projectedRunout: Boolean,
    val projectedRunoutDays: Int
)

fun daysLeftInMonth(monthKey: String): Int {
    val ym = runCatching { YearMonth.parse(monthKey) }.getOrElse { YearMonth.now() }
    val today = LocalDate.now()
    val monthEnd = ym.atEndOfMonth()
    return when {
        ym.isBefore(YearMonth.from(today)) -> 0
        ym.isAfter(YearMonth.from(today)) -> ym.lengthOfMonth()
        else -> ChronoUnit.DAYS.between(today, monthEnd).toInt() + 1
    }.coerceAtLeast(0)
}

fun buildBudgetProgress(budget: Budget, spentAmount: Double): BudgetProgress {
    val remaining = (budget.limitAmount - spentAmount).coerceAtLeast(0.0)
    val pct = if (budget.limitAmount > 0.0) ((spentAmount / budget.limitAmount) * 100.0).coerceIn(0.0, 100.0).toFloat() else 0f
    val today = LocalDate.now()
    val status = when {
        spentAmount >= budget.limitAmount && budget.limitAmount > 0.0 -> BudgetStatus.OVER
        pct >= 80f -> BudgetStatus.WATCHING
        else -> BudgetStatus.ON_TRACK
    }
    val daysLeft = daysLeftInMonth(budget.monthKey)
    val monthStart = runCatching { YearMonth.parse(budget.monthKey).atDay(1) }.getOrElse { YearMonth.now().atDay(1) }
    val daysElapsed = ChronoUnit.DAYS.between(monthStart, today).toInt().coerceAtLeast(0) + 1
    val avgDailySpend = if (daysElapsed > 0) spentAmount / daysElapsed else spentAmount
    val projectedRunoutDays = if (avgDailySpend > 0.0) kotlin.math.ceil(remaining / avgDailySpend).toInt() else Int.MAX_VALUE
    val projectedRunout = projectedRunoutDays <= daysLeft && remaining > 0.0
    val requiredDailySpend = if (daysLeft > 0) remaining / daysLeft else remaining
    return BudgetProgress(
        category = budget.category,
        limitAmount = budget.limitAmount,
        spentAmount = spentAmount,
        remainingAmount = remaining,
        pct = pct,
        status = status,
        daysLeftInMonth = daysLeft,
        requiredDailySpend = requiredDailySpend,
        projectedRunout = projectedRunout,
        projectedRunoutDays = projectedRunoutDays
    )
}

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
    val status: GoalStatus,
    val nextMilestonePct: Int
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
    val nextMilestonePct = when {
        pct < 25f -> 25
        pct < 50f -> 50
        pct < 75f -> 75
        pct < 100f -> 100
        else -> 100
    }
    return GoalStats(daysLeft, monthsLeftRaw, remaining, achieved, overdue, requiredMonthly, contributedThisMonth, pct, status, nextMilestonePct)
}

/**
 * A person in the udhaar (credit) ledger — someone you've lent money to or borrowed
 * from. [balance] is the running net, kept on the doc itself (like a goal's savedAmount)
 * so a card never sums the ledger just to show who owes whom:
 *   balance > 0  → they owe you (you gave/lent)
 *   balance < 0  → you owe them (you got/borrowed)
 *   balance == 0 → settled
 */
data class Person(
    val id: String = "",
    val name: String = "",
    val note: String = "",
    val balance: Double = 0.0,
    val createdAt: String = ""
)

/**
 * One entry in a person's udhaar ledger. [amount] is the signed delta applied to the
 * person's balance: positive = you gave, negative = you got. So the running balance is
 * just the sum of every entry's amount.
 */
data class LedgerEntry(
    val id: String = "",
    val amount: Double = 0.0,
    val note: String = "",
    val date: String = "" // yyyy-MM-dd
)

fun currentMonthKey(): String = YearMonth.now().toString()
fun monthKeyFromDate(date: String): String = if (date.length >= 7) date.substring(0, 7) else currentMonthKey()
fun shiftMonth(key: String, delta: Int): String = YearMonth.parse(key).plusMonths(delta.toLong()).toString()
fun monthLabel(key: String): String {
    val ym = YearMonth.parse(key)
    val monthName = ym.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    return "$monthName ${ym.year}"
}
fun todayStr(): String = LocalDate.now().toString()
