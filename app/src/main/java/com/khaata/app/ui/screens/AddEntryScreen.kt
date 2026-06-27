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
import com.khaata.app.data.model.Expense
import com.khaata.app.data.model.monthLabel
import com.khaata.app.data.model.todayStr
import com.khaata.app.ui.components.DatePickerField
import com.khaata.app.ui.theme.Green
import com.khaata.app.ui.theme.Ink
import com.khaata.app.ui.theme.Muted
import com.khaata.app.ui.theme.Paper
import com.khaata.app.ui.theme.PaperCard
import com.khaata.app.ui.theme.PaperLine
import com.khaata.app.ui.theme.Rust
import com.khaata.app.util.CATEGORIES
import com.khaata.app.util.categoryMeta
import com.khaata.app.util.formatINR
import com.khaata.app.viewmodel.FinanceViewModel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddEntryScreen(viewModel: FinanceViewModel) {
    val monthSummary by viewModel.monthSummary.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val viewedMonthKey by viewModel.viewedMonthKey.collectAsState()

    var incomeDraft by remember(monthSummary.income, viewedMonthKey) { mutableStateOf(if (monthSummary.income == 0.0) "" else monthSummary.income.toString()) }
    var date by remember { mutableStateOf(todayStr()) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf("food") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Surface(color = PaperCard, border = BorderStroke(1.dp, PaperLine), shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("This month's income", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = incomeDraft,
                            onValueChange = { incomeDraft = it },
                            placeholder = { Text("0") },
                            modifier = Modifier.width(140.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Button(
                            onClick = { viewModel.updateIncome(incomeDraft.toDoubleOrNull() ?: 0.0) },
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
                    value = amount, onValueChange = { amount = it }, label = { Text("Amount") },
                    modifier = Modifier.width(120.dp), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, label = { Text("Note") },
                    modifier = Modifier.widthIn(min = 150.dp), singleLine = true
                )
                Button(
                    onClick = {
                        val amt = amount.toDoubleOrNull()
                        if (amt != null && amt > 0 && date.isNotBlank()) {
                            viewModel.addExpense(category, amt, note.trim(), date)
                            amount = ""; note = ""
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
                "${monthLabel(viewedMonthKey)} \u00b7 ${expenses.size} entries \u00b7 ${formatINR(monthSummary.totalExpenses)} total",
                color = Muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
            )
        }
        if (expenses.isEmpty()) {
            item { Text("No entries yet for this month.", color = Muted, fontSize = 13.sp) }
        } else {
            items(expenses, key = { it.id }) { e ->
                Column {
                    ExpenseRow(e) { viewModel.deleteExpense(e) }
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, color = PaperLine)
                }
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: Expense, onDelete: () -> Unit) {
    val meta = categoryMeta(expense.category)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(expense.date.takeLast(5), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Muted, modifier = Modifier.width(50.dp))
        Box(Modifier.size(8.dp).background(meta.color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(meta.label, fontSize = 13.sp, modifier = Modifier.width(130.dp))
        Text(expense.note, fontSize = 12.sp, color = Muted, modifier = Modifier.weight(1f), maxLines = 1)
        Text(formatINR(expense.amount), fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.width(85.dp), textAlign = TextAlign.End)
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Rust, modifier = Modifier.size(18.dp))
        }
    }
}
