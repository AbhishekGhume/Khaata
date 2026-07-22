package com.khaata.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khaata.app.data.model.Goal
import com.khaata.app.data.model.MonthSummary
import com.khaata.app.data.model.monthLabel
import com.khaata.app.ui.components.animatedListItem
import com.khaata.app.ui.theme.GoldSoft
import com.khaata.app.ui.theme.Gold
import com.khaata.app.ui.theme.Green
import com.khaata.app.ui.theme.GreenSoft
import com.khaata.app.ui.theme.Ink
import com.khaata.app.ui.theme.Muted
import com.khaata.app.ui.theme.PaperCard
import com.khaata.app.ui.theme.PaperLine
import com.khaata.app.ui.theme.Rust
import com.khaata.app.util.formatINR
import com.khaata.app.viewmodel.FinanceViewModel

@Composable
fun HistoryScreen(viewModel: FinanceViewModel, onJump: (String) -> Unit) {
    val months by viewModel.allMonths.collectAsState()
    val goals by viewModel.goals.collectAsState()

    // Pre-compute per-month goal contributions so we don't recalculate in
    // every row. This uses the monthlyContributions map already on each
    // goal doc — no extra Firestore query.
    val goalsByMonth = remember(goals) {
        val result = mutableMapOf<String, Double>()
        goals.forEach { goal ->
            goal.monthlyContributions.forEach { (monthKey, amount) ->
                result[monthKey] = (result[monthKey] ?: 0.0) + amount
            }
        }
        result
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item { Text("Month by month", fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
        item { Spacer(Modifier.height(8.dp)) }

        // Legend explaining the columns — specifically the new "Saved to goals"
        // column so users understand why NET ≠ freely spendable.
        item {
            Surface(
                color = GoldSoft,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ) {
                Text(
                    "\"NET\" = Income − Kharcha. \"Saved\" = amount logged toward goals that month. " +
                            "\"Free\" = what's left unallocated.",
                    fontSize = 11.sp,
                    color = Ink,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    lineHeight = 15.sp
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }

        if (months.isEmpty()) {
            item { Text("No history yet.", color = Muted, fontSize = 13.sp) }
        } else {
            item { HistoryHeader(showGoalsColumn = goalsByMonth.isNotEmpty()) }
            item { HorizontalDivider(color = PaperLine) }
        }

        items(months, key = { it.monthKey }) { m ->
            val savedToGoals = goalsByMonth[m.monthKey] ?: 0.0
            Column(animatedListItem()) {
                HistoryRow(
                    month = m,
                    savedToGoals = savedToGoals,
                    showGoalsColumn = goalsByMonth.isNotEmpty(),
                    onClick = { onJump(m.monthKey) }
                )
                HorizontalDivider(color = PaperLine)
            }
        }

        if (goalsByMonth.isNotEmpty()) {
            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tip: tap any month to open its full ledger on the Dashboard.",
                    color = Muted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun HistoryHeader(showGoalsColumn: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            "MONTH",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = Muted,
            modifier = Modifier.weight(1f)
        )
        Text(
            "KHARCHA",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = Muted,
            modifier = Modifier.width(72.dp),
            textAlign = TextAlign.End
        )
        if (showGoalsColumn) {
            Text(
                "SAVED",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Gold,
                modifier = Modifier.width(72.dp),
                textAlign = TextAlign.End
            )
        }
        Text(
            "FREE",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = Green,
            modifier = Modifier.width(72.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun HistoryRow(
    month: MonthSummary,
    savedToGoals: Double,
    showGoalsColumn: Boolean,
    onClick: () -> Unit,
) {
    val freeAvailable = month.netSavings - savedToGoals
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Month name + income
            Column(Modifier.weight(1f)) {
                Text(monthLabel(month.monthKey), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    "Income ${formatINR(month.income)}",
                    fontSize = 11.sp,
                    color = Muted,
                    fontFamily = FontFamily.Monospace
                )
            }
            // Kharcha
            Text(
                formatINR(month.totalExpenses),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = Rust,
                modifier = Modifier.width(72.dp),
                textAlign = TextAlign.End
            )
            // Goals saved column
            if (showGoalsColumn) {
                Text(
                    if (savedToGoals > 0) formatINR(savedToGoals) else "—",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = if (savedToGoals > 0) Gold else Muted,
                    modifier = Modifier.width(72.dp),
                    textAlign = TextAlign.End
                )
            }
            // Free available (the number that actually answers "what's mine to spend?")
            Text(
                formatINR(freeAvailable),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (freeAvailable >= 0) Green else Rust,
                modifier = Modifier.width(72.dp),
                textAlign = TextAlign.End
            )
        }
    }
}