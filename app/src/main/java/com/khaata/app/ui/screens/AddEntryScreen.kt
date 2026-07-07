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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khaata.app.data.model.BudgetStatus
import com.khaata.app.data.model.Expense
import com.khaata.app.data.model.monthLabel
import com.khaata.app.data.model.todayStr
import com.khaata.app.ui.components.DatePickerField
import com.khaata.app.ui.theme.Green
import com.khaata.app.ui.theme.GreenSoft
import com.khaata.app.ui.theme.Ink
import com.khaata.app.ui.theme.Muted
import com.khaata.app.ui.theme.Paper
import com.khaata.app.ui.theme.PaperCard
import com.khaata.app.ui.theme.PaperLine
import com.khaata.app.ui.theme.Gold
import com.khaata.app.ui.theme.Rust
import com.khaata.app.ui.theme.RustSoft
import com.khaata.app.util.CATEGORIES
import com.khaata.app.util.categoryMeta
import com.khaata.app.util.formatINR
import com.khaata.app.util.isMoneyInputAllowed
import com.khaata.app.util.parsePositiveAmount
import com.khaata.app.viewmodel.FinanceViewModel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddEntryScreen(viewModel: FinanceViewModel) {
    val monthSummary by viewModel.monthSummary.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val budgetProgress by viewModel.budgetProgress.collectAsState()
    val viewedMonthKey by viewModel.viewedMonthKey.collectAsState()

    var incomeDraft by remember(monthSummary.income, viewedMonthKey) { mutableStateOf(if (monthSummary.income == 0.0) "" else monthSummary.income.toString()) }
    var incomeError by remember(viewedMonthKey) { mutableStateOf<String?>(null) }
    var date by remember { mutableStateOf(todayStr()) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf("food") }
    var amount by remember { mutableStateOf("") }
    var amountError by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    val selectedBudget = remember(category, budgetProgress) { budgetProgress.firstOrNull { it.category == category } }

    // Expense being edited (opens the edit dialog) and being deleted (confirm dialog).
    var expenseToEdit by remember { mutableStateOf<Expense?>(null) }
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Surface(color = PaperCard, border = BorderStroke(1.dp, PaperLine), shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("This month's income", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = incomeDraft,
                            onValueChange = { if (isMoneyInputAllowed(it)) { incomeDraft = it; incomeError = null } },
                            placeholder = { Text("0") },
                            modifier = Modifier.width(150.dp),
                            singleLine = true,
                            isError = incomeError != null,
                            supportingText = incomeError?.let { { Text(it, color = Rust, fontSize = 11.sp) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        Button(
                            onClick = {
                                val parsed = incomeDraft.trim().toDoubleOrNull()
                                when {
                                    incomeDraft.isBlank() -> incomeError = "Enter your income for this month."
                                    parsed == null || parsed < 0 -> incomeError = "Enter a valid amount."
                                    else -> { incomeError = null; viewModel.updateIncome(parsed) }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper)
                        ) { Text("Update") }
                        Text(
                            "Net so far: ${formatINR(monthSummary.netSavings)}",
                            color = if (monthSummary.netSavings >= 0) Green else Rust,
                            fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }
        }

        if (selectedBudget != null) {
            item {
                val (bg, fg, label) = when {
                    selectedBudget.projectedRunout || selectedBudget.status == BudgetStatus.OVER -> Triple(RustSoft, Rust, "This category budget will likely run out before month end")
                    selectedBudget.status == BudgetStatus.WATCHING -> Triple(Gold, Ink, "This category budget is getting tight")
                    else -> Triple(GreenSoft, Green, "This category budget is on track")
                }
                val paceText = if (selectedBudget.projectedRunout && selectedBudget.projectedRunoutDays != Int.MAX_VALUE) {
                    "At the current pace, this category looks like it may run out in about ${selectedBudget.projectedRunoutDays} day${if (selectedBudget.projectedRunoutDays == 1) "" else "s"}."
                } else {
                    "${selectedBudget.daysLeftInMonth} day${if (selectedBudget.daysLeftInMonth == 1) "" else "s"} left, about ${formatINR(selectedBudget.requiredDailySpend)} per day to stay within the remaining cap."
                }
                Surface(color = bg, shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("${formatINR(selectedBudget.spentAmount)} spent of ${formatINR(selectedBudget.limitAmount)}.", color = fg, fontSize = 12.sp)
                        Text(paceText, color = fg, fontSize = 12.sp)
                    }
                }
            }
        }

        item { Text("Log a kharcha entry", fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DatePickerField(label = "Date", value = date, onValueChange = { date = it }, modifier = Modifier.width(150.dp))

                ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = it }, modifier = Modifier.width(190.dp)) {
                    OutlinedTextField(
                        value = categoryMeta(category).label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                        CATEGORIES.forEach { c ->
                            DropdownMenuItem(text = { Text(c.label) }, onClick = { category = c.key; categoryExpanded = false })
                        }
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (isMoneyInputAllowed(it)) { amount = it; amountError = null } },
                    label = { Text("Amount") },
                    modifier = Modifier.width(140.dp), singleLine = true,
                    isError = amountError != null,
                    supportingText = amountError?.let { { Text(it, color = Rust, fontSize = 11.sp) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, label = { Text("Note") },
                    modifier = Modifier.widthIn(min = 150.dp), singleLine = true
                )
                Button(
                    onClick = {
                        val amt = parsePositiveAmount(amount)
                        when {
                            amt == null -> amountError = "Enter an amount greater than 0."
                            date.isBlank() -> amountError = "Pick a date."
                            else -> {
                                viewModel.addExpense(category, amt, note.trim(), date)
                                amount = ""; note = ""; amountError = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Green)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add entry")
                }
            }
        }

        item {
            Text(
                "${monthLabel(viewedMonthKey)} · ${expenses.size} entries · ${formatINR(monthSummary.totalExpenses)} total",
                color = Muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
            )
        }
        if (expenses.isEmpty()) {
            item { Text("No entries yet for this month.", color = Muted, fontSize = 13.sp) }
        } else {
            items(expenses, key = { it.id }) { e ->
                Column {
                    ExpenseRow(e, onEdit = { expenseToEdit = e }, onDelete = { expenseToDelete = e })
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, color = PaperLine)
                }
            }
        }
    }

    expenseToEdit?.let { editing ->
        EditExpenseDialog(
            expense = editing,
            onDismiss = { expenseToEdit = null },
            onSave = { cat, amt, noteText, dateStr ->
                viewModel.updateExpense(editing, cat, amt, noteText, dateStr)
                expenseToEdit = null
            }
        )
    }

    expenseToDelete?.let { deleting ->
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            title = { Text("Delete this entry?") },
            text = { Text("${categoryMeta(deleting.category).label} · ${formatINR(deleting.amount)}${if (deleting.note.isNotBlank()) " · ${deleting.note}" else ""}") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteExpense(deleting)
                    expenseToDelete = null
                }) { Text("Delete", color = Rust) }
            },
            dismissButton = { TextButton(onClick = { expenseToDelete = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun EditExpenseDialog(
    expense: Expense,
    onDismiss: () -> Unit,
    onSave: (category: String, amount: Double, note: String, date: String) -> Unit,
) {
    var category by remember { mutableStateOf(expense.category) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf(if (expense.amount == 0.0) "" else expense.amount.toString()) }
    var note by remember { mutableStateOf(expense.note) }
    var date by remember { mutableStateOf(expense.date) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DatePickerField(label = "Date", value = date, onValueChange = { date = it }, modifier = Modifier.fillMaxWidth())
                ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = it }) {
                    OutlinedTextField(
                        value = categoryMeta(category).label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                        CATEGORIES.forEach { c ->
                            DropdownMenuItem(text = { Text(c.label) }, onClick = { category = c.key; categoryExpanded = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (isMoneyInputAllowed(it)) { amount = it; error = null } },
                    label = { Text("Amount") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = Rust, fontSize = 11.sp) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, label = { Text("Note") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = parsePositiveAmount(amount)
                when {
                    amt == null -> error = "Enter an amount greater than 0."
                    date.isBlank() -> error = "Pick a date."
                    else -> onSave(category, amt, note.trim(), date)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ExpenseRow(expense: Expense, onEdit: () -> Unit, onDelete: () -> Unit) {
    val meta = categoryMeta(expense.category)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(expense.date.takeLast(5), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Muted, modifier = Modifier.width(50.dp))
        Box(Modifier.size(8.dp).background(meta.color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(meta.label, fontSize = 13.sp, modifier = Modifier.width(110.dp))
        Text(expense.note, fontSize = 12.sp, color = Muted, modifier = Modifier.weight(1f), maxLines = 1)
        Text(formatINR(expense.amount), fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.width(85.dp), textAlign = TextAlign.End)
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = Muted, modifier = Modifier.size(17.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Rust, modifier = Modifier.size(18.dp))
        }
    }
}
