package com.khaata.app.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khaata.app.data.model.currentMonthKey
import com.khaata.app.data.model.todayStr
import com.khaata.app.ui.components.DatePickerField
import com.khaata.app.ui.theme.Gold
import com.khaata.app.ui.theme.Green
import com.khaata.app.ui.theme.GreenSoft
import com.khaata.app.ui.theme.Ink
import com.khaata.app.ui.theme.Muted
import com.khaata.app.ui.theme.Paper
import com.khaata.app.ui.theme.PaperCard
import com.khaata.app.ui.theme.PaperLine
import com.khaata.app.util.CATEGORIES
import com.khaata.app.viewmodel.FinanceViewModel
import java.time.LocalDate

private enum class OnboardingStep(val title: String) {
    WELCOME("Welcome"),
    INCOME("Monthly income"),
    BUDGETS("Set budgets"),
    GOAL("First goal"),
}

/**
 * Shown exactly once, right after a person signs up (or signs in for the
 * very first time on a device with no saved onboarding flag).
 *
 * Design intent, from the user's POV:
 *  - Every step is skippable. Nobody should feel trapped in a wizard.
 *  - We pre-fill sensible defaults (suggested budget caps, today + 6 months
 *    for a goal date) so finishing fast is the path of least resistance.
 *  - We write data using the SAME ViewModel calls the rest of the app uses
 *    (updateIncome / setBudget / addGoal), so nothing here is "fake" demo
 *    data — whatever the user enters is immediately their real first month.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    viewModel: FinanceViewModel,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(OnboardingStep.WELCOME) }

    var incomeDraft by remember { mutableStateOf("") }

    // category key -> whether selected, and a parallel draft amount per category
    val selectedCategories = remember { mutableStateMapOf<String, Boolean>() }
    val budgetDrafts = remember { mutableStateMapOf<String, String>() }

    var goalName by remember { mutableStateOf("") }
    var goalAmount by remember { mutableStateOf("") }
    var goalDate by remember { mutableStateOf(LocalDate.now().plusMonths(6).toString()) }
    var skipGoal by remember { mutableStateOf(false) }

    fun finish() {
        val income = incomeDraft.toDoubleOrNull()
        if (income != null && income > 0) viewModel.updateIncome(income)

        selectedCategories.filterValues { it }.keys.forEach { category ->
            val amount = budgetDrafts[category]?.toDoubleOrNull()
            if (amount != null && amount > 0) viewModel.setBudget(category, amount)
        }

        if (!skipGoal) {
            val target = goalAmount.toDoubleOrNull()
            if (goalName.isNotBlank() && target != null && target > 0) {
                viewModel.addGoal(goalName.trim(), target, goalDate)
            }
        }

        setOnboardingComplete(context, true)
        onComplete()
    }

    fun skipAll() {
        setOnboardingComplete(context, true)
        onComplete()
    }

    Box(Modifier.fillMaxSize().background(Paper)) {
        Column(Modifier.fillMaxSize()) {
            // Progress dots + skip-all, always visible so the user always
            // knows "how much is left" and always has an exit.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OnboardingStep.entries.forEach { s ->
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(
                                    if (s.ordinal <= step.ordinal) Ink else PaperLine,
                                    CircleShape
                                )
                        )
                    }
                }
                if (step != OnboardingStep.WELCOME) {
                    TextButton(onClick = { skipAll() }) { Text("Skip setup", color = Muted) }
                }
            }

            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                    label = "onboarding-step"
                ) { current ->
                    when (current) {
                        OnboardingStep.WELCOME -> WelcomeStep()
                        OnboardingStep.INCOME -> IncomeStep(
                            incomeDraft = incomeDraft,
                            onIncomeChange = { incomeDraft = it }
                        )
                        OnboardingStep.BUDGETS -> BudgetsStep(
                            selectedCategories = selectedCategories,
                            budgetDrafts = budgetDrafts
                        )
                        OnboardingStep.GOAL -> GoalStep(
                            goalName = goalName,
                            onNameChange = { goalName = it },
                            goalAmount = goalAmount,
                            onAmountChange = { goalAmount = it },
                            goalDate = goalDate,
                            onDateChange = { goalDate = it },
                            skipGoal = skipGoal,
                            onSkipGoalChange = { skipGoal = it }
                        )
                    }
                }
            }

            // Footer nav: Back is hidden on step 1, label changes to
            // "Get started" on the very first step and "Finish" on the last.
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (step != OnboardingStep.WELCOME) {
                    OutlinedButton(
                        onClick = { step = OnboardingStep.entries[step.ordinal - 1] },
                        modifier = Modifier.weight(1f)
                    ) { Text("Back") }
                }
                Button(
                    onClick = {
                        if (step == OnboardingStep.GOAL) finish()
                        else step = OnboardingStep.entries[step.ordinal + 1]
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        when (step) {
                            OnboardingStep.WELCOME -> "Get started"
                            OnboardingStep.GOAL -> "Finish setup"
                            else -> "Next"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StepShell(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Box(
            Modifier
                .size(52.dp)
                .background(GreenSoft, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Green, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, fontSize = 14.sp, color = Muted, lineHeight = 20.sp)
        Spacer(Modifier.height(24.dp))
        content()
    }
}

@Composable
private fun WelcomeStep() {
    StepShell(
        icon = Icons.Filled.AccountBalanceWallet,
        title = "Welcome to Khaata",
        subtitle = "Your digital passbook for everyday money \u2014 income, kharcha, budgets, and the goals you're saving toward. Let's set up the basics; it takes under a minute and every step is optional."
    ) {}
}

@Composable
private fun IncomeStep(incomeDraft: String, onIncomeChange: (String) -> Unit) {
    StepShell(
        icon = Icons.Filled.AccountBalanceWallet,
        title = "What do you earn this month?",
        subtitle = "This is just for ${currentMonthKeyLabel()} \u2014 you can update it any month from the Dashboard. It's what your savings and goal pace get measured against."
    ) {
        OutlinedTextField(
            value = incomeDraft,
            onValueChange = { onIncomeChange(it.filter { ch -> ch.isDigit() || ch == '.' }) },
            label = { Text("Monthly income (\u20B9)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Tip: a rough number is fine. You can refine it later \u2014 nothing here is locked in.",
            fontSize = 12.sp,
            color = Muted
        )
    }
}

private fun currentMonthKeyLabel(): String = com.khaata.app.data.model.monthLabel(currentMonthKey())

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetsStep(
    selectedCategories: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>,
    budgetDrafts: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>,
) {
    StepShell(
        icon = Icons.Filled.PieChart,
        title = "Cap a few categories",
        subtitle = "Tap the categories you actually want to control, then set a monthly limit. Most people start with 2\u20133 \u2014 you can add the rest anytime from Budgets."
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            items(CATEGORIES) { meta ->
                val isSelected = selectedCategories[meta.key] == true
                Surface(
                    color = if (isSelected) meta.color.copy(alpha = 0.12f) else PaperCard,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) meta.color else PaperLine
                    ),
                    shape = RoundedCornerShape(10.dp),
                    onClick = { selectedCategories[meta.key] = !isSelected },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Box(Modifier.size(10.dp).background(meta.color, CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Text(meta.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            if (isSelected) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = meta.color, modifier = Modifier.size(18.dp))
                        }
                        if (isSelected) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = budgetDrafts[meta.key] ?: "",
                                onValueChange = { budgetDrafts[meta.key] = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                label = { Text("Monthly cap (\u20B9)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }
    }
}

@Composable
private fun GoalStep(
    goalName: String,
    onNameChange: (String) -> Unit,
    goalAmount: String,
    onAmountChange: (String) -> Unit,
    goalDate: String,
    onDateChange: (String) -> Unit,
    skipGoal: Boolean,
    onSkipGoalChange: (Boolean) -> Unit,
) {
    StepShell(
        icon = Icons.Filled.Flag,
        title = "Saving toward something?",
        subtitle = "A bike, a trip, an emergency fund \u2014 give it a name and a target, and Khaata will tell you how much to set aside each month to stay on pace."
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(checked = skipGoal, onCheckedChange = onSkipGoalChange)
            Text("I don't have a goal in mind right now", fontSize = 13.sp, color = Muted)
        }
        if (!skipGoal) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = goalName,
                onValueChange = onNameChange,
                label = { Text("Goal name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = goalAmount,
                onValueChange = { onAmountChange(it.filter { ch -> ch.isDigit() || ch == '.' }) },
                label = { Text("Target amount (\u20B9)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            DatePickerField(
                label = "Target date",
                value = goalDate,
                onValueChange = onDateChange,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}