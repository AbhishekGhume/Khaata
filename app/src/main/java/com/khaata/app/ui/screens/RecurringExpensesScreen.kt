package com.khaata.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.khaata.app.data.model.RecurringExpense
import com.khaata.app.ui.components.CategoryDropdown
import com.khaata.app.ui.theme.Green
import com.khaata.app.ui.theme.Ink
import com.khaata.app.ui.theme.Muted
import com.khaata.app.ui.theme.Paper
import com.khaata.app.ui.theme.PaperCard
import com.khaata.app.ui.theme.PaperLine
import com.khaata.app.ui.theme.Rust
import com.khaata.app.util.categoryMeta
import com.khaata.app.util.formatINR
import com.khaata.app.util.iconForKey
import com.khaata.app.util.isMoneyInputAllowed
import com.khaata.app.util.parsePositiveAmount
import com.khaata.app.viewmodel.FinanceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringExpensesScreen(viewModel: FinanceViewModel, onBack: () -> Unit) {
    val recurring by viewModel.recurring.collectAsState()
    val categories by viewModel.categories.collectAsState()

    var editing by remember { mutableStateOf<RecurringExpense?>(null) }
    var creatingNew by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<RecurringExpense?>(null) }

    Column(Modifier.fillMaxSize().background(Paper)) {
        TopAppBar(
            title = { Text("Recurring expenses") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Paper)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink, titleContentColor = Paper)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Set up rent, subscriptions, or any fixed monthly cost. Each active template posts " +
                        "itself as an expense once a month on its chosen day — no need to log it by hand.",
                    color = Muted, fontSize = 13.sp
                )
            }
            item {
                Button(
                    onClick = { creatingNew = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New recurring expense")
                }
            }

            if (recurring.isEmpty()) {
                item { Text("No recurring expenses yet.", color = Muted, fontSize = 13.sp) }
            }

            items(recurring, key = { it.id }) { r ->
                val meta = categoryMeta(r.category, categories)
                Surface(color = PaperCard, border = BorderStroke(1.dp, PaperLine), shape = RoundedCornerShape(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(34.dp).background(meta.color, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(iconForKey(meta.iconKey), contentDescription = null, tint = Paper, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(meta.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    formatINR(r.amount),
                                    fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                                    color = if (r.active) Ink else Muted
                                )
                            }
                            Text(
                                "Day ${r.dayOfMonth} each month" + if (r.note.isNotBlank()) " · ${r.note}" else "",
                                color = Muted, fontSize = 12.sp, maxLines = 1
                            )
                        }
                        Switch(
                            checked = r.active,
                            onCheckedChange = { viewModel.setRecurringActive(r.id, it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = Green)
                        )
                        IconButton(onClick = { editing = r }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = Muted, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { toDelete = r }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Rust, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (creatingNew) {
        RecurringEditDialog(
            existing = null,
            categories = categories,
            onDismiss = { creatingNew = false },
            onSave = { category, amount, note, day ->
                viewModel.addRecurring(category, amount, note, day)
                creatingNew = false
            }
        )
    }

    editing?.let { r ->
        RecurringEditDialog(
            existing = r,
            categories = categories,
            onDismiss = { editing = null },
            onSave = { category, amount, note, day ->
                viewModel.updateRecurring(r.id, category, amount, note, day)
                editing = null
            }
        )
    }

    toDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete this recurring expense?") },
            text = {
                Text(
                    "${categoryMeta(r.category, categories).label} · ${formatINR(r.amount)} on day ${r.dayOfMonth}. " +
                        "Entries already posted stay; it just won't post again."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecurring(r)
                    toDelete = null
                }) { Text("Delete", color = Rust) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringEditDialog(
    existing: RecurringExpense?,
    categories: List<com.khaata.app.util.CategoryMeta>,
    onDismiss: () -> Unit,
    onSave: (category: String, amount: Double, note: String, dayOfMonth: Int) -> Unit,
) {
    var category by remember { mutableStateOf(existing?.category ?: categories.firstOrNull()?.key ?: "other") }
    var categoryExpanded by remember { mutableStateOf(false) }
    // BigDecimal.toPlainString avoids Double.toString's scientific notation (e.g. "1.0E8"),
    // which isMoneyInputAllowed would reject and force the user to clear before editing.
    var amount by remember {
        mutableStateOf(
            if (existing == null || existing.amount == 0.0) ""
            else java.math.BigDecimal.valueOf(existing.amount).stripTrailingZeros().toPlainString()
        )
    }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var day by remember { mutableStateOf((existing?.dayOfMonth ?: 1).toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New recurring expense" else "Edit recurring expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    onValueChange = { if (isMoneyInputAllowed(it)) { amount = it; error = null } },
                    label = { Text("Amount") },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = day,
                    onValueChange = { input -> if (input.isEmpty() || input.all { it.isDigit() }) { day = input.take(2); error = null } },
                    label = { Text("Day of month (1–31)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                // Days 29–31 don't exist in every month. postDueRecurring clamps to the
                // month's last day, so tell the user rather than letting them assume it
                // silently skips (Feb) or posts on a day that isn't there.
                day.toIntOrNull()?.let { d ->
                    if (d in 29..31) {
                        Text(
                            "In shorter months this posts on the last day instead " +
                                "(e.g. day $d → Feb 28/29).",
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, label = { Text("Note (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = Rust, fontSize = 11.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = parsePositiveAmount(amount)
                val dayInt = day.toIntOrNull()
                when {
                    amt == null -> error = "Enter an amount greater than 0."
                    dayInt == null || dayInt < 1 || dayInt > 31 -> error = "Day must be between 1 and 31."
                    else -> onSave(category, amt, note.trim(), dayInt)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
