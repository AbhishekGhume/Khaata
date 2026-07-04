package com.khaata.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.khaata.app.data.model.AnalyticsSnapshot
import com.khaata.app.data.model.Budget
import com.khaata.app.data.model.BudgetProgress
import com.khaata.app.data.model.Expense
import com.khaata.app.data.model.Goal
import com.khaata.app.data.model.MonthSummary
import com.khaata.app.data.model.buildBudgetProgress
import com.khaata.app.data.model.computeStats
import com.khaata.app.data.model.currentMonthKey
import com.khaata.app.data.model.monthKeyFromDate
import com.khaata.app.data.model.shiftMonth
import com.khaata.app.data.model.todayStr
import com.khaata.app.data.repository.FinanceRepository
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FinanceViewModel(private val repository: FinanceRepository) : ViewModel() {

    private val _viewedMonthKey = MutableStateFlow(currentMonthKey())
    val viewedMonthKey: StateFlow<String> = _viewedMonthKey

    @OptIn(ExperimentalCoroutinesApi::class)
    val monthSummary: StateFlow<MonthSummary> = _viewedMonthKey
        .flatMapLatest { key -> repository.observeMonthSummary(key) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthSummary())

    @OptIn(ExperimentalCoroutinesApi::class)
    val expenses: StateFlow<List<Expense>> = _viewedMonthKey
        .flatMapLatest { key -> repository.observeExpenses(key) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMonths: StateFlow<List<MonthSummary>> = repository.observeAllMonths()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val analytics: StateFlow<AnalyticsSnapshot> = allMonths
        .mapLatest { months -> repository.buildAnalyticsSnapshot(months) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsSnapshot())

    val goals: StateFlow<List<Goal>> = repository.observeGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val budgets: StateFlow<List<Budget>> = _viewedMonthKey
        .flatMapLatest { key -> repository.observeBudgets(key) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val budgetProgress: StateFlow<List<BudgetProgress>> = _viewedMonthKey
        .flatMapLatest { key ->
            combine(repository.observeBudgets(key), repository.observeExpenses(key)) { budgets, expenses ->
                val spentByCategory = expenses.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }
                budgets.map { budget ->
                    com.khaata.app.data.model.buildBudgetProgress(budget, spentByCategory[budget.category] ?: 0.0)
                }.sortedByDescending { it.pct }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun goToMonth(delta: Int) {
        _viewedMonthKey.value = shiftMonth(_viewedMonthKey.value, delta)
    }

    fun jumpToMonth(key: String) {
        _viewedMonthKey.value = key
    }

    fun updateIncome(amount: Double) = viewModelScope.launch {
        repository.setIncome(_viewedMonthKey.value, amount)
    }

    fun addExpense(category: String, amount: Double, note: String, date: String) = viewModelScope.launch {
        repository.addExpense(
            monthKeyFromDate(date),
            Expense(category = category, amount = amount, note = note, date = date)
        )
    }

    fun deleteExpense(expense: Expense) = viewModelScope.launch {
        repository.deleteExpense(monthKeyFromDate(expense.date), expense.id, expense.amount)
    }

    fun addGoal(name: String, targetAmount: Double, targetDate: String) = viewModelScope.launch {
        if (validateGoalTarget(null, targetAmount, targetDate) != null) return@launch
        repository.addGoal(Goal(name = name, targetAmount = targetAmount, targetDate = targetDate, createdAt = todayStr()))
    }

    fun updateGoalTarget(goalId: String, targetAmount: Double, targetDate: String) = viewModelScope.launch {
        if (validateGoalTarget(goalId, targetAmount, targetDate) != null) return@launch
        repository.updateGoalTarget(goalId, targetAmount, targetDate)
    }

    fun deleteGoal(goalId: String) = viewModelScope.launch {
        repository.deleteGoal(goalId)
    }

    fun logContribution(goalId: String, amount: Double, date: String) = viewModelScope.launch {
        repository.logContribution(goalId, monthKeyFromDate(date), amount, date)
    }

    fun setBudget(category: String, limitAmount: Double) = viewModelScope.launch {
        if (_viewedMonthKey.value != currentMonthKey()) return@launch
        if (validateBudgetLimit(category, limitAmount) != null) return@launch
        repository.setBudget(_viewedMonthKey.value, category, limitAmount)
    }

    fun deleteBudget(category: String) = viewModelScope.launch {
        if (_viewedMonthKey.value != currentMonthKey()) return@launch
        repository.deleteBudget(_viewedMonthKey.value, category)
    }

    /**
     * HARD checks only — reasons the entry itself is invalid. Whether it
     * happens to push the month's totals past income is NOT one of them
     * (see budgetAllocationWarning below) — that's normal, expected budget
     * pressure, not an error.
     */
    fun validateBudgetLimit(category: String, limitAmount: Double): String? {
        if (_viewedMonthKey.value != currentMonthKey()) {
            return "Budgets can be changed only for the current month."
        }
        if (limitAmount <= 0.0) return "Budget amount must be greater than zero."
        return null
    }

    /**
     * Soft, informational check. A goal's "need per month" grows on its own
     * as its target date gets closer, so budgets + goals can drift past
     * income purely with the passage of time — not because of anything the
     * user just did. That should never trap the user from saving; it
     * should just tell them where they stand.
     *
     * Returns null when the new total fits inside this month's income,
     * otherwise a friendly heads-up describing the gap. Never blocks.
     */
    fun budgetAllocationWarning(category: String, limitAmount: Double): String? {
        val income = monthSummary.value.income
        val otherBudgetsTotal = budgets.value
            .filterNot { it.category == category }
            .sumOf { it.limitAmount }
        val newBudgetsTotal = otherBudgetsTotal + limitAmount
        val monthlyGoalNeed = requiredMonthlyGoalSavings(goals.value)
        val totalAllocated = newBudgetsTotal + monthlyGoalNeed
        val over = totalAllocated - income

        return if (over > 0.001) {
            "Heads up: budgets (₹${"%,.0f".format(newBudgetsTotal)}) plus this month's goal " +
                    "savings need (₹${"%,.0f".format(monthlyGoalNeed)}) add up to " +
                    "₹${"%,.0f".format(totalAllocated)} — about ₹${"%,.0f".format(over)} more than your " +
                    "₹${"%,.0f".format(income)} income. You can still save it; consider trimming a " +
                    "category or pushing a goal's date out if this doesn't feel doable."
        } else null
    }

    /**
     * HARD checks only for a goal — basic input sanity. Whether the goal's
     * required monthly saving is affordable given income is NOT a reason to
     * block saving (see goalAllocationWarning below).
     */
    fun validateGoalTarget(goalId: String?, targetAmount: Double, targetDate: String): String? {
        if (targetAmount <= 0.0) return "Goal target amount must be greater than zero."
        if (targetDate.isBlank()) return "Goal target date is required."
        if (runCatching { LocalDate.parse(targetDate) }.isFailure) {
            return "Goal target date must be a valid date."
        }
        return null
    }

    /**
     * Soft, informational check for goals — mirrors budgetAllocationWarning.
     * A goal that's tight (or even over) this month's income is a real
     * situation the app already has a name for ("behind schedule"), not an
     * invalid one — so it's surfaced as guidance, never as a blocker.
     */
    fun goalAllocationWarning(goalId: String?, targetAmount: Double, targetDate: String): String? {
        if (targetAmount <= 0.0 || targetDate.isBlank()) return null
        if (runCatching { LocalDate.parse(targetDate) }.isFailure) return null

        val income = monthSummary.value.income
        val proposedGoal = goals.value.firstOrNull { it.id == goalId }
            ?.copy(targetAmount = targetAmount, targetDate = targetDate)
            ?: Goal(
                id = goalId ?: "",
                name = "",
                targetAmount = targetAmount,
                targetDate = targetDate,
                createdAt = todayStr()
            )

        val updatedGoals = if (goalId != null && goals.value.any { it.id == goalId }) {
            goals.value.map { if (it.id == goalId) proposedGoal else it }
        } else {
            goals.value + proposedGoal
        }

        val budgetsTotal = budgets.value.sumOf { it.limitAmount }
        val newMonthlyGoalNeed = requiredMonthlyGoalSavings(updatedGoals)
        val totalAllocated = budgetsTotal + newMonthlyGoalNeed
        val over = totalAllocated - income

        return if (over > 0.001) {
            "Heads up: all your goals together now need ₹${"%,.0f".format(newMonthlyGoalNeed)}/month, " +
                    "and with budgets (₹${"%,.0f".format(budgetsTotal)}) that's " +
                    "₹${"%,.0f".format(totalAllocated)} — about ₹${"%,.0f".format(over)} more than your " +
                    "₹${"%,.0f".format(income)} income. You can still save this goal — it'll show as " +
                    "\"behind\" and you can catch up later or push the target date out."
        } else null
    }

    private fun requiredMonthlyGoalSavings(goalList: List<Goal>): Double {
        val monthKey = currentMonthKey()
        return goalList.sumOf { it.computeStats(monthKey).requiredMonthly }
    }
}

class FinanceViewModelFactory(private val repository: FinanceRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FinanceViewModel(repository) as T
    }
}