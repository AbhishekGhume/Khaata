package com.khaata.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khaata.app.data.model.Expense
import com.khaata.app.ui.theme.Green
import com.khaata.app.ui.theme.Muted
import com.khaata.app.ui.theme.Rust
import com.khaata.app.util.CategoryMeta
import com.khaata.app.util.categoryMeta
import com.khaata.app.util.evaluateExpression
import com.khaata.app.util.formatINR
import com.khaata.app.util.isMoneyOrExprInputAllowed
import com.khaata.app.util.looksLikeExpression

/** A category picker driven by the live category list. Shared by every entry form. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDropdown(
    category: String,
    categories: List<CategoryMeta>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = onExpandedChange, modifier = modifier) {
        OutlinedTextField(
            value = categoryMeta(category, categories).label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            categories.forEach { c ->
                DropdownMenuItem(text = { Text(c.label) }, onClick = { onSelect(c.key) })
            }
        }
    }
}

/** Edit dialog for one expense — category, amount, note, date. Shared by AddEntry & Search. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseDialog(
    expense: Expense,
    categories: List<CategoryMeta>,
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
                DatePickerField(label = "Date", value = date, onValueChange = { date = it }, modifier = Modifier.fillMaxWidth(), allowFuture = false)
                CategoryDropdown(
                    category = category,
                    categories = categories,
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                    onSelect = { category = it; categoryExpanded = false },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (isMoneyOrExprInputAllowed(it)) { amount = it; error = null } },
                    label = { Text("Amount") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = when {
                        error != null -> { { Text(error!!, color = Rust, fontSize = 11.sp) } }
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
                    // Phone keypad exposes + − ( ) * / so expressions (340+55) are typable.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
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
                val amt = evaluateExpression(amount)?.takeIf { it > 0.0 }
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

/** Delete-confirmation dialog for one expense. Shared by AddEntry & Search. */
@Composable
fun DeleteExpenseDialog(
    expense: Expense,
    categories: List<CategoryMeta>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this entry?") },
        text = {
            Text(
                "${categoryMeta(expense.category, categories).label} · ${formatINR(expense.amount)}" +
                    if (expense.note.isNotBlank()) " · ${expense.note}" else ""
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = Rust) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** A ledger-style expense row with edit + delete affordances. Shared by AddEntry & Search. */
@Composable
fun ExpenseListRow(
    expense: Expense,
    categories: List<CategoryMeta>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    showFullDate: Boolean = false,
) {
    val meta = categoryMeta(expense.category, categories)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (showFullDate) expense.date else expense.date.takeLast(5),
            fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Muted,
            modifier = Modifier.width(if (showFullDate) 82.dp else 50.dp)
        )
        Box(Modifier.size(8.dp).background(meta.color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(meta.label, fontSize = 13.sp, modifier = Modifier.width(110.dp))
        Text(expense.note, fontSize = 12.sp, color = Muted, modifier = Modifier.width(0.dp).weight(1f), maxLines = 1)
        Text(formatINR(expense.amount), fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.width(80.dp), textAlign = TextAlign.End)
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = Muted, modifier = Modifier.size(17.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Rust, modifier = Modifier.size(18.dp))
        }
    }
}
