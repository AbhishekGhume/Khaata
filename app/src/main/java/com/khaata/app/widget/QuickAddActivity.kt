package com.khaata.app.widget

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import com.google.firebase.auth.FirebaseAuth
import com.khaata.app.MainActivity
import com.khaata.app.data.model.Expense
import com.khaata.app.data.model.Template
import com.khaata.app.data.model.monthKeyFromDate
import com.khaata.app.data.model.todayStr
import com.khaata.app.data.repository.FinanceRepository
import com.khaata.app.notifications.EXTRA_OPEN_ADD_ENTRY
import com.khaata.app.notifications.EXTRA_QUICK_ADD_CATEGORY
import com.khaata.app.ui.theme.Green
import com.khaata.app.ui.theme.Ink
import com.khaata.app.ui.theme.Muted
import com.khaata.app.ui.theme.Paper
import com.khaata.app.ui.theme.PaperCard
import com.khaata.app.ui.theme.PaperLine
import com.khaata.app.ui.theme.Rust
import com.khaata.app.util.CategoryMeta
import com.khaata.app.util.evaluateExpression
import com.khaata.app.util.formatINR
import com.khaata.app.util.isMoneyOrExprInputAllowed
import com.khaata.app.util.looksLikeExpression
import com.khaata.app.util.moneyToInput
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * A lightweight, transparent dialog that logs one expense straight to Firestore
 * without ever loading the full app. Launched from the home-screen widget
 * ([AddEntryWidget]) and the reminder notification's "Quick add" action.
 *
 * Deliberately bypasses the app's security lock: adding an expense reveals no
 * existing ledger data, and the whole point is speed. Viewing the ledger still
 * goes through [MainActivity]'s gate.
 *
 * The write is fired on [quickAddScope] (an app-lifetime scope) rather than the
 * Activity's lifecycle scope, so finishing the popup immediately doesn't cancel
 * the server ack. The Firestore mutation is queued synchronously on commit, so
 * it's durable offline regardless.
 */
class QuickAddActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            // Can't write without a signed-in user — send them to the app to sign in.
            Toast.makeText(this, "Sign in to add an expense", Toast.LENGTH_SHORT).show()
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    putExtra(EXTRA_OPEN_ADD_ENTRY, true)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
            finish()
            return
        }

        val repository = FinanceRepository(uid)
        // The user's own categories + quick-add templates, mirrored device-locally by
        // the app (see CategoryCache / TemplateCache) — so renames/additions show up
        // here without a Firestore read.
        val categories = CategoryCache.load(this)
        val templates = TemplateCache.load(this)
        val presetCategory = intent?.getStringExtra(EXTRA_QUICK_ADD_CATEGORY)
            ?.takeIf { key -> categories.any { it.key == key } }
            ?: categories.first().key

        setContent {
            QuickAddDialog(
                categories = categories,
                templates = templates,
                presetCategory = presetCategory,
                onDismiss = { finish() },
                onSave = { category, amount, note, date ->
                    // App-scoped so it outlives this Activity's finish().
                    quickAddScope.launch {
                        runCatching {
                            repository.addExpense(
                                monthKeyFromDate(date),
                                Expense(category = category, amount = amount, note = note, date = date)
                            )
                        }
                    }
                    Toast.makeText(this, "Saved ${formatINR(amount)}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            )
        }
    }

    companion object {
        /** App-lifetime scope so a queued write's server ack isn't cancelled when the popup finishes. */
        private val quickAddScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun QuickAddDialog(
    categories: List<CategoryMeta>,
    templates: List<Template>,
    presetCategory: String,
    onDismiss: () -> Unit,
    onSave: (category: String, amount: Double, note: String, date: String) -> Unit,
) {
    var category by remember { mutableStateOf(presetCategory) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(todayStr()) }
    var error by remember { mutableStateOf<String?>(null) }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val resolved = remember(amount) { evaluateExpression(amount) }

    fun submit() {
        val value = evaluateExpression(amount)
        when {
            amount.isBlank() -> error = "Enter an amount."
            value == null || value <= 0.0 -> error = "Enter a valid amount."
            else -> onSave(category, value, note.trim(), date)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = PaperCard,
            border = BorderStroke(1.dp, PaperLine),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Add expense",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Ink,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = Muted,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(onClick = onDismiss)
                    )
                }
                Spacer(Modifier.height(14.dp))

                // One-tap template chips — tap fills the whole form, then Save is one
                // more tap. Same templates the Add Entry screen shows.
                if (templates.isNotEmpty()) {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        templates.forEach { t ->
                            val meta = categories.firstOrNull { it.key == t.category }
                            Surface(
                                color = Paper,
                                border = BorderStroke(1.dp, PaperLine),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.clickable {
                                    category = t.category.takeIf { key -> categories.any { it.key == key } } ?: category
                                    amount = moneyToInput(t.amount)
                                    note = t.note
                                    error = null
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (meta != null) {
                                        Box(Modifier.size(8.dp).background(meta.color, CircleShape))
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(t.label.ifBlank { meta?.label ?: t.category }, fontSize = 12.sp, color = Ink)
                                    Spacer(Modifier.width(6.dp))
                                    Text(formatINR(t.amount), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Muted)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Amount
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (isMoneyOrExprInputAllowed(it)) { amount = it; error = null } },
                    placeholder = { Text("0") },
                    prefix = { Text("₹ ") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = {
                        when {
                            error != null -> Text(error!!, color = Rust, fontSize = 11.sp)
                            looksLikeExpression(amount) && resolved != null ->
                                Text("= ${formatINR(resolved)}", color = Green, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    },
                    // Phone keypad, same as the in-app entry form: it exposes + − ( ) * /
                    // so expressions like 340+55 can be typed here too.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                Spacer(Modifier.height(12.dp))

                // Category chips
                Text("Category", fontSize = 12.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    categories.forEach { meta ->
                        val selected = meta.key == category
                        Surface(
                            color = if (selected) meta.color else Paper,
                            border = BorderStroke(1.dp, if (selected) meta.color else PaperLine),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.clickable { category = meta.key }
                        ) {
                            Text(
                                meta.label,
                                color = if (selected) Color.White else Ink,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // Date — defaults to today; "Yesterday" covers the forgot-to-log-dinner
                // case, and the calendar chip handles anything older.
                Text("Date", fontSize = 12.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                QuickDateRow(date = date, onDateChange = { date = it })
                Spacer(Modifier.height(12.dp))

                // Note
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("Note (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                // Save
                Button(
                    onClick = { focusManager.clearFocus(); submit() },
                    colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Save", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "Saving to $date",
                    fontSize = 11.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 6.dp).align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

/**
 * Today / Yesterday chips plus a calendar chip for anything older. Selected chip is
 * filled Ink; a custom-picked date shows on the calendar chip itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickDateRow(date: String, onDateChange: (String) -> Unit) {
    val today = todayStr()
    val yesterday = remember { LocalDate.now().minusDays(1).toString() }
    var showPicker by remember { mutableStateOf(false) }
    val isCustom = date != today && date != yesterday

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DateChip("Today", selected = date == today) { onDateChange(today) }
        DateChip("Yesterday", selected = date == yesterday) { onDateChange(yesterday) }
        DateChip(if (isCustom) date else "Pick…", selected = isCustom) { showPicker = true }
    }

    if (showPicker) {
        val initialMillis = runCatching {
            LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
        // Expenses can only have happened already — cap selection at today (UTC,
        // matching the picker's own UTC-based millis), same as DatePickerField.
        val todayEndMillis = remember {
            LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1
        }
        val todayYear = remember { LocalDate.now(ZoneOffset.UTC).year }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayEndMillis
                override fun isSelectableYear(year: Int) = year <= todayYear
            }
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onDateChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun DateChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) Ink else Paper,
        border = BorderStroke(1.dp, if (selected) Ink else PaperLine),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            label,
            color = if (selected) Paper else Ink,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}
