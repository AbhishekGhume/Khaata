package com.khaata.app.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khaata.app.data.model.BudgetProgress
import com.khaata.app.data.model.BudgetStatus
import com.khaata.app.data.model.currentMonthKey
import com.khaata.app.data.model.monthLabel
import com.khaata.app.ui.theme.Gold
import com.khaata.app.ui.theme.Green
import com.khaata.app.ui.theme.GreenSoft
import com.khaata.app.ui.theme.Ink
import com.khaata.app.ui.theme.Muted
import com.khaata.app.ui.theme.Overdue
import com.khaata.app.ui.theme.OverdueSoft
import com.khaata.app.ui.theme.PaperCard
import com.khaata.app.ui.theme.PaperLine
import com.khaata.app.ui.theme.Rust
import com.khaata.app.ui.theme.RustSoft
import com.khaata.app.util.CATEGORIES
import com.khaata.app.util.categoryMeta
import com.khaata.app.util.formatINR
import com.khaata.app.util.isMoneyInputAllowed
import com.khaata.app.viewmodel.FinanceViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetsScreen(viewModel: FinanceViewModel) {
    val viewedMonthKey by viewModel.viewedMonthKey.collectAsState()
    val budgetProgress by viewModel.budgetProgress.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    val isCurrentMonth = viewedMonthKey == currentMonthKey()

    var category by remember { mutableStateOf(CATEGORIES.first().key) }
    var limitDraft by remember { mutableStateOf("") }
    var budgetError by remember(viewedMonthKey) { mutableStateOf<String?>(null) }
    // (category, limitAmount) of the budget pending delete confirmation.
    var budgetToDelete by remember { mutableStateOf<Pair<String, Double>?>(null) }
    val budgetWarning = remember(category, limitDraft) {
        val amt = limitDraft.toDoubleOrNull()
        if (amt != null && amt > 0) viewModel.budgetAllocationWarning(category, amt) else null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Budgets", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    "Set a monthly cap per category and watch it update as you log expenses.",
                    color = Muted,
                    fontSize = 13.sp
                )
            }
        }

        item {
            Surface(color = PaperCard, border = BorderStroke(1.dp, PaperLine), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Create / update budget for ${monthLabel(viewedMonthKey)}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    if (!isCurrentMonth) {
                        Text(
                            "Past and future months are read-only. Switch to the current month to create or edit caps.",
                            color = Muted,
                            fontSize = 12.sp
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CATEGORIES.forEach { meta ->
                            Button(
                                enabled = isCurrentMonth,
                                onClick = { category = meta.key },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (category == meta.key) meta.color else PaperLine,
                                    contentColor = if (category == meta.key) Ink else Ink
                                )
                            ) { Text(meta.label, fontSize = 12.sp) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = limitDraft,
                            onValueChange = { if (isMoneyInputAllowed(it)) { limitDraft = it; budgetError = null } },
                            label = { Text("Monthly cap (₹)") },
                            singleLine = true,
                            isError = budgetError != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.widthIn(min = 160.dp)
                        )
                        Button(
                            enabled = isCurrentMonth,
                            onClick = {
                                val amount = limitDraft.toDoubleOrNull()
                                if (amount != null && amount > 0) {
                                    val validationError = viewModel.validateBudgetLimit(category, amount)
                                    if (validationError == null) {
                                        // Saved regardless of the allocation warning above — running
                                        // tight against income is normal and shouldn't block the save.
                                        viewModel.setBudget(category, amount)
                                        budgetError = null
                                        limitDraft = ""
                                    } else {
                                        budgetError = validationError
                                    }
                                } else {
                                    budgetError = "Enter a valid budget amount."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = com.khaata.app.ui.theme.Paper)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save")
                        }
                    }
                    if (budgetError != null) {
                        Text(
                            budgetError!!,
                            color = Rust,
                            fontSize = 12.sp
                        )
                    } else if (budgetWarning != null) {
                        Text(
                            budgetWarning,
                            color = Gold,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        "Budgets here are monthly and tied to the currently viewed month.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        item {
            Text("Live progress", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }

        if (budgetProgress.isEmpty()) {
            item {
                Text("No budgets yet for this month. Add one above.", color = Muted, fontSize = 13.sp)
            }
        } else {
            items(budgetProgress, key = { it.category }) { progress ->
                BudgetProgressCard(
                    progress = progress,
                    canDelete = isCurrentMonth,
                    onDelete = { budgetToDelete = progress.category to progress.limitAmount }
                )
            }
        }

        if (budgets.isNotEmpty()) {
            item { Text("Saved budgets", fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
            items(budgets, key = { it.id }) { budget ->
                val meta = categoryMeta(budget.category)
                Surface(color = PaperCard, border = BorderStroke(1.dp, PaperLine), shape = RoundedCornerShape(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BoxWithColor(meta.color)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(meta.label, fontWeight = FontWeight.SemiBold)
                            Text("Cap ${formatINR(budget.limitAmount)}", color = Muted, fontSize = 12.sp)
                        }
                        TextButtonLikeDelete(
                            enabled = isCurrentMonth,
                            onClick = { budgetToDelete = budget.category to budget.limitAmount }
                        )
                    }
                }
            }
        }
    }

    budgetToDelete?.let { (cat, limit) ->
        AlertDialog(
            onDismissRequest = { budgetToDelete = null },
            title = { Text("Remove this budget?") },
            text = { Text("${categoryMeta(cat).label} cap of ${formatINR(limit)} will be removed for ${monthLabel(viewedMonthKey)}.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBudget(cat, limit)
                    budgetToDelete = null
                }) { Text("Remove", color = Rust) }
            },
            dismissButton = { TextButton(onClick = { budgetToDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun BudgetProgressCard(progress: BudgetProgress, canDelete: Boolean, onDelete: () -> Unit) {
    val meta = categoryMeta(progress.category)
    val (bg, fg, label) = when (progress.status) {
        BudgetStatus.ON_TRACK -> Triple(GreenSoft, Green, "On track")
        BudgetStatus.WATCHING -> Triple(Gold, Ink, "Watch closely")
        BudgetStatus.OVER -> Triple(RustSoft, Overdue, "Over budget")
    }

    Surface(color = PaperCard, border = BorderStroke(1.dp, PaperLine), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BoxWithColor(meta.color)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(meta.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = onDelete, enabled = canDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete budget", tint = Rust)
                }
            }

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Spent ${formatINR(progress.spentAmount)}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Text("Left ${formatINR(progress.remainingAmount)}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Text("Cap ${formatINR(progress.limitAmount)}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }

            BudgetBar(progress.pct, bg)

            if (progress.status == BudgetStatus.OVER) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Overdue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("You are over this category budget.", color = Overdue, fontSize = 12.sp)
                }
            } else if (progress.status == BudgetStatus.WATCHING) {
                Text("You have crossed 80% of this cap.", color = Ink, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun BudgetBar(pct: Float, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .background(PaperLine, RoundedCornerShape(50))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction = (pct / 100f).coerceIn(0f, 1f))
                .height(10.dp)
                .background(color, RoundedCornerShape(50))
        )
    }
}

@Composable
private fun BoxWithColor(color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .size(10.dp)
            .background(color, RoundedCornerShape(50))
    )
}

@Composable
private fun TextButtonLikeDelete(enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Icon(Icons.Filled.Delete, contentDescription = null, tint = Rust, modifier = Modifier.size(16.dp))
    }
}