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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khaata.app.data.model.Goal
import com.khaata.app.data.model.GoalStats
import com.khaata.app.data.model.GoalStatus
import com.khaata.app.data.model.computeStats
import com.khaata.app.data.model.currentMonthKey
import com.khaata.app.data.model.monthLabel
import com.khaata.app.data.model.todayStr
import com.khaata.app.ui.components.DatePickerField
import com.khaata.app.ui.components.ProgressStamp
import com.khaata.app.ui.components.StatusBadge
import com.khaata.app.ui.theme.Green
import com.khaata.app.ui.theme.GreenSoft
import com.khaata.app.ui.theme.Ink
import com.khaata.app.ui.theme.Muted
import com.khaata.app.ui.theme.Paper
import com.khaata.app.ui.theme.PaperCard
import com.khaata.app.ui.theme.PaperLine
import com.khaata.app.ui.theme.Rust
import com.khaata.app.ui.theme.Gold
import com.khaata.app.util.formatINR
import com.khaata.app.util.isMoneyInputAllowed
import com.khaata.app.util.moneyToInput
import com.khaata.app.util.parsePositiveAmount
import com.khaata.app.viewmodel.FinanceViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalsScreen(viewModel: FinanceViewModel) {
    val goals by viewModel.goals.collectAsState()
    val goalStats = remember(goals) { goals.map { it to it.computeStats(currentMonthKey()) } }

    var showAddForm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var targetAmount by remember { mutableStateOf("") }
    var targetDate by remember { mutableStateOf("") }
    var goalFormError by remember { mutableStateOf<String?>(null) }
    val goalFormWarning = remember(targetAmount, targetDate) {
        val amt = targetAmount.toDoubleOrNull()
        if (amt != null && amt > 0 && targetDate.isNotBlank()) {
            viewModel.goalAllocationWarning(null, amt, targetDate.trim())
        } else null
    }

    var openContribGoalId by remember { mutableStateOf<String?>(null) }
    var contribAmount by remember { mutableStateOf("") }
    var contribDate by remember { mutableStateOf(todayStr()) }
    var contribError by remember { mutableStateOf<String?>(null) }

    // Goal pending delete confirmation.
    var goalToDelete by remember { mutableStateOf<Goal?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Savings goals",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { showAddForm = !showAddForm },
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
                    Text(if (showAddForm) "Cancel" else "New goal")
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
                            onValueChange = { name = it },
                            label = { Text("Goal name (e.g. Bike)") },
                            modifier = Modifier.widthIn(min = 170.dp)
                        )
                        OutlinedTextField(
                            value = targetAmount,
                            onValueChange = { if (isMoneyInputAllowed(it)) targetAmount = it },
                            label = { Text("Target amount (₹)") },
                            modifier = Modifier.width(160.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        DatePickerField(
                            label = "Target date",
                            value = targetDate,
                            onValueChange = { targetDate = it },
                            modifier = Modifier.width(160.dp)
                        )
                        Button(
                            onClick = {
                                val amt = targetAmount.toDoubleOrNull()
                                if (name.isNotBlank() && amt != null && amt > 0 && targetDate.isNotBlank()) {
                                    val validationError = viewModel.validateGoalTarget(null, amt, targetDate.trim())
                                    if (validationError == null) {
                                        // Saved regardless of the allocation warning above — a tight
                                        // or even over-income goal is a valid situation (shows as
                                        // "behind"), not something that should trap the user.
                                        viewModel.addGoal(name.trim(), amt, targetDate.trim())
                                        goalFormError = null
                                        name = ""; targetAmount = ""; targetDate = ""; showAddForm = false
                                    } else {
                                        goalFormError = validationError
                                    }
                                } else {
                                    goalFormError = "Enter goal name, valid amount, and target date."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Green)
                        ) { Text("Save goal") }
                        if (goalFormError != null) {
                            Text(goalFormError!!, color = Rust, fontSize = 12.sp)
                        } else if (goalFormWarning != null) {
                            Text(goalFormWarning, color = Gold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        if (goalStats.isEmpty()) {
            item {
                Text(
                    "No goals yet — add the thing you're saving for, its cost, and your target date.",
                    color = Muted,
                    fontSize = 13.sp
                )
            }
        }

        items(goalStats, key = { it.first.id }) { (goal, stats) ->
            GoalCard(
                goal = goal,
                stats = stats,
                isContribOpen = openContribGoalId == goal.id,
                contribAmount = contribAmount,
                contribDate = contribDate,
                contribError = if (openContribGoalId == goal.id) contribError else null,
                onToggleContrib = {
                    openContribGoalId = if (openContribGoalId == goal.id) null else goal.id
                    contribAmount = ""; contribDate = todayStr(); contribError = null
                },
                onContribAmountChange = { contribAmount = it; contribError = null },
                onContribDateChange = { contribDate = it },
                onSaveContrib = {
                    val amt = contribAmount.toDoubleOrNull()
                    if (amt != null && amt > 0) {
                        viewModel.logContribution(goal.id, amt, contribDate)
                        openContribGoalId = null; contribAmount = ""; contribError = null
                    } else {
                        contribError = "Enter an amount greater than 0."
                    }
                },
                onSaveEdit = { goalId, amountDraft, dateDraft ->
                    val amt = amountDraft.toDoubleOrNull()
                    when {
                        amt == null || amt <= 0.0 -> "Enter a valid target amount."
                        dateDraft.isBlank() -> "Target date is required."
                        else -> {
                            val validationError = viewModel.validateGoalTarget(goalId, amt, dateDraft)
                            if (validationError == null) {
                                // Allocation warnings (goal need vs. income) are informational
                                // only — they never block saving the edit.
                                viewModel.updateGoalTarget(goalId, amt, dateDraft)
                                null
                            } else {
                                validationError
                            }
                        }
                    }
                },
                allocationWarning = { goalId, amountDraft, dateDraft ->
                    val amt = amountDraft.toDoubleOrNull()
                    if (amt != null && amt > 0 && dateDraft.isNotBlank()) {
                        viewModel.goalAllocationWarning(goalId, amt, dateDraft)
                    } else null
                },
                onEditContribution = { monthKey, oldAmount, newAmount ->
                    viewModel.editMonthlyContribution(goal.id, monthKey, oldAmount, newAmount)
                },
                onDelete = { goalToDelete = goal }
            )
        }
    }

    goalToDelete?.let { goal ->
        AlertDialog(
            onDismissRequest = { goalToDelete = null },
            title = { Text("Delete \"${goal.name}\"?") },
            text = {
                Text(
                    "This removes the goal and its entire contribution history " +
                        "(${formatINR(goal.savedAmount)} saved so far). You can undo right after."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGoal(goal)
                    goalToDelete = null
                }) { Text("Delete", color = Rust) }
            },
            dismissButton = { TextButton(onClick = { goalToDelete = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalCard(
    goal: Goal,
    stats: GoalStats,
    isContribOpen: Boolean,
    contribAmount: String,
    contribDate: String,
    contribError: String?,
    onToggleContrib: () -> Unit,
    onContribAmountChange: (String) -> Unit,
    onContribDateChange: (String) -> Unit,
    onSaveContrib: () -> Unit,
    onSaveEdit: (goalId: String, amountDraft: String, dateDraft: String) -> String?,
    allocationWarning: (goalId: String, amountDraft: String, dateDraft: String) -> String? = { _, _, _ -> null },
    onEditContribution: (monthKey: String, oldAmount: Double, newAmount: Double) -> Unit,
    onDelete: () -> Unit,
) {
    // Sort all months with contributions, newest first
    val contribHistory = remember(goal.monthlyContributions) {
        goal.monthlyContributions
            .filter { it.value > 0.0 }
            .entries
            .sortedByDescending { it.key }
    }
    var showHistory by remember { mutableStateOf(false) }
    var isEditOpen by remember { mutableStateOf(false) }
    var editTargetAmount by remember(goal.id) { mutableStateOf(moneyToInput(goal.targetAmount)) }
    var editTargetDate by remember(goal.id) { mutableStateOf(goal.targetDate) }
    var editError by remember(goal.id) { mutableStateOf<String?>(null) }

    // Per-month contribution being edited / deleted (monthKey to current amount).
    var contribToEdit by remember(goal.id) { mutableStateOf<Pair<String, Double>?>(null) }
    var contribToDelete by remember(goal.id) { mutableStateOf<Pair<String, Double>?>(null) }

    Surface(
        color = PaperCard,
        border = BorderStroke(1.dp, PaperLine),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(16.dp)) {

            // ── Top: progress ring + goal info ──────────────────────────────
            Row(verticalAlignment = Alignment.Top) {
                ProgressStamp(
                    pct = stats.pct,
                    color = when {
                        stats.achieved -> Green
                        stats.status == GoalStatus.BEHIND || stats.status == GoalStatus.OVERDUE -> Rust
                        else -> Gold
                    }
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            goal.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        StatusBadge(stats.status)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Target ${formatINR(goal.targetAmount)} by ${goal.targetDate} · " +
                                when {
                                    stats.daysLeft < 0 -> "past due"
                                    stats.daysLeft < 31 -> "${stats.daysLeft} days left"
                                    else -> "${kotlin.math.ceil(stats.monthsLeftRaw).toInt()} months left"
                                },
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = PaperLine)
            Spacer(Modifier.height(10.dp))

            // ── Stats row ───────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatCell(label = "SAVED SO FAR", value = formatINR(goal.savedAmount))
                StatCell(label = "REMAINING", value = formatINR((goal.targetAmount - goal.savedAmount).coerceAtLeast(0.0)))
                if (!stats.achieved) StatCell(label = "NEED / MONTH", value = formatINR(stats.requiredMonthly))
            }

            Spacer(Modifier.height(10.dp))

            // ── Contribution history ─────────────────────────────────────────
            // This is the key missing piece: shows WHEN and HOW MUCH was saved,
            // so the user is never left wondering "did my June log save?"
            if (contribHistory.isNotEmpty()) {
                HorizontalDivider(color = PaperLine)
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Contribution history",
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

                // Always show the most recent entry so it's never invisible
                ContribRow(
                    monthKey = contribHistory.first().key,
                    amount = contribHistory.first().value,
                    isLatest = true,
                    onEdit = { contribToEdit = contribHistory.first().key to contribHistory.first().value },
                    onDelete = { contribToDelete = contribHistory.first().key to contribHistory.first().value }
                )

                if (showHistory && contribHistory.size > 1) {
                    contribHistory.drop(1).forEach { (monthKey, amount) ->
                        ContribRow(
                            monthKey = monthKey,
                            amount = amount,
                            isLatest = false,
                            onEdit = { contribToEdit = monthKey to amount },
                            onDelete = { contribToDelete = monthKey to amount }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            HorizontalDivider(color = PaperLine)
            Spacer(Modifier.height(8.dp))

            // ── Action row ───────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    isEditOpen = !isEditOpen
                    editTargetAmount = moneyToInput(goal.targetAmount)
                    editTargetDate = goal.targetDate
                    editError = null
                }) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isEditOpen) "Close edit" else "Edit target")
                }
                TextButton(onClick = onToggleContrib) {
                    Icon(
                        if (isContribOpen) Icons.Filled.Close else Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (isContribOpen) "Close" else "Log saved ₹")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete goal",
                        tint = Rust,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (isEditOpen) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = Paper,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, PaperLine)
                ) {
                    FlowRow(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editTargetAmount,
                            onValueChange = { if (isMoneyInputAllowed(it)) editTargetAmount = it },
                            label = { Text("Target amount (₹)") },
                            modifier = Modifier.width(150.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        DatePickerField(
                            label = "Target date",
                            value = editTargetDate,
                            onValueChange = { editTargetDate = it },
                            modifier = Modifier.width(150.dp)
                        )
                        Button(
                            onClick = {
                                val error = onSaveEdit(goal.id, editTargetAmount, editTargetDate)
                                editError = error
                                if (error == null) isEditOpen = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper)
                        ) {
                            Text("Save changes")
                        }
                        if (editError != null) {
                            Text(editError!!, color = Rust, fontSize = 12.sp)
                        } else {
                            val liveWarning = remember(editTargetAmount, editTargetDate) {
                                allocationWarning(goal.id, editTargetAmount, editTargetDate)
                            }
                            if (liveWarning != null) {
                                Text(liveWarning, color = Gold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ── Log contribution form ────────────────────────────────────────
            if (isContribOpen) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    color = GreenSoft,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    FlowRow(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = contribAmount,
                            onValueChange = { if (isMoneyInputAllowed(it)) onContribAmountChange(it) },
                            label = { Text("Amount (₹)") },
                            modifier = Modifier.width(130.dp),
                            isError = contribError != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        DatePickerField(
                            label = "Date",
                            value = contribDate,
                            onValueChange = onContribDateChange,
                            modifier = Modifier.width(150.dp)
                        )
                        Button(
                            onClick = onSaveContrib,
                            colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Paper)
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save")
                        }
                        if (contribError != null) {
                            Text(contribError, color = Rust, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    contribToEdit?.let { (monthKey, current) ->
        EditContributionDialog(
            monthKey = monthKey,
            currentAmount = current,
            onDismiss = { contribToEdit = null },
            onSave = { newAmount ->
                onEditContribution(monthKey, current, newAmount)
                contribToEdit = null
            }
        )
    }

    contribToDelete?.let { (monthKey, amount) ->
        AlertDialog(
            onDismissRequest = { contribToDelete = null },
            title = { Text("Remove this contribution?") },
            text = { Text("${formatINR(amount)} saved in ${monthLabel(monthKey)} will be removed from this goal.") },
            confirmButton = {
                TextButton(onClick = {
                    onEditContribution(monthKey, amount, 0.0)
                    contribToDelete = null
                }) { Text("Remove", color = Rust) }
            },
            dismissButton = { TextButton(onClick = { contribToDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EditContributionDialog(
    monthKey: String,
    currentAmount: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    var draft by remember { mutableStateOf(moneyToInput(currentAmount)) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${monthLabel(monthKey)} contribution") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (isMoneyInputAllowed(it)) { draft = it; error = null } },
                label = { Text("Amount saved (₹)") },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it, color = Rust, fontSize = 11.sp) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = parsePositiveAmount(draft)
                if (amt == null) error = "Enter an amount greater than 0." else onSave(amt)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = Muted, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
    }
}

/**
 * One row in the contribution history. Shows the month name and the amount
 * logged that month. The "latest" entry gets a green dot to confirm the most
 * recent save was recorded — directly answering "did my June log save?".
 */
@Composable
private fun ContribRow(
    monthKey: String,
    amount: Double,
    isLatest: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(7.dp)
                .background(if (isLatest) Green else PaperLine, androidx.compose.foundation.shape.CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            monthLabel(monthKey),
            fontSize = 12.sp,
            color = if (isLatest) Ink else Muted,
            modifier = Modifier.weight(1f)
        )
        Text(
            formatINR(amount),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isLatest) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isLatest) Green else Muted
        )
        IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit contribution", tint = Muted, modifier = Modifier.size(15.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove contribution", tint = Rust, modifier = Modifier.size(15.dp))
        }
    }
}