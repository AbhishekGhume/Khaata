package com.khaata.app.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khaata.app.data.model.Goal
import com.khaata.app.data.model.GoalStats
import com.khaata.app.data.model.GoalStatus
import com.khaata.app.data.model.computeStats
import com.khaata.app.data.model.currentMonthKey
import com.khaata.app.data.model.todayStr
import com.khaata.app.ui.components.DatePickerField
import com.khaata.app.ui.components.ProgressStamp
import com.khaata.app.ui.components.StatusBadge
import com.khaata.app.ui.theme.Green
import com.khaata.app.ui.theme.Ink
import com.khaata.app.ui.theme.Muted
import com.khaata.app.ui.theme.Paper
import com.khaata.app.ui.theme.PaperCard
import com.khaata.app.ui.theme.PaperLine
import com.khaata.app.ui.theme.Rust
import com.khaata.app.ui.theme.Gold
import com.khaata.app.util.formatINR
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

    var openContribGoalId by remember { mutableStateOf<String?>(null) }
    var contribAmount by remember { mutableStateOf("") }
    var contribDate by remember { mutableStateOf(todayStr()) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Savings goals", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Button(
                    onClick = { showAddForm = !showAddForm },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showAddForm) PaperLine else Ink,
                        contentColor = if (showAddForm) Ink else Paper
                    )
                ) {
                    Icon(if (showAddForm) Icons.Filled.Close else Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (showAddForm) "Cancel" else "New goal")
                }
            }
        }

        if (showAddForm) {
            item {
                Surface(color = PaperCard, border = BorderStroke(1.dp, PaperLine), shape = RoundedCornerShape(10.dp)) {
                    FlowRow(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Goal name (e.g. Bike)") }, modifier = Modifier.widthIn(min = 170.dp))
                        OutlinedTextField(
                            value = targetAmount, onValueChange = { targetAmount = it }, label = { Text("Target amount (\u20B9)") },
                            modifier = Modifier.width(160.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        DatePickerField(label = "Target date", value = targetDate, onValueChange = { targetDate = it }, modifier = Modifier.width(160.dp))
                        Button(
                            onClick = {
                                val amt = targetAmount.toDoubleOrNull()
                                if (name.isNotBlank() && amt != null && amt > 0 && targetDate.isNotBlank()) {
                                    viewModel.addGoal(name.trim(), amt, targetDate.trim())
                                    name = ""; targetAmount = ""; targetDate = ""; showAddForm = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Green)
                        ) { Text("Save goal") }
                    }
                }
            }
        }

        if (goalStats.isEmpty()) {
            item { Text("No goals yet \u2014 add the thing you're saving for, its cost, and your target date.", color = Muted, fontSize = 13.sp) }
        }

        items(goalStats, key = { it.first.id }) { (goal, stats) ->
            GoalCard(
                goal = goal, stats = stats,
                isContribOpen = openContribGoalId == goal.id,
                contribAmount = contribAmount, contribDate = contribDate,
                onToggleContrib = {
                    openContribGoalId = if (openContribGoalId == goal.id) null else goal.id
                    contribAmount = ""; contribDate = todayStr()
                },
                onContribAmountChange = { contribAmount = it },
                onContribDateChange = { contribDate = it },
                onSaveContrib = {
                    val amt = contribAmount.toDoubleOrNull()
                    if (amt != null && amt > 0) {
                        viewModel.logContribution(goal.id, amt, contribDate)
                        openContribGoalId = null; contribAmount = ""
                    }
                },
                onDelete = { viewModel.deleteGoal(goal.id) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalCard(
    goal: Goal, stats: GoalStats, isContribOpen: Boolean,
    contribAmount: String, contribDate: String,
    onToggleContrib: () -> Unit, onContribAmountChange: (String) -> Unit, onContribDateChange: (String) -> Unit,
    onSaveContrib: () -> Unit, onDelete: () -> Unit
) {
    Surface(color = PaperCard, border = BorderStroke(1.dp, PaperLine), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                ProgressStamp(pct = stats.pct, color = if (stats.achieved) Green else if (stats.status == GoalStatus.BEHIND) Rust else Gold)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(goal.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        StatusBadge(stats.status)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Target ${formatINR(goal.targetAmount)} by ${goal.targetDate} \u00b7 " +
                            if (stats.daysLeft >= 0) (if (stats.daysLeft < 31) "${stats.daysLeft} days left" else "${kotlin.math.ceil(stats.monthsLeftRaw).toInt()} months left") else "past due",
                        color = Muted, fontSize = 12.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Saved ${formatINR(goal.savedAmount)}", fontSize = 12.sp)
                        if (!stats.achieved) Text("Needed pace ${formatINR(stats.requiredMonthly)}/mo", fontSize = 12.sp)
                        Text("This month ${formatINR(stats.contributedThisMonth)}", fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onToggleContrib) { Text(if (isContribOpen) "Close" else "Log saved \u20B9") }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete goal", tint = Rust, modifier = Modifier.size(16.dp)) }
                    }
                }
            }

            if (isContribOpen) {
                Divider(color = PaperLine)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = contribAmount, onValueChange = onContribAmountChange, label = { Text("Amount") },
                        modifier = Modifier.width(120.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    DatePickerField(label = "Date", value = contribDate, onValueChange = onContribDateChange, modifier = Modifier.width(150.dp))
                    Button(onClick = onSaveContrib, colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("Save") }
                }
            }
        }
    }
}
