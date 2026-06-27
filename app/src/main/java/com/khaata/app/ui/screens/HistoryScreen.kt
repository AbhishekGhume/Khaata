package com.khaata.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khaata.app.data.model.MonthSummary
import com.khaata.app.data.model.monthLabel
import com.khaata.app.ui.theme.Green
import com.khaata.app.ui.theme.Muted
import com.khaata.app.ui.theme.PaperLine
import com.khaata.app.ui.theme.Rust
import com.khaata.app.util.formatINR
import com.khaata.app.viewmodel.FinanceViewModel

@Composable
fun HistoryScreen(viewModel: FinanceViewModel, onJump: (String) -> Unit) {
    val months by viewModel.allMonths.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item { Text("Month by month", fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
        item { Spacer(Modifier.height(12.dp)) }
        if (months.isEmpty()) {
            item { Text("No history yet.", color = Muted, fontSize = 13.sp) }
        } else {
            item { HistoryHeader() }
            item { Divider(color = PaperLine) }
        }
        items(months, key = { it.monthKey }) { m ->
            HistoryRow(m, onClick = { onJump(m.monthKey) })
            Divider(color = PaperLine)
        }
    }
}

@Composable
private fun HistoryHeader() {
    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("MONTH", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Muted, modifier = Modifier.weight(1f))
        Text("INCOME", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Muted, modifier = Modifier.width(95.dp), textAlign = TextAlign.End)
        Text("KHARCHA", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Muted, modifier = Modifier.width(95.dp), textAlign = TextAlign.End)
        Text("NET", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Muted, modifier = Modifier.width(100.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun HistoryRow(month: MonthSummary, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(monthLabel(month.monthKey), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(formatINR(month.income), fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.width(95.dp), textAlign = TextAlign.End)
        Text(formatINR(month.totalExpenses), fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Rust, modifier = Modifier.width(95.dp), textAlign = TextAlign.End)
        Text(
            formatINR(month.netSavings), fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = if (month.netSavings >= 0) Green else Rust, modifier = Modifier.width(100.dp), textAlign = TextAlign.End
        )
    }
}
