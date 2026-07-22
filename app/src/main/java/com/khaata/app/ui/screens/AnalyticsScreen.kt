package com.khaata.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.khaata.app.data.model.MonthSummary
import com.khaata.app.data.model.monthLabel
import com.khaata.app.ui.components.staggerDelay
import com.khaata.app.ui.components.valueReveal
import com.khaata.app.ui.theme.Gold
import com.khaata.app.ui.theme.Green
import com.khaata.app.ui.theme.Muted
import com.khaata.app.ui.theme.PaperCard
import com.khaata.app.ui.theme.PaperLine
import com.khaata.app.ui.theme.Rust
import com.khaata.app.util.formatINR
import com.khaata.app.viewmodel.FinanceViewModel
import kotlin.math.abs
import kotlin.math.max

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalyticsScreen(viewModel: FinanceViewModel) {
    val viewedMonthKey by viewModel.viewedMonthKey.collectAsState()
    val monthSummary by viewModel.monthSummary.collectAsState()
    val allMonths by viewModel.allMonths.collectAsState()
    val budgetProgress by viewModel.budgetProgress.collectAsState()
    val goals by viewModel.goals.collectAsState()

    val sortedMonths = remember(allMonths) { allMonths.sortedBy { it.monthKey } }
    val previousMonth = remember(viewedMonthKey, sortedMonths) {
        sortedMonths.filter { it.monthKey < viewedMonthKey }.maxByOrNull { it.monthKey }
    }

    val yearKey = viewedMonthKey.take(4)
    val yearMonthsTillNow = remember(viewedMonthKey, sortedMonths) {
        sortedMonths.filter { it.monthKey.startsWith("$yearKey-") && it.monthKey <= viewedMonthKey }
    }
    val previousMonthsSnapshot = remember(viewedMonthKey, sortedMonths) {
        sortedMonths.filter { it.monthKey < viewedMonthKey }.takeLast(3).reversed()
    }
    val graphMonths = remember(viewedMonthKey, sortedMonths) {
        sortedMonths.filter { it.monthKey <= viewedMonthKey }.takeLast(6)
    }

    val yearIncome = yearMonthsTillNow.sumOf { it.income }
    val yearExpense = yearMonthsTillNow.sumOf { it.totalExpenses }
    val yearNet = yearIncome - yearExpense
    val avgYearNet = if (yearMonthsTillNow.isEmpty()) 0.0 else yearNet / yearMonthsTillNow.size

    val goalsSavedByMonth = remember(goals) {
        val totals = mutableMapOf<String, Double>()
        goals.forEach { goal ->
            goal.monthlyContributions.forEach { (monthKey, amount) ->
                totals[monthKey] = (totals[monthKey] ?: 0.0) + amount
            }
        }
        totals
    }

    val goalsSavedInViewedMonth = goalsSavedByMonth[viewedMonthKey] ?: 0.0
    val freeAvailableThisMonth = monthSummary.netSavings - goalsSavedInViewedMonth
    val goalsSavedThisYear = goals.sumOf { goal ->
        goal.monthlyContributions
            .filterKeys { it.startsWith("$yearKey-") && it <= viewedMonthKey }
            .values
            .sum()
    }
    val yearFree = yearNet - goalsSavedThisYear

    val budgetSpent = budgetProgress.sumOf { it.spentAmount }
    val budgetLimit = budgetProgress.sumOf { it.limitAmount }
    val overBudgets = budgetProgress.count { it.status.name == "OVER" }

    val plainInsight = when {
        monthSummary.income <= 0.0 -> "Set this month's income first so analytics can guide you better."
        monthSummary.netSavings < 0.0 -> "You are spending more than income this month. Try reducing kharcha in top categories."
        overBudgets > 0 -> "Some budgets are crossed. Prioritize only essential spending for the rest of this month."
        goals.isNotEmpty() && goalsSavedInViewedMonth <= 0.0 -> "You have goals but no savings logged for this month yet. Even a small amount helps."
        else -> "You are on a healthy track this month. Keep logging regularly for better insights."
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Easy Analytics", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    "Simple money view for ${monthLabel(viewedMonthKey)}. Use month selector to check previous months.",
                    color = Muted,
                    fontSize = 13.sp
                )
            }
        }

        item {
            AnalyticsSection(
                title = "This month (${monthLabel(viewedMonthKey)})",
                subtitle = "How things stand till now"
            ) {
                FlowRow(
                    maxItemsInEachRow = 2,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SimpleMetricCard(Modifier.weight(1f), "Income", formatINR(monthSummary.income), "Money received", Green)
                    SimpleMetricCard(Modifier.weight(1f), "Kharcha", formatINR(monthSummary.totalExpenses), "Spent till now", Rust)
                    SimpleMetricCard(Modifier.weight(1f), "Saved to goals", formatINR(goalsSavedInViewedMonth), "Logged this month", Gold)
                    SimpleMetricCard(
                        Modifier.weight(1f),
                        "Free amount",
                        formatINR(freeAvailableThisMonth),
                        "Can be used freely now",
                        if (freeAvailableThisMonth >= 0) Green else Rust
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Net savings before goal allocation: ${formatINR(monthSummary.netSavings)}",
                    color = Muted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        item {
            AnalyticsSection(
                title = "Simple graph view",
                subtitle = "Quick visuals anyone can understand"
            ) {
                Text("Where this month's income went", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                IncomeSplitBar(
                    income = monthSummary.income,
                    kharcha = monthSummary.totalExpenses,
                    goalsSaved = goalsSavedInViewedMonth,
                    freeAmount = freeAvailableThisMonth
                )
                Spacer(Modifier.height(6.dp))
                Text("Red: kharcha  Gold: goals  Green: free", color = Muted, fontSize = 11.sp)

                Spacer(Modifier.height(14.dp))
                Text("Free amount trend (last ${graphMonths.size} months)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                if (graphMonths.isEmpty()) {
                    Text("No month data yet.", color = Muted, fontSize = 12.sp)
                } else {
                    FreeTrendGraph(
                        months = graphMonths,
                        goalsSavedByMonth = goalsSavedByMonth
                    )
                }
            }
        }

        item {
            AnalyticsSection(
                title = "Budget status (till now)",
                subtitle = "Quick check of your monthly caps"
            ) {
                FlowRow(
                    maxItemsInEachRow = 2,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SimpleMetricCard(Modifier.weight(1f), "Budget used", formatINR(budgetSpent), "Spent under budgets", Green)
                    SimpleMetricCard(Modifier.weight(1f), "Budget limit", formatINR(budgetLimit), "Total cap set", Gold)
                }
                if (budgetProgress.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("No budget caps set for this month.", color = Muted, fontSize = 12.sp)
                } else if (overBudgets > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("$overBudgets categories are over budget.", color = Rust, fontSize = 12.sp)
                }
            }
        }

        item {
            // `previousMonth` is the most recent month with data before the viewed one,
            // which isn't necessarily the calendar-previous month (a user can skip a
            // month). Label the comparison with the actual month being compared so a
            // May-vs-July gap never masquerades as "vs last month".
            val isAdjacent = previousMonth != null &&
                previousMonth.monthKey == com.khaata.app.data.model.shiftMonth(viewedMonthKey, -1)
            AnalyticsSection(
                title = "Compared to previous month",
                subtitle = if (previousMonth == null) "Selected month vs earlier data"
                    else "${monthLabel(viewedMonthKey)} vs ${monthLabel(previousMonth.monthKey)}"
            ) {
                if (previousMonth == null) {
                    Text("No previous month data available yet.", color = Muted, fontSize = 12.sp)
                } else {
                    val comparedLabel = monthLabel(previousMonth.monthKey)
                    if (!isAdjacent) {
                        Text(
                            "No data for the month right before this — comparing against $comparedLabel, " +
                                "the latest month with entries.",
                            color = Muted,
                            fontSize = 11.5.sp
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    val netDelta = monthSummary.netSavings - previousMonth.netSavings
                    val previousGoalsSaved = goalsSavedByMonth[previousMonth.monthKey] ?: 0.0
                    val previousFree = previousMonth.netSavings - previousGoalsSaved
                    ComparisonRow("Income", monthSummary.income, previousMonth.income)
                    HorizontalDivider(color = PaperLine, modifier = Modifier.padding(vertical = 6.dp))
                    ComparisonRow("Kharcha", monthSummary.totalExpenses, previousMonth.totalExpenses)
                    HorizontalDivider(color = PaperLine, modifier = Modifier.padding(vertical = 6.dp))
                    ComparisonRow("Net savings", monthSummary.netSavings, previousMonth.netSavings)
                    HorizontalDivider(color = PaperLine, modifier = Modifier.padding(vertical = 6.dp))
                    ComparisonRow("Free available", freeAvailableThisMonth, previousFree)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (netDelta >= 0.0) {
                            "Good: net savings improved by ${formatINR(netDelta)} from $comparedLabel."
                        } else {
                            "Alert: net savings dropped by ${formatINR(kotlin.math.abs(netDelta))} from $comparedLabel."
                        },
                        color = if (netDelta >= 0.0) Green else Rust,
                        fontSize = 12.sp
                    )
                }
            }
        }

        item {
            AnalyticsSection(
                title = "Previous months snapshot",
                subtitle = "Last 3 months before selected month"
            ) {
                if (previousMonthsSnapshot.isEmpty()) {
                    Text("No older months found.", color = Muted, fontSize = 12.sp)
                } else {
                    previousMonthsSnapshot.forEachIndexed { index, month ->
                        MonthLine(month)
                        if (index != previousMonthsSnapshot.lastIndex) {
                            HorizontalDivider(color = PaperLine, modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }
                }
            }
        }

        item {
            AnalyticsSection(
                title = "Yearly summary ($yearKey)",
                subtitle = "From January till selected month"
            ) {
                FlowRow(
                    maxItemsInEachRow = 2,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SimpleMetricCard(Modifier.weight(1f), "Total income", formatINR(yearIncome), "Year till now", Green)
                    SimpleMetricCard(Modifier.weight(1f), "Total kharcha", formatINR(yearExpense), "Year till now", Rust)
                    SimpleMetricCard(Modifier.weight(1f), "Net savings", formatINR(yearNet), "Year till now", if (yearNet >= 0) Green else Rust)
                    SimpleMetricCard(Modifier.weight(1f), "Free till now", formatINR(yearFree), "After goal savings", if (yearFree >= 0) Green else Rust)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Saved to goals in $yearKey till now: ${formatINR(goalsSavedThisYear)} · Avg net/month: ${formatINR(avgYearNet)}",
                    color = Muted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        item {
            AnalyticsSection(
                title = "What to do now",
                subtitle = "Simple next step"
            ) {
                Text(plainInsight, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun AnalyticsSection(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Surface(
        color = PaperCard,
        border = BorderStroke(1.dp, PaperLine),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SimpleMetricCard(
    modifier: Modifier,
    label: String,
    value: String,
    sub: String,
    accent: androidx.compose.ui.graphics.Color,
) {
    Surface(
        color = PaperCard,
        border = BorderStroke(1.dp, PaperLine),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label.uppercase(), color = Muted, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(value, color = accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(sub, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ComparisonRow(label: String, current: Double, previous: Double) {
    val diff = current - previous
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Text(
            "Now ${formatINR(current)}",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = Muted
        )
        Spacer(Modifier.width(10.dp))
        Text(
            if (diff >= 0.0) "+${formatINR(diff)}" else "-${formatINR(kotlin.math.abs(diff))}",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = if (label == "Kharcha") {
                if (diff > 0.0) Rust else Green
            } else {
                if (diff > 0.0) Green else Rust
            }
        )
    }
}

@Composable
private fun MonthLine(month: MonthSummary) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(monthLabel(month.monthKey), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("Income ${formatINR(month.income)}", color = Muted, fontSize = 11.5.sp)
        }
        Text(
            "Net ${formatINR(month.netSavings)}",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = if (month.netSavings >= 0) Green else Rust,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun IncomeSplitBar(
    income: Double,
    kharcha: Double,
    goalsSaved: Double,
    freeAmount: Double,
) {
    if (income <= 0.0) {
        Text("Add income to see this graph.", color = Muted, fontSize = 12.sp)
        return
    }

    // Actual money out, uncapped. Goals saved and kharcha both stay visible even when
    // together they exceed income — the old version capped spend at income and let goal
    // savings (and any overspend signal) silently collapse to zero.
    val spentPart = kharcha.coerceAtLeast(0.0)
    val goalsPart = goalsSaved.coerceAtLeast(0.0)
    val freePart = freeAmount.coerceAtLeast(0.0)
    val overspent = (spentPart + goalsPart) > income + 0.001

    // Scale the bar to whichever is larger: income, or what actually went out. When
    // overspent there's no green "free" slice — the bar is fully spend + goals — and
    // the note below calls out the shortfall instead of hiding it.
    val basis = max(income, spentPart + goalsPart)

    Surface(
        color = PaperLine,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
    ) {
        Row(Modifier.fillMaxSize()) {
            if (spentPart > 0.0) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .weight(spentPart.toFloat())
                        .background(Rust)
                )
            }
            if (goalsPart > 0.0) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .weight(goalsPart.toFloat())
                        .background(Gold)
                )
            }
            val remainder = (basis - spentPart - goalsPart).toFloat()
            if (remainder > 0.0f) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .weight(remainder)
                        .background(Green)
                )
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "Income ${formatINR(income)} = Kharcha ${formatINR(spentPart)} + Goals ${formatINR(goalsPart)}" +
            if (overspent) "" else " + Free ${formatINR(freePart)}",
        color = Muted,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace
    )
    if (overspent) {
        Spacer(Modifier.height(4.dp))
        Text(
            "You spent and saved ${formatINR(spentPart + goalsPart - income)} more than your income " +
                "this month — the extra came from savings or borrowing.",
            color = Rust,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun FreeTrendGraph(
    months: List<MonthSummary>,
    goalsSavedByMonth: Map<String, Double>,
) {
    val freeByMonth = months.associate { month ->
        month.monthKey to (month.netSavings - (goalsSavedByMonth[month.monthKey] ?: 0.0))
    }
    val maxAbs = max(1.0, freeByMonth.values.maxOfOrNull { abs(it) } ?: 1.0)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        months.forEachIndexed { index, month ->
            val free = freeByMonth[month.monthKey] ?: 0.0
            val barColor = if (free >= 0.0) Green else Rust
            val fraction = (abs(free) / maxAbs).toFloat().coerceIn(0f, 1f)
            // Bars trace in from empty, staggered top-to-bottom, so the graph draws
            // itself rather than appearing fully formed.
            val animatedFraction by animateFloatAsState(
                targetValue = fraction,
                animationSpec = valueReveal(delayMillis = staggerDelay(index)),
                label = "trendBar"
            )

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    monthLabel(month.monthKey).take(3),
                    color = Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.width(34.dp)
                )
                Surface(
                    color = PaperLine,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedFraction)
                            .background(barColor, RoundedCornerShape(50))
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    formatINR(free),
                    color = barColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.width(82.dp)
                )
            }
        }
    }
}
