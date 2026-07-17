package com.khaata.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.khaata.app.data.model.Person
import com.khaata.app.data.model.todayStr
import com.khaata.app.ui.components.DatePickerField
import com.khaata.app.ui.theme.Green
import com.khaata.app.ui.theme.Ink
import com.khaata.app.ui.theme.Muted
import com.khaata.app.ui.theme.Rust
import com.khaata.app.util.CategoryMeta
import com.khaata.app.util.evaluateExpression
import com.khaata.app.util.formatINR
import com.khaata.app.util.isMoneyOrExprInputAllowed
import com.khaata.app.viewmodel.FinanceViewModel.SplitPart

/** A single editable row in the split dialog: what it's for, and how much. */
private class SplitRow(
    initialTarget: String,
    amount: String = "",
    note: String = "",
) {
    // "cat:<key>" for an expense part, "person:<id>" for an udhaar part.
    var target by mutableStateOf(initialTarget)
    var amount by mutableStateOf(amount)
    var note by mutableStateOf(note)
}

/**
 * Splits one payment into parts. Each part is either a category (books an expense)
 * or a person (books a "they owe you" udhaar entry, since you paid their share).
 * Amounts are expression-capable. A running total shows how much is assigned vs the
 * optional bill total, so the user can see what's left to allocate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitExpenseDialog(
    categories: List<CategoryMeta>,
    people: List<Person>,
    onDismiss: () -> Unit,
    onSave: (date: String, parts: List<SplitPart>) -> Unit,
) {
    val firstCat = "cat:${categories.firstOrNull()?.key ?: "other"}"
    var date by remember { mutableStateOf(todayStr()) }
    var totalText by remember { mutableStateOf("") }
    val rows = remember { mutableStateListOf(SplitRow(firstCat), SplitRow(firstCat)) }

    val assigned = rows.sumOf { evaluateExpression(it.amount) ?: 0.0 }
    val total = evaluateExpression(totalText)
    val remaining = total?.let { it - assigned }

    fun labelFor(target: String): String = when {
        target.startsWith("cat:") -> categories.firstOrNull { it.key == target.removePrefix("cat:") }?.label ?: "Other"
        target.startsWith("person:") -> "To " + (people.firstOrNull { it.id == target.removePrefix("person:") }?.name ?: "someone")
        else -> "Pick"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Split a bill") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Split one payment across categories, or assign a share to someone you paid for (it goes to their udhaar).",
                    color = Muted, fontSize = 12.sp
                )
                DatePickerField(label = "Date", value = date, onValueChange = { date = it }, modifier = Modifier.fillMaxWidth(), allowFuture = false)

                OutlinedTextField(
                    value = totalText,
                    onValueChange = { if (isMoneyOrExprInputAllowed(it)) totalText = it },
                    label = { Text("Bill total (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )

                rows.forEachIndexed { index, row ->
                    SplitRowEditor(
                        row = row,
                        label = labelFor(row.target),
                        categories = categories,
                        people = people,
                        canRemove = rows.size > 1,
                        onRemove = { rows.removeAt(index) }
                    )
                }

                TextButton(onClick = { rows.add(SplitRow(firstCat)) }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add a part")
                }

                // Running tally against the optional total.
                Column {
                    Text("Assigned: ${formatINR(assigned)}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Ink)
                    if (remaining != null) {
                        val color = when {
                            kotlin.math.abs(remaining) < 0.01 -> Green
                            remaining < 0 -> Rust
                            else -> Muted
                        }
                        val text = when {
                            kotlin.math.abs(remaining) < 0.01 -> "Fully allocated"
                            remaining < 0 -> "Over by ${formatINR(-remaining)}"
                            else -> "${formatINR(remaining)} left to assign"
                        }
                        Text(text, fontSize = 12.sp, color = color)
                    }
                }
            }
        },
        confirmButton = {
            val parts = rows.mapNotNull { row ->
                val amt = evaluateExpression(row.amount)?.takeIf { it > 0.0 } ?: return@mapNotNull null
                when {
                    row.target.startsWith("cat:") -> SplitPart(categoryKey = row.target.removePrefix("cat:"), amount = amt, note = row.note.trim())
                    row.target.startsWith("person:") -> SplitPart(personId = row.target.removePrefix("person:"), amount = amt, note = row.note.trim())
                    else -> null
                }
            }
            TextButton(
                enabled = parts.isNotEmpty(),
                onClick = { onSave(date, parts) }
            ) { Text("Save split") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SplitRowEditor(
    row: SplitRow,
    label: String,
    categories: List<CategoryMeta>,
    people: List<Person>,
    canRemove: Boolean,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            OutlinedTextField(
                value = label,
                onValueChange = {},
                readOnly = true,
                label = { Text("For") },
                singleLine = true,
                modifier = Modifier.width(140.dp)
            )
            // Transparent overlay so a tap anywhere on the read-only field opens the menu
            // (a readOnly OutlinedTextField swallows clicks otherwise).
            Box(Modifier.matchParentSize().clickable { menuOpen = true })
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                categories.forEach { c ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(c.color, CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text(c.label)
                            }
                        },
                        onClick = { row.target = "cat:${c.key}"; menuOpen = false }
                    )
                }
                if (people.isNotEmpty()) {
                    DropdownMenuItem(enabled = false, text = { Text("Owe to you (udhaar)", color = Muted, fontSize = 12.sp) }, onClick = {})
                    people.forEach { p ->
                        DropdownMenuItem(
                            text = { Text("To ${p.name}") },
                            onClick = { row.target = "person:${p.id}"; menuOpen = false }
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = row.amount,
            onValueChange = { if (isMoneyOrExprInputAllowed(it)) row.amount = it },
            label = { Text("Amount") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.width(0.dp).weight(1f)
        )
        if (canRemove) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove part", tint = Rust, modifier = Modifier.size(18.dp))
            }
        }
    }
}
