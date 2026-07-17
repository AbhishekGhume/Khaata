package com.khaata.app.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khaata.app.data.model.Expense
import com.khaata.app.ui.components.DatePickerField
import com.khaata.app.ui.components.DeleteExpenseDialog
import com.khaata.app.ui.components.EditExpenseDialog
import com.khaata.app.ui.components.ExpenseListRow
import com.khaata.app.ui.theme.Muted
import com.khaata.app.ui.theme.PaperLine
import com.khaata.app.util.categoryMeta
import com.khaata.app.util.formatINR
import com.khaata.app.util.isMoneyInputAllowed
import com.khaata.app.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: FinanceViewModel) {
    val allExpenses by viewModel.allExpenses.collectAsState()
    val loading by viewModel.allExpensesLoading.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val goals by viewModel.goals.collectAsState()
    val people by viewModel.people.collectAsState()
    val scope = rememberCoroutineScope()

    // All-time expenses are a one-shot snapshot (not a live listener), so refresh
    // on open and again after any edit/delete so the list reflects the change.
    LaunchedEffect(Unit) { viewModel.refreshAllExpenses() }

    var query by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf<String?>(null) } // null = all
    var categoryExpanded by remember { mutableStateOf(false) }
    var minAmount by remember { mutableStateOf("") }
    var maxAmount by remember { mutableStateOf("") }
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }

    var expenseToEdit by remember { mutableStateOf<Expense?>(null) }
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }

    val filtered = remember(allExpenses, query, categoryFilter, minAmount, maxAmount, fromDate, toDate, categories) {
        val q = query.trim().lowercase()
        val min = minAmount.trim().toDoubleOrNull()
        val max = maxAmount.trim().toDoubleOrNull()
        val from = fromDate.trim().ifBlank { null }
        val to = toDate.trim().ifBlank { null }
        allExpenses.filter { e ->
            val label = categoryMeta(e.category, categories).label.lowercase()
            (q.isBlank() || e.note.lowercase().contains(q) || label.contains(q)) &&
                (categoryFilter == null || e.category == categoryFilter) &&
                (min == null || e.amount >= min) &&
                (max == null || e.amount <= max) &&
                (from == null || e.date >= from) &&
                (to == null || e.date <= to)
        }
    }
    val filteredTotal = remember(filtered) { filtered.sumOf { it.amount } }
    val anyFilterActive = query.isNotBlank() || categoryFilter != null ||
        minAmount.isNotBlank() || maxAmount.isNotBlank() || fromDate.isNotBlank() || toDate.isNotBlank()

    // Goals and people match on the text query only (the amount/date/category filters
    // are expense-specific). Shown as their own sections so search spans the whole app,
    // not just the expense ledger.
    val matchedGoals = remember(goals, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) emptyList() else goals.filter { it.name.lowercase().contains(q) }
    }
    val matchedPeople = remember(people, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) emptyList()
        else people.filter { it.name.lowercase().contains(q) || it.note.lowercase().contains(q) }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search expenses, goals & people") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = it }) {
                OutlinedTextField(
                    value = categoryFilter?.let { categoryMeta(it, categories).label } ?: "All categories",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All categories") },
                        onClick = { categoryFilter = null; categoryExpanded = false }
                    )
                    categories.forEach { c ->
                        DropdownMenuItem(
                            text = { Text(c.label) },
                            onClick = { categoryFilter = c.key; categoryExpanded = false }
                        )
                    }
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = minAmount,
                    onValueChange = { if (isMoneyInputAllowed(it)) minAmount = it },
                    label = { Text("Min ₹") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(110.dp)
                )
                OutlinedTextField(
                    value = maxAmount,
                    onValueChange = { if (isMoneyInputAllowed(it)) maxAmount = it },
                    label = { Text("Max ₹") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(110.dp)
                )
                DatePickerField(label = "From", value = fromDate, onValueChange = { fromDate = it }, modifier = Modifier.width(150.dp))
                DatePickerField(label = "To", value = toDate, onValueChange = { toDate = it }, modifier = Modifier.width(150.dp))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${filtered.size} result${if (filtered.size == 1) "" else "s"} · ${formatINR(filteredTotal)}",
                    color = Muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                if (anyFilterActive) {
                    TextButton(onClick = {
                        query = ""; categoryFilter = null; minAmount = ""; maxAmount = ""; fromDate = ""; toDate = ""
                    }) { Text("Clear") }
                }
            }
        }

        if (matchedGoals.isNotEmpty()) {
            item { Text("Goals", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Muted) }
            items(matchedGoals, key = { "goal_${it.id}" }) { g ->
                Column {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(g.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(
                            "${formatINR(g.savedAmount)} / ${formatINR(g.targetAmount)}",
                            fontSize = 12.sp, color = Muted
                        )
                    }
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, color = PaperLine)
                }
            }
        }

        if (matchedPeople.isNotEmpty()) {
            item { Text("People", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Muted) }
            items(matchedPeople, key = { "person_${it.id}" }) { p ->
                val status = when {
                    kotlin.math.abs(p.balance) < 0.001 -> "Settled"
                    p.balance > 0.0 -> "Owes you ${formatINR(p.balance)}"
                    else -> "You owe ${formatINR(-p.balance)}"
                }
                Column {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            if (p.note.isNotBlank()) Text(p.note, fontSize = 11.sp, color = Muted)
                        }
                        Text(status, fontSize = 12.sp, color = Muted)
                    }
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, color = PaperLine)
                }
            }
        }

        if (query.isNotBlank() && (matchedGoals.isNotEmpty() || matchedPeople.isNotEmpty())) {
            item { Text("Expenses", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Muted) }
        }

        if (loading && allExpenses.isEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Loading your expenses…", color = Muted, fontSize = 13.sp)
                }
            }
        } else if (filtered.isEmpty()) {
            item { Text(if (anyFilterActive) "No entries match these filters." else "No expenses yet.", color = Muted, fontSize = 13.sp) }
        } else {
            items(filtered, key = { it.id }) { e ->
                Column {
                    ExpenseListRow(
                        e, categories,
                        onEdit = { expenseToEdit = e },
                        onDelete = { expenseToDelete = e },
                        showFullDate = true,
                        onLogAgain = {
                            val job = viewModel.logAgainToday(e)
                            scope.launch { job.join(); viewModel.refreshAllExpenses() }
                        }
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
                val job = viewModel.updateExpense(editing, cat, amt, noteText, dateStr)
                expenseToEdit = null
                scope.launch { job.join(); viewModel.refreshAllExpenses() }
            }
        )
    }

    expenseToDelete?.let { deleting ->
        DeleteExpenseDialog(
            expense = deleting,
            categories = categories,
            onDismiss = { expenseToDelete = null },
            onConfirm = {
                val job = viewModel.deleteExpense(deleting)
                expenseToDelete = null
                scope.launch { job.join(); viewModel.refreshAllExpenses() }
            }
        )
    }
}
