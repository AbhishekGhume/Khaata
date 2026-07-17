package com.khaata.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khaata.app.data.model.GoalStatus
import com.khaata.app.data.model.BudgetStatus
import com.khaata.app.data.model.computeStats
import com.khaata.app.data.model.currentMonthKey
import com.khaata.app.ui.components.CategoryBarRow
import com.khaata.app.ui.components.ProgressStamp
import com.khaata.app.ui.components.StatusBadge
import com.khaata.app.ui.components.SummaryCard
import com.khaata.app.ui.theme.Gold
import com.khaata.app.ui.theme.Green
import com.khaata.app.ui.theme.GreenSoft
import com.khaata.app.ui.theme.Muted
import com.khaata.app.ui.theme.PaperCard
import com.khaata.app.ui.theme.PaperLine
import com.khaata.app.ui.theme.Rust
import com.khaata.app.ui.theme.RustSoft
import com.khaata.app.util.categoryMeta
import com.khaata.app.util.formatINR
import com.khaata.app.viewmodel.FinanceViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(viewModel: FinanceViewModel) {
    val monthSummary by viewModel.monthSummary.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val goals by viewModel.goals.collectAsState()
    val budgetProgress by viewModel.budgetProgress.collectAsState()
    val viewedMonthKey by viewModel.viewedMonthKey.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val isCurrentMonth = viewedMonthKey == currentMonthKey()

    val categoryTotals = remember(expenses, categories) {
        val totals = mutableMapOf<String, Double>()
        expenses.forEach { e -> totals[e.category] = (totals[e.category] ?: 0.0) + e.amount }
        val total = totals.values.sum().takeIf { it > 0 } ?: 1.0
        // Any category key present in expenses, resolved against the live list
        // (falls back to "Other" for keys whose category was deleted).
        totals.keys
            .map { key -> Triple(categoryMeta(key, categories), totals[key]!!, (totals[key]!! / total * 100).toFloat()) }
            .sortedByDescending { it.second }
    }

    val goalStats = remember(goals) { goals.map { it to it.computeStats(currentMonthKey()) } }
    val rolloverOffer by viewModel.rolloverOffer.collectAsState()
    val totalRequiredMonthly = goalStats.filter { !it.second.achieved }.sumOf { it.second.requiredMonthly }
    val totalContributedThisMonth = goalStats.sumOf { it.second.contributedThisMonth }

    // Key calculation: how much of the net savings is already earmarked for goals THIS month?
    // This is what the user is actually asking when they say "I saved 16360, why does my
    // net savings still say 24420?" — they want to see the allocation clearly.
    val goalsCommittedThisMonth = goals.sumOf { g ->
        g.monthlyContributions[viewedMonthKey] ?: 0.0
    }
    val freeAvailable = monthSummary.netSavings - goalsCommittedThisMonth

    val shortfall = totalRequiredMonthly - monthSummary.netSavings
    val budgetSpent = budgetProgress.sumOf { it.spentAmount }
    val budgetLimit = budgetProgress.sumOf { it.limitAmount }
    val overBudgets = budgetProgress.count { it.status == BudgetStatus.OVER }
    val watchingBudgets = budgetProgress.count { it.status == BudgetStatus.WATCHING }
    val projectedRunoutBudgets = budgetProgress.filter { it.projectedRunout }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // ── New-month rollover offer ───────────────────────────────────────
        rolloverOffer?.let { offer ->
            if (isCurrentMonth) {
                item {
                    Surface(color = GreenSoft, shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text("New month, fresh page", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Green)
                            Spacer(Modifier.height(4.dp))
                            val parts = buildList {
                                if (offer.budgetCount > 0) add("${offer.budgetCount} budget cap${if (offer.budgetCount == 1) "" else "s"}")
                                if (offer.income > 0.0) add("income of ${formatINR(offer.income)}")
                            }
                            Text(
                                "Carry over last month's ${parts.joinToString(" and ")} so you don't have to re-enter them?",
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.applyRollover() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Green)
                                ) { Text("Carry over") }
                                TextButton(onClick = { viewModel.dismissRollover() }) {
                                    Text("Start fresh", color = Muted)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Summary cards ──────────────────────────────────────────────────
        item {
            FlowRow(
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    label = "Income",
                    value = formatINR(monthSummary.income)
                )
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    label = "Kharcha",
                    value = formatINR(monthSummary.totalExpenses),
                    accent = Rust
                )
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    label = "Net Savings",
                    value = formatINR(monthSummary.netSavings),
                    accent = if (monthSummary.netSavings >= 0) Green else Rust,
                    sub = if (monthSummary.netSavings < 0) "spent more than earned" else null
                )
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    label = "Budget usage",
                    value = formatINR(budgetSpent),
                    accent = if (overBudgets > 0) Rust else if (watchingBudgets > 0) Gold else Green,
                    sub = if (budgetProgress.isEmpty()) {
                        "No caps set yet"
                    } else {
                        "of ${formatINR(budgetLimit)} across ${budgetProgress.size} caps"
                    }
                )
            }
        }

        // ── Savings breakdown card ─────────────────────────────────────────
        // This is the "missing link" between Net Savings and Goals.
        // Shows the user exactly where their surplus is going, so they
        // don't feel like their goal contributions disappeared.
        if (goalsCommittedThisMonth > 0.0 || goals.isNotEmpty()) {
            item {
                Surface(
                    color = PaperCard,
                    border = BorderStroke(1.dp, PaperLine),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Savings breakdown",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Muted
                        )
                        Spacer(Modifier.height(8.dp))
                        SavingsBreakdownRow(
                            label = "Net savings",
                            value = formatINR(monthSummary.netSavings),
                            color = if (monthSummary.netSavings >= 0) Green else Rust,
                            isBold = true
                        )
                        if (goalsCommittedThisMonth > 0.0) {
                            HorizontalDivider(color = PaperLine, modifier = Modifier.padding(vertical = 4.dp))
                            SavingsBreakdownRow(
                                label = "Committed to goals",
                                value = "− ${formatINR(goalsCommittedThisMonth)}",
                                color = Gold,
                                isBold = false
                            )
                            HorizontalDivider(color = PaperLine, modifier = Modifier.padding(vertical = 4.dp))
                            SavingsBreakdownRow(
                                label = "Freely available",
                                value = formatINR(freeAvailable),
                                color = if (freeAvailable >= 0) Green else Rust,
                                isBold = true
                            )
                        } else {
                            HorizontalDivider(color = PaperLine, modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                "No goal contributions logged for this month yet.",
                                color = Muted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // ── Budget health ─────────────────────────────────────────────────
        if (budgetProgress.isNotEmpty()) {
            item {
                val warningText = when {
                    projectedRunoutBudgets.isNotEmpty() -> "${projectedRunoutBudgets.size} budget${if (projectedRunoutBudgets.size == 1) " is" else "s are"} likely to run out before month end."
                    overBudgets > 0 -> "$overBudgets budget${if (overBudgets == 1) " is" else "s are"} over limit."
                    watchingBudgets > 0 -> "$watchingBudgets budget${if (watchingBudgets == 1) " is" else "s are"} near the limit."
                    else -> "All budgets are on track right now."
                }
                Surface(
                    color = if (projectedRunoutBudgets.isNotEmpty() || overBudgets > 0) RustSoft else GreenSoft,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        warningText,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp,
                        color = if (projectedRunoutBudgets.isNotEmpty() || overBudgets > 0) Rust else Green
                    )
                }
            }
        }

        // ── Goals pace ────────────────────────────────────────────────────
        if (isCurrentMonth && goalStats.isNotEmpty()) {
            item {
                val onPace = shortfall <= 0
                Surface(color = if (onPace) GreenSoft else RustSoft, shape = RoundedCornerShape(10.dp)) {
                    Text(
                        if (onPace)
                            "Nice — your net savings this month cover what's needed to stay on pace for all your goals (${formatINR(totalRequiredMonthly)} required, ${formatINR(totalContributedThisMonth)} already logged)."
                        else
                            "You need about ${formatINR(shortfall)} more in savings this month to stay on pace for all your goals. So far you've put ${formatINR(totalContributedThisMonth)} toward them.",
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.5.sp
                    )
                }
            }
        } else if (!isCurrentMonth && goalStats.isNotEmpty()) {
            item {
                Text(
                    "Viewing a different month — switch to the current month to see if you're on pace for your goals.",
                    color = Muted,
                    fontSize = 12.5.sp
                )
            }
        }

        // ── Spend breakdown ───────────────────────────────────────────────
        item { Text("Where the kharcha went", fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
        if (categoryTotals.isEmpty()) {
            item { Text("No expenses logged for this month yet.", color = Muted, fontSize = 13.sp) }
        } else {
            items(categoryTotals) { (meta, amount, pct) ->
                val budget = budgetProgress.firstOrNull { it.category == meta.key }
                CategoryBarRow(
                    label = meta.label,
                    color = when {
                        budget?.projectedRunout == true -> Rust
                        budget?.status == BudgetStatus.OVER -> Rust
                        budget?.status == BudgetStatus.WATCHING -> Gold
                        else -> meta.color
                    },
                    amount = formatINR(amount),
                    pct = pct
                )
            }
        }

        // ── Goal cards ────────────────────────────────────────────────────
        if (goals.isNotEmpty()) {
            item { Text("Your goals", fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    goalStats.forEach { (goal, stats) ->
                        Surface(
                            color = PaperCard,
                            border = BorderStroke(1.dp, PaperLine),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.widthIn(min = 260.dp)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                ProgressStamp(
                                    pct = stats.pct,
                                    color = if (stats.achieved) Green else if (stats.status == GoalStatus.BEHIND) Rust else Gold
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(goal.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(
                                        "${formatINR(goal.savedAmount)} of ${formatINR(goal.targetAmount)}",
                                        color = Muted,
                                        fontSize = 12.sp
                                    )
                                    // Show this month's contribution right on the Dashboard card
                                    val thisMonthAmount = goal.monthlyContributions[viewedMonthKey] ?: 0.0
                                    if (thisMonthAmount > 0.0) {
                                        Text(
                                            "${formatINR(thisMonthAmount)} saved this month",
                                            color = Green,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    StatusBadge(stats.status)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavingsBreakdownRow(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    isBold: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = if (isBold) com.khaata.app.ui.theme.Ink else Muted,
            fontWeight = if (isBold) FontWeight.SemiBold else FontWeight.Normal
        )
        Text(
            value,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = color
        )
    }
}