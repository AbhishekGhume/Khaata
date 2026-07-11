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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khaata.app.data.model.LedgerEntry
import com.khaata.app.data.model.Person
import com.khaata.app.data.model.todayStr
import com.khaata.app.ui.components.DatePickerField
import com.khaata.app.ui.components.SummaryCard
import com.khaata.app.ui.theme.Green
import com.khaata.app.ui.theme.GreenSoft
import com.khaata.app.ui.theme.Ink
import com.khaata.app.ui.theme.Muted
import com.khaata.app.ui.theme.Paper
import com.khaata.app.ui.theme.PaperCard
import com.khaata.app.ui.theme.PaperLine
import com.khaata.app.ui.theme.Rust
import com.khaata.app.ui.theme.RustSoft
import com.khaata.app.util.formatINR
import com.khaata.app.util.isMoneyInputAllowed
import com.khaata.app.viewmodel.FinanceViewModel

/**
 * Udhaar / people ledger — who owes whom. Each person carries a running [Person.balance]
 * (positive = they owe you, negative = you owe them). "You gave" adds a positive entry,
 * "You got" adds a negative one, and "Settle up" posts an offsetting entry that zeroes
 * the balance. Mirrors GoalsScreen's card + inline-form structure.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeopleScreen(viewModel: FinanceViewModel) {
    val people by viewModel.people.collectAsState()
    val owedToYou = remember(people) { people.filter { it.balance > 0.0 }.sumOf { it.balance } }
    val youOwe = remember(people) { people.filter { it.balance < 0.0 }.sumOf { -it.balance } }

    var showAddForm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }

    // Inline "add entry" form — single-open across cards (like GoalsScreen's contrib form).
    var openEntryPersonId by remember { mutableStateOf<String?>(null) }
    var entryGave by remember { mutableStateOf(true) }
    var entryAmount by remember { mutableStateOf("") }
    var entryNote by remember { mutableStateOf("") }
    var entryDate by remember { mutableStateOf(todayStr()) }

    var personToSettle by remember { mutableStateOf<Person?>(null) }
    var personToDelete by remember { mutableStateOf<Person?>(null) }

    fun openEntry(personId: String, gave: Boolean) {
        openEntryPersonId = personId
        entryGave = gave
        entryAmount = ""; entryNote = ""; entryDate = todayStr()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Udhaar — who owes whom",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { showAddForm = !showAddForm; addError = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showAddForm) PaperLine else Ink,
                        contentColor = if (showAddForm) Ink else Paper
                    )
                ) {
                    Icon(
                        if (showAddForm) Icons.Filled.Close else Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (showAddForm) "Cancel" else "Add person")
                }
            }
        }

        // Owed / owing summary — self-contained on this screen.
        if (people.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        label = "YOU ARE OWED",
                        value = formatINR(owedToYou),
                        accent = Green
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        label = "YOU OWE",
                        value = formatINR(youOwe),
                        accent = Rust
                    )
                }
            }
        }

        if (showAddForm) {
            item {
                Surface(
                    color = PaperCard,
                    border = BorderStroke(1.dp, PaperLine),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    FlowRow(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it; addError = null },
                            label = { Text("Name (e.g. Rohan)") },
                            modifier = Modifier.widthIn(min = 170.dp)
                        )
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("Note (optional)") },
                            modifier = Modifier.widthIn(min = 170.dp)
                        )
                        Button(
                            onClick = {
                                val error = viewModel.validatePersonName(name)
                                if (error == null) {
                                    viewModel.addPerson(name, note)
                                    name = ""; note = ""; addError = null; showAddForm = false
                                } else {
                                    addError = error
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Green)
                        ) { Text("Add") }
                        if (addError != null) {
                            Text(addError!!, color = Rust, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        if (people.isEmpty()) {
            item {
                Text(
                    "No one yet — add a person to start tracking who owes whom.",
                    color = Muted,
                    fontSize = 13.sp
                )
            }
        }

        items(people, key = { it.id }) { person ->
            // Live ledger for this card. LazyColumn only composes visible items, so
            // there's one snapshot listener per on-screen person.
            val ledger by remember(person.id) { viewModel.ledgerFor(person.id) }
                .collectAsState(initial = emptyList())
            PersonCard(
                person = person,
                ledger = ledger,
                isEntryOpen = openEntryPersonId == person.id,
                entryGave = entryGave,
                entryAmount = entryAmount,
                entryNote = entryNote,
                entryDate = entryDate,
                onOpenGave = { openEntry(person.id, true) },
                onOpenGot = { openEntry(person.id, false) },
                onCloseEntry = { openEntryPersonId = null },
                onEntryAmountChange = { entryAmount = it },
                onEntryNoteChange = { entryNote = it },
                onEntryDateChange = { entryDate = it },
                onSaveEntry = {
                    val amt = entryAmount.toDoubleOrNull()
                    if (amt != null && amt > 0) {
                        viewModel.recordLedgerEntry(person.id, if (entryGave) amt else -amt, entryNote, entryDate)
                        openEntryPersonId = null; entryAmount = ""; entryNote = ""
                    }
                },
                onDeleteEntry = { entry -> viewModel.deleteLedgerEntry(person.id, entry) },
                onSettle = { personToSettle = person },
                onDelete = { personToDelete = person }
            )
        }
    }

    personToSettle?.let { person ->
        val youAreOwed = person.balance > 0.0
        AlertDialog(
            onDismissRequest = { personToSettle = null },
            title = { Text("Settle up with ${person.name}?") },
            text = {
                Text(
                    if (youAreOwed)
                        "This records ${person.name} paying you back ${formatINR(person.balance)} and clears the balance to ₹0."
                    else
                        "This records you paying ${person.name} ${formatINR(-person.balance)} and clears the balance to ₹0."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.settleUp(person)
                    personToSettle = null
                }) { Text("Settle up", color = Green) }
            },
            dismissButton = { TextButton(onClick = { personToSettle = null }) { Text("Cancel") } }
        )
    }

    personToDelete?.let { person ->
        AlertDialog(
            onDismissRequest = { personToDelete = null },
            title = { Text("Remove \"${person.name}\"?") },
            text = {
                Text(
                    "This removes ${person.name} and their entire udhaar history. You can undo right after."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePerson(person)
                    personToDelete = null
                }) { Text("Remove", color = Rust) }
            },
            dismissButton = { TextButton(onClick = { personToDelete = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonCard(
    person: Person,
    ledger: List<LedgerEntry>,
    isEntryOpen: Boolean,
    entryGave: Boolean,
    entryAmount: String,
    entryNote: String,
    entryDate: String,
    onOpenGave: () -> Unit,
    onOpenGot: () -> Unit,
    onCloseEntry: () -> Unit,
    onEntryAmountChange: (String) -> Unit,
    onEntryNoteChange: (String) -> Unit,
    onEntryDateChange: (String) -> Unit,
    onSaveEntry: () -> Unit,
    onDeleteEntry: (LedgerEntry) -> Unit,
    onSettle: () -> Unit,
    onDelete: () -> Unit,
) {
    var showHistory by remember(person.id) { mutableStateOf(false) }
    var entryToDelete by remember(person.id) { mutableStateOf<LedgerEntry?>(null) }

    val settled = kotlin.math.abs(person.balance) < 0.001
    val youAreOwed = person.balance > 0.0
    val statusColor = when {
        settled -> Muted
        youAreOwed -> Green
        else -> Rust
    }

    Surface(
        color = PaperCard,
        border = BorderStroke(1.dp, PaperLine),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(16.dp)) {

            // ── Top: name + status pill ─────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(person.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (person.note.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(person.note, color = Muted, fontSize = 12.sp)
                    }
                }
                BalancePill(
                    text = when {
                        settled -> "Settled"
                        youAreOwed -> "Owes you"
                        else -> "You owe"
                    },
                    color = statusColor
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = PaperLine)
            Spacer(Modifier.height(10.dp))

            // ── Balance figure ──────────────────────────────────────────────
            Column {
                Text(
                    when {
                        settled -> "ALL SETTLED"
                        youAreOwed -> "${person.name.uppercase()} OWES YOU"
                        else -> "YOU OWE ${person.name.uppercase()}"
                    },
                    fontSize = 10.sp,
                    color = Muted,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    formatINR(kotlin.math.abs(person.balance)),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = statusColor
                )
            }

            Spacer(Modifier.height(10.dp))

            // ── Ledger history ──────────────────────────────────────────────
            if (ledger.isNotEmpty()) {
                HorizontalDivider(color = PaperLine)
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "History",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Muted,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showHistory = !showHistory }) {
                        Icon(
                            if (showHistory) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = Muted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(if (showHistory) "Hide" else "Show", color = Muted, fontSize = 12.sp)
                    }
                }

                // Always show the most recent entry; the rest expand on demand.
                LedgerRow(entry = ledger.first(), onDelete = { entryToDelete = ledger.first() })
                if (showHistory && ledger.size > 1) {
                    ledger.drop(1).forEach { entry ->
                        LedgerRow(entry = entry, onDelete = { entryToDelete = entry })
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            HorizontalDivider(color = PaperLine)
            Spacer(Modifier.height(8.dp))

            // ── Action row ──────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (isEntryOpen && entryGave) onCloseEntry() else onOpenGave() }) {
                    Text(if (isEntryOpen && entryGave) "Close" else "You gave", color = Green)
                }
                TextButton(onClick = { if (isEntryOpen && !entryGave) onCloseEntry() else onOpenGot() }) {
                    Text(if (isEntryOpen && !entryGave) "Close" else "You got", color = Rust)
                }
                if (!settled) {
                    TextButton(onClick = onSettle) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Settle up")
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remove person",
                        tint = Rust,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // ── Inline add-entry form ───────────────────────────────────────
            if (isEntryOpen) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    color = if (entryGave) GreenSoft else RustSoft,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            if (entryGave) "You gave ${person.name}" else "You got from ${person.name}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (entryGave) Green else Rust
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = entryAmount,
                                onValueChange = { if (isMoneyInputAllowed(it)) onEntryAmountChange(it) },
                                label = { Text("Amount (₹)") },
                                modifier = Modifier.width(130.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                            OutlinedTextField(
                                value = entryNote,
                                onValueChange = onEntryNoteChange,
                                label = { Text("Note (optional)") },
                                modifier = Modifier.widthIn(min = 150.dp)
                            )
                            DatePickerField(
                                label = "Date",
                                value = entryDate,
                                onValueChange = onEntryDateChange,
                                modifier = Modifier.width(150.dp),
                                allowFuture = false
                            )
                            Button(
                                onClick = onSaveEntry,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (entryGave) Green else Rust,
                                    contentColor = Paper
                                )
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }
    }

    entryToDelete?.let { entry ->
        val gave = entry.amount > 0.0
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Remove this entry?") },
            text = {
                Text(
                    "${if (gave) "You gave" else "You got"} ${formatINR(kotlin.math.abs(entry.amount))} " +
                        "on ${entry.date} will be removed and the balance adjusted. You can undo right after."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteEntry(entry)
                    entryToDelete = null
                }) { Text("Remove", color = Rust) }
            },
            dismissButton = { TextButton(onClick = { entryToDelete = null }) { Text("Cancel") } }
        )
    }
}

/** A colored pill showing the udhaar status ("Owes you" / "You owe" / "Settled"). */
@Composable
private fun BalancePill(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
        Text(
            text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/**
 * One row in a person's udhaar history: a colored dot + direction, the note (or date),
 * and the signed amount. Green when you gave (they owe you more), rust when you got.
 */
@Composable
private fun LedgerRow(entry: LedgerEntry, onDelete: () -> Unit) {
    val gave = entry.amount > 0.0
    val color = if (gave) Green else Rust
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (entry.note.isNotBlank()) entry.note else if (gave) "You gave" else "You got",
                fontSize = 12.sp,
                color = Ink
            )
            Text(entry.date, fontSize = 10.sp, color = Muted)
        }
        Text(
            (if (gave) "+" else "−") + formatINR(kotlin.math.abs(entry.amount)),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove entry", tint = Rust, modifier = Modifier.size(15.dp))
        }
    }
}
