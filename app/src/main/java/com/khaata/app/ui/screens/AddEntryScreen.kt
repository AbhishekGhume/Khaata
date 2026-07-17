package com.khaata.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.khaata.app.data.model.Template
import com.khaata.app.data.model.todayStr
import com.khaata.app.ui.components.CategoryDropdown
import com.khaata.app.ui.components.DatePickerField
import com.khaata.app.ui.components.DeleteExpenseDialog
import com.khaata.app.ui.components.EditExpenseDialog
import com.khaata.app.ui.components.ExpenseListRow
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
import com.khaata.app.util.CategoryMeta
import com.khaata.app.util.categoryMeta
import com.khaata.app.util.evaluateExpression
import com.khaata.app.util.formatINR
import com.khaata.app.util.isMoneyOrExprInputAllowed
import com.khaata.app.util.looksLikeExpression
import com.khaata.app.util.moneyToInput
import com.khaata.app.viewmodel.FinanceViewModel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddEntryScreen(viewModel: FinanceViewModel) {
    val monthSummary by viewModel.monthSummary.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val budgetProgress by viewModel.budgetProgress.collectAsState()
    val viewedMonthKey by viewModel.viewedMonthKey.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val people by viewModel.people.collectAsState()

    var incomeDraft by remember(monthSummary.income, viewedMonthKey) { mutableStateOf(moneyToInput(monthSummary.income)) }
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

    // Quick-add template state: which one is pending delete, and the "save current
    // form as a template" dialog.
    var templateToDelete by remember { mutableStateOf<Template?>(null) }
    var showSaveTemplate by remember { mutableStateOf(false) }
    var showSplit by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Surface(color = PaperCard, border = BorderStroke(1.dp, PaperLine), shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("This month's income", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = incomeDraft,
                            onValueChange = { if (isMoneyOrExprInputAllowed(it)) { incomeDraft = it; incomeError = null } },
                            placeholder = { Text("0") },
                            modifier = Modifier.width(150.dp),
                            singleLine = true,
                            isError = incomeError != null,
                            supportingText = when {
                                incomeError != null -> { { Text(incomeError!!, color = Rust, fontSize = 11.sp) } }
                                looksLikeExpression(incomeDraft) -> {
                                    {
                                        val preview = evaluateExpression(incomeDraft)
                                        Text(
                                            if (preview != null) "= ${formatINR(preview)}" else "Check the expression",
                                            color = if (preview != null) Green else Rust,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                else -> null
                            },
                            // Phone keypad + expression eval, same as the amount field. Since
                            // this prefills with the current income, a user who receives extra
                            // money just appends "+500" and hits Update — no mental math.
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )
                        Button(
                            onClick = {
                                val parsed = evaluateExpression(incomeDraft)
                                when {
                                    incomeDraft.isBlank() -> incomeError = "Enter your income for this month."
                                    parsed == null -> incomeError = "Enter a valid amount."
                                    else -> { incomeError = null; viewModel.updateIncome(parsed); incomeDraft = moneyToInput(parsed) }
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
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Got extra money? Add it right here — e.g. type \"${moneyToInput(monthSummary.income).ifBlank { "0" }}+500\" and Update.",
                        color = Muted, fontSize = 11.sp
                    )
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
        if (templates.isNotEmpty()) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    templates.forEach { template ->
                        TemplateChip(
                            template = template,
                            categories = categories,
                            onTap = {
                                category = template.category
                                amount = moneyToInput(template.amount)
                                note = template.note
                                amountError = null
                            },
                            onLongPress = { templateToDelete = template }
                        )
                    }
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DatePickerField(label = "Date", value = date, onValueChange = { date = it }, modifier = Modifier.width(150.dp), allowFuture = false)

                CategoryDropdown(
                    category = category,
                    categories = categories,
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                    onSelect = { category = it; categoryExpanded = false },
                    modifier = Modifier.width(190.dp)
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (isMoneyOrExprInputAllowed(it)) { amount = it; amountError = null } },
                    label = { Text("Amount") },
                    modifier = Modifier.width(140.dp), singleLine = true,
                    isError = amountError != null,
                    supportingText = when {
                        amountError != null -> { { Text(amountError!!, color = Rust, fontSize = 11.sp) } }
                        looksLikeExpression(amount) -> {
                            {
                                val preview = evaluateExpression(amount)
                                Text(
                                    if (preview != null) "= ${formatINR(preview)}" else "Check the expression",
                                    color = if (preview != null) Green else Rust,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        else -> null
                    },
                    // Phone keypad instead of Decimal: it exposes + − ( ) * / by
                    // default, so an amount like 340+55+12 can be typed without any
                    // extra in-app buttons. evaluateExpression resolves it on submit.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, label = { Text("Note") },
                    modifier = Modifier.widthIn(min = 150.dp), singleLine = true
                )
                Button(
                    onClick = {
                        val amt = evaluateExpression(amount)?.takeIf { it > 0.0 }
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
                TextButton(
                    onClick = {
                        val amt = evaluateExpression(amount)?.takeIf { it > 0.0 }
                        if (amt == null) amountError = "Enter an amount first to save a template."
                        else showSaveTemplate = true
                    }
                ) {
                    Icon(Icons.Filled.StarBorder, contentDescription = null, modifier = Modifier.size(16.dp), tint = Gold)
                    Spacer(Modifier.width(4.dp))
                    Text("Save as template", color = Ink, fontSize = 13.sp)
                }
                TextButton(onClick = { showSplit = true }) {
                    Icon(Icons.Filled.CallSplit, contentDescription = null, modifier = Modifier.size(16.dp), tint = Ink)
                    Spacer(Modifier.width(4.dp))
                    Text("Split a bill", color = Ink, fontSize = 13.sp)
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
                    ExpenseListRow(
                        e, categories,
                        onEdit = { expenseToEdit = e },
                        onDelete = { expenseToDelete = e },
                        onLogAgain = { viewModel.logAgainToday(e) }
                    )
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, color = PaperLine)
                }
            }
        }
    }

    expenseToEdit?.let { editing ->
        EditExpenseDialog(
            expense = editing,
            categories = categories,
            onDismiss = { expenseToEdit = null },
            onSave = { cat, amt, noteText, dateStr ->
                viewModel.updateExpense(editing, cat, amt, noteText, dateStr)
                expenseToEdit = null
            }
        )
    }

    expenseToDelete?.let { deleting ->
        DeleteExpenseDialog(
            expense = deleting,
            categories = categories,
            onDismiss = { expenseToDelete = null },
            onConfirm = {
                viewModel.deleteExpense(deleting)
                expenseToDelete = null
            }
        )
    }

    templateToDelete?.let { deleting ->
        AlertDialog(
            onDismissRequest = { templateToDelete = null },
            title = { Text("Remove this template?") },
            text = { Text("\"${deleting.label}\" will be removed from your quick-add chips. Your logged entries are untouched.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTemplate(deleting)
                    templateToDelete = null
                }) { Text("Remove", color = Rust) }
            },
            dismissButton = { TextButton(onClick = { templateToDelete = null }) { Text("Cancel") } }
        )
    }

    if (showSaveTemplate) {
        val defaultLabel = note.trim().ifBlank { categoryMeta(category, categories).label }
        SaveTemplateDialog(
            defaultLabel = defaultLabel,
            onDismiss = { showSaveTemplate = false },
            onSave = { label ->
                val amt = evaluateExpression(amount)?.takeIf { it > 0.0 }
                if (amt != null) viewModel.addTemplate(label, category, amt, note.trim())
                showSaveTemplate = false
            }
        )
    }

    if (showSplit) {
        SplitExpenseDialog(
            categories = categories,
            people = people,
            onDismiss = { showSplit = false },
            onSave = { splitDate, parts ->
                viewModel.saveSplit(splitDate, parts)
                showSplit = false
            }
        )
    }
}

/**
 * A quick-add template chip. Tap fills the entry form; long-press asks to remove it.
 * Rendered as a small pill with the category dot so it reads like the ledger rows.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TemplateChip(
    template: Template,
    categories: List<CategoryMeta>,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val meta = categoryMeta(template.category, categories)
    Surface(
        color = PaperCard,
        border = BorderStroke(1.dp, PaperLine),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.combinedClickable(onClick = onTap, onLongClick = onLongPress)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).background(meta.color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(template.label.ifBlank { meta.label }, fontSize = 13.sp, color = Ink)
            Spacer(Modifier.width(6.dp))
            Text(formatINR(template.amount), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Muted)
        }
    }
}

/** Names a template before saving. Prefilled with the note or category label. */
@Composable
private fun SaveTemplateDialog(
    defaultLabel: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var label by remember { mutableStateOf(defaultLabel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save as template") },
        text = {
            Column {
                Text("Give this quick-add a short name.", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank(),
                onClick = { onSave(label.trim()) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
