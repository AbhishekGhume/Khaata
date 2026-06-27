package com.khaata.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khaata.app.data.model.AnalyticsSnapshot
import com.khaata.app.data.model.Expense
import com.khaata.app.data.model.Goal
import com.khaata.app.data.model.GoalStats
import com.khaata.app.data.model.GoalStatus
import com.khaata.app.data.model.MonthlyAnalyticsPoint
import com.khaata.app.data.model.computeStats
import com.khaata.app.data.model.currentMonthKey
import com.khaata.app.data.model.forecast
import com.khaata.app.data.model.monthLabel
import com.khaata.app.ui.components.ProgressStamp
import com.khaata.app.ui.components.StatusBadge
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalyticsScreen(viewModel: FinanceViewModel) {
    val analytics by viewModel.analytics.collectAsState()
    val goals by viewModel.goals.collectAsState()

    val trendMonths = remember(analytics.months) { analytics.months.takeLast(6) }
    val bestMonth = analytics.bestMonthKey?.let { key -> analytics.months.find { it.monthKey == key } }
    val worstMonth = analytics.worstMonthKey?.let { key -> analytics.months.find { it.monthKey == key } }
    val goalStats = remember(goals) { goals.map { it to it.computeStats(currentMonthKey()) } }
    val goalForecasts = remember(goals) { goals.map { it.forecast() } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Analytics", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    "A month-over-month view of income, spending, savings rate, and goal pace.",
                    color = Muted,
                    fontSize = 13.sp
                )
            }
        }

        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    label = "Average savings rate",
                    value = if (analytics.averageSavingsRate > 0.0) "${analytics.averageSavingsRate.toInt()}%" else "No income yet",
                    accent = if (analytics.averageSavingsRate >= 30.0) Green else Gold,
                    sub = "Net savings as a share of income"
                )
                MetricCard(
                    label = "Best month",
                    value = bestMonth?.let { monthLabel(it.monthKey) } ?: "Not enough data",
                    accent = Green,
                    sub = bestMonth?.let { formatINR(it.netSavings) } ?: "Track more months"
                )
                MetricCard(
                    label = "Worst month",
                    value = worstMonth?.let { monthLabel(it.monthKey) } ?: "Not enough data",
                    accent = Rust,
                    sub = worstMonth?.let { formatINR(it.netSavings) } ?: "Track more months"
                )
                MetricCard(
                    label = "Latest net savings",
                    value = trendMonths.lastOrNull()?.let { formatINR(it.netSavings) } ?: formatINR(0.0),
                    accent = if ((trendMonths.lastOrNull()?.netSavings ?: 0.0) >= 0.0) Green else Rust,
                    sub = trendMonths.lastOrNull()?.monthKey?.let { monthLabel(it) } ?: "No months yet"
                )
            }
        }

        item {
            AnalyticsCard(title = "Trend line", subtitle = "Income vs kharcha vs net savings across the latest months") {
                if (trendMonths.isEmpty()) {
                    EmptyState("No history yet. Log a few months of income and expenses first.")
                } else {
                    TrendChart(points = trendMonths)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        SeriesLegend(color = Green, label = "Income")
                        SeriesLegend(color = Rust, label = "Kharcha")
                        SeriesLegend(color = Gold, label = "Net savings")
                    }
                }
            }
        }

        item {
            AnalyticsCard(title = "Savings rate by month", subtitle = "Percent of income kept each month") {
                if (trendMonths.isEmpty()) {
                    EmptyState("No monthly savings data yet.")
                } else {
                    trendMonths.forEach { month ->
                        SavingsRateRow(month.monthKey, month.savingsRate)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        item {
            AnalyticsCard(title = "Category trend", subtitle = "Latest month vs previous month") {
                if (analytics.categoryTrends.isEmpty()) {
                    EmptyState("Add at least two months of expenses to compare categories.")
                } else {
                    analytics.categoryTrends.forEachIndexed { index, trend ->
                        val meta = categoryMeta(trend.categoryKey)
                        CategoryTrendRow(
                            label = meta.label,
                            color = meta.color,
                            current = trend.currentAmount,
                            previous = trend.previousAmount,
                            delta = trend.delta,
                            deltaPct = trend.deltaPct
                        )
                        if (index != analytics.categoryTrends.lastIndex) Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }

        item {
            AnalyticsCard(title = "Biggest movers", subtitle = "Top expenses logged in the latest month") {
                if (analytics.biggestExpenses.isEmpty()) {
                    EmptyState("No expenses logged in the latest month yet.")
                } else {
                    analytics.biggestExpenses.forEachIndexed { index, expense ->
                        BigExpenseRow(expense)
                        if (index != analytics.biggestExpenses.lastIndex) Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }

        item {
            AnalyticsCard(title = "Goal velocity", subtitle = "Forecasted hit date based on recent monthly pace") {
                if (goals.isEmpty()) {
                    EmptyState("Add a goal first to see a velocity forecast.")
                } else {
                    goalForecasts.forEachIndexed { index, forecast ->
                        val goal = goals.first { it.id == forecast.goalId }
                        val stats = goalStats.first { it.first.id == goal.id }.second
                        GoalVelocityRow(goal = goal, stats = stats, forecastHitDate = forecast.estimatedHitDate, avgMonthly = forecast.averageMonthlyContribution)
                        if (index != goalForecasts.lastIndex) Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Surface(
        color = PaperCard,
        border = BorderStroke(1.dp, PaperLine),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, accent: Color, sub: String) {
    Surface(
        color = PaperCard,
        border = BorderStroke(1.dp, PaperLine),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.widthIn(min = 150.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label.uppercase(), color = Muted, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(value, color = accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(sub, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SeriesLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun TrendChart(points: List<MonthlyAnalyticsPoint>) {
    val chartHeight = 220.dp
    Column(Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .padding(top = 10.dp, bottom = 26.dp)
        ) {
            val values = points.flatMap { listOf(it.income, it.expenses, it.netSavings) }
            val minValue = min(0.0, values.minOrNull() ?: 0.0)
            val maxValue = max(1.0, values.maxOrNull() ?: 1.0)
            val range = max(1.0, maxValue - minValue)
            val stepX = if (points.size <= 1) 0f else size.width / (points.size - 1)
            val plotHeight = size.height - 12f

            fun yFor(value: Double): Float {
                val normalized = ((value - minValue) / range).coerceIn(0.0, 1.0)
                return (plotHeight - (normalized * plotHeight)).toFloat()
            }

            fun drawSeries(color: Color, selector: (MonthlyAnalyticsPoint) -> Double) {
                val path = Path()
                points.forEachIndexed { index, point ->
                    val x = index * stepX
                    val y = yFor(selector(point))
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path = path, color = color, style = Stroke(width = 4f))
                points.forEachIndexed { index, point ->
                    val x = index * stepX
                    val y = yFor(selector(point))
                    drawCircle(color = color, radius = 4.5f, center = Offset(x, y))
                }
            }

            drawSeries(Green) { it.income }
            drawSeries(Rust) { it.expenses }
            drawSeries(Gold) { it.netSavings }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            points.forEach { month ->
                Text(
                    monthLabel(month.monthKey),
                    fontSize = 10.5.sp,
                    color = Muted,
                    modifier = Modifier.widthIn(min = 48.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SavingsRateRow(monthKey: String, savingsRate: Double) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(monthLabel(monthKey), fontSize = 12.5.sp, modifier = Modifier.weight(1f))
            Text("${savingsRate.toInt()}%", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = if (savingsRate >= 0) Green else Rust)
        }
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(PaperLine, RoundedCornerShape(50))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction = (abs(savingsRate) / 100.0).toFloat().coerceIn(0f, 1f))
                    .height(8.dp)
                    .background(if (savingsRate >= 0) Green else Rust, RoundedCornerShape(50))
            )
        }
    }
}

@Composable
private fun CategoryTrendRow(
    label: String,
    color: Color,
    current: Double,
    previous: Double,
    delta: Double,
    deltaPct: Double
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size(10.dp).background(color, RoundedCornerShape(50)))
            Spacer(Modifier.width(8.dp))
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(formatINR(current), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Last month ${formatINR(previous)}", color = Muted, fontSize = 11.5.sp, modifier = Modifier.weight(1f))
            Text(
                if (delta >= 0) "+${formatINR(delta)}" else "-${formatINR(abs(delta))}",
                color = if (delta >= 0) Rust else Green,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "(${if (deltaPct >= 0) "+" else ""}${deltaPct.toInt()}%)",
                color = if (delta >= 0) Rust else Green,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun BigExpenseRow(expense: Expense) {
    val meta = categoryMeta(expense.category)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(10.dp).background(meta.color, RoundedCornerShape(50)))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(meta.label, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
            Text(
                if (expense.note.isBlank()) expense.date else "${expense.note} · ${expense.date}",
                color = Muted,
                fontSize = 11.5.sp,
                maxLines = 1
            )
        }
        Text(formatINR(expense.amount), fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GoalVelocityRow(goal: Goal, stats: GoalStats, forecastHitDate: String?, avgMonthly: Double) {
    Surface(color = PaperCard, border = BorderStroke(1.dp, PaperLine), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressStamp(
                pct = stats.pct,
                color = if (stats.achieved) Green else if (stats.status == GoalStatus.BEHIND) Rust else Gold,
                size = 56.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(goal.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    StatusBadge(stats.status)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    if (forecastHitDate != null) {
                        "At your current pace, you should hit this goal around $forecastHitDate."
                    } else {
                        "Log a few contributions first so I can forecast a hit date."
                    },
                    color = Muted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Saved ${formatINR(goal.savedAmount)}", fontSize = 12.sp)
                    Text("Avg ${formatINR(avgMonthly)}/mo", fontSize = 12.sp)
                    Text("Remaining ${formatINR((goal.targetAmount - goal.savedAmount).coerceAtLeast(0.0))}", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Text(message, color = Muted, fontSize = 12.5.sp)
}