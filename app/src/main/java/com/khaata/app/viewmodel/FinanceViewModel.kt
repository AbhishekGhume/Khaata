package com.khaata.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.khaata.app.data.model.AnalyticsSnapshot
import com.khaata.app.data.model.Budget
import com.khaata.app.data.model.BudgetProgress
import androidx.compose.ui.graphics.Color
import com.khaata.app.data.model.Contribution
import com.khaata.app.data.model.Expense
import com.khaata.app.data.model.Goal
import com.khaata.app.data.model.MonthSummary
import com.khaata.app.data.model.RecurringExpense
import com.khaata.app.util.CategoryMeta
import com.khaata.app.util.DEFAULT_CATEGORIES
import com.khaata.app.data.model.buildBudgetProgress
import com.khaata.app.data.model.computeStats
import com.khaata.app.data.model.currentMonthKey
import com.khaata.app.data.model.monthKeyFromDate
import com.khaata.app.data.model.shiftMonth
import com.khaata.app.data.model.todayStr
import com.khaata.app.data.repository.FinanceRepository
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A transient, one-shot message for the UI to show in a Snackbar. [actionLabel] +
 * [onAction] drive the optional "Undo" affordance on destructive actions.
 */
data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

class FinanceViewModel(private val repository: FinanceRepository) : ViewModel() {

    // One-shot messages (write failures, "deleted — Undo", etc.). Buffered so an
    // emit from a background coroutine never suspends or drops silently.
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private fun postMessage(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        _messages.tryEmit(UiMessage(text, actionLabel, onAction))
    }

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

    val categories: StateFlow<List<CategoryMeta>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_CATEGORIES)

    val recurring: StateFlow<List<RecurringExpense>> = repository.observeRecurring()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All-time expenses for the Search screen — refreshed on demand (not a live listener).
    private val _allExpenses = MutableStateFlow<List<Expense>>(emptyList())
    val allExpenses: StateFlow<List<Expense>> = _allExpenses
    private val _allExpensesLoading = MutableStateFlow(false)
    val allExpensesLoading: StateFlow<Boolean> = _allExpensesLoading

    init {
        // Seed built-in categories once, then auto-post any recurring templates due
        // for the actual current calendar month. Both are idempotent.
        viewModelScope.launch {
            runCatching { repository.ensureCategoriesSeeded() }
            runCatching { repository.postDueRecurring(currentMonthKey()) }
        }
    }

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
        runCatching { repository.setIncome(_viewedMonthKey.value, amount) }
            .onFailure { postMessage("Couldn't update income — check your connection and try again.") }
    }

    fun addExpense(category: String, amount: Double, note: String, date: String) = viewModelScope.launch {
        runCatching {
            repository.addExpense(
                monthKeyFromDate(date),
                Expense(category = category, amount = amount, note = note, date = date)
            )
        }.onFailure { postMessage("Couldn't add the entry — nothing was saved.") }
    }

    fun updateExpense(original: Expense, category: String, amount: Double, note: String, date: String) = viewModelScope.launch {
        runCatching {
            repository.updateExpense(
                oldMonthKey = monthKeyFromDate(original.date),
                expenseId = original.id,
                oldAmount = original.amount,
                updated = Expense(id = original.id, category = category, amount = amount, note = note, date = date)
            )
        }.onFailure { postMessage("Couldn't save the changes — the entry is unchanged.") }
    }

    fun deleteExpense(expense: Expense) = viewModelScope.launch {
        runCatching { repository.deleteExpense(monthKeyFromDate(expense.date), expense.id, expense.amount) }
            .onSuccess {
                postMessage("Entry deleted.", actionLabel = "Undo") {
                    addExpense(expense.category, expense.amount, expense.note, expense.date)
                }
            }
            .onFailure { postMessage("Couldn't delete the entry.") }
    }

    fun addGoal(name: String, targetAmount: Double, targetDate: String) = viewModelScope.launch {
        if (validateGoalTarget(null, targetAmount, targetDate) != null) return@launch
        runCatching {
            repository.addGoal(Goal(name = name, targetAmount = targetAmount, targetDate = targetDate, createdAt = todayStr()))
        }.onFailure { postMessage("Couldn't create the goal — nothing was saved.") }
    }

    fun updateGoalTarget(goalId: String, targetAmount: Double, targetDate: String) = viewModelScope.launch {
        if (validateGoalTarget(goalId, targetAmount, targetDate) != null) return@launch
        runCatching { repository.updateGoalTarget(goalId, targetAmount, targetDate) }
            .onFailure { postMessage("Couldn't update the goal.") }
    }

    fun deleteGoal(goal: Goal) = viewModelScope.launch {
        // Capture the contribution log first so a delete can be fully undone.
        val contributions = runCatching { repository.getAllContributions(goal.id) }.getOrDefault(emptyList())
        runCatching { repository.deleteGoal(goal.id) }
            .onSuccess {
                postMessage("\"${goal.name}\" deleted.", actionLabel = "Undo") {
                    viewModelScope.launch {
                        runCatching { repository.restoreGoal(goal, contributions) }
                            .onFailure { postMessage("Couldn't restore the goal.") }
                    }
                }
            }
            .onFailure { postMessage("Couldn't delete the goal.") }
    }

    fun logContribution(goalId: String, amount: Double, date: String) = viewModelScope.launch {
        runCatching { repository.logContribution(goalId, monthKeyFromDate(date), amount, date) }
            .onFailure { postMessage("Couldn't log the contribution — nothing was saved.") }
    }

    /** Edits (or removes, when [newAmount] <= 0) the total saved for one month of a goal. */
    fun editMonthlyContribution(goalId: String, monthKey: String, oldAmount: Double, newAmount: Double) = viewModelScope.launch {
        runCatching { repository.editMonthlyContribution(goalId, monthKey, oldAmount, newAmount) }
            .onSuccess {
                if (newAmount <= 0.0) {
                    postMessage("Contribution removed.", actionLabel = "Undo") {
                        viewModelScope.launch {
                            runCatching { repository.editMonthlyContribution(goalId, monthKey, 0.0, oldAmount) }
                                .onFailure { postMessage("Couldn't restore the contribution.") }
                        }
                    }
                }
            }
            .onFailure { postMessage("Couldn't update the contribution.") }
    }

    fun setBudget(category: String, limitAmount: Double) = viewModelScope.launch {
        if (_viewedMonthKey.value != currentMonthKey()) return@launch
        if (validateBudgetLimit(category, limitAmount) != null) return@launch
        runCatching { repository.setBudget(_viewedMonthKey.value, category, limitAmount) }
            .onFailure { postMessage("Couldn't save the budget.") }
    }

    fun deleteBudget(category: String, limitAmount: Double) = viewModelScope.launch {
        if (_viewedMonthKey.value != currentMonthKey()) return@launch
        val monthKey = _viewedMonthKey.value
        runCatching { repository.deleteBudget(monthKey, category) }
            .onSuccess {
                postMessage("Budget removed.", actionLabel = "Undo") {
                    // Only restore if still on the same (current) month it was deleted from.
                    if (_viewedMonthKey.value == monthKey) setBudget(category, limitAmount)
                }
            }
            .onFailure { postMessage("Couldn't remove the budget.") }
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

    // ── Categories ──────────────────────────────────────────────────────────

    fun saveCategory(key: String, label: String, color: Color, iconKey: String) = viewModelScope.launch {
        // New categories append to the end of the current ordering.
        val order = categories.value.indexOfFirst { it.key == key }.let { if (it >= 0) it else categories.value.size }
        runCatching { repository.upsertCategory(key, label.trim(), color, iconKey, order) }
            .onFailure { postMessage("Couldn't save the category.") }
    }

    fun deleteCategory(key: String) = viewModelScope.launch {
        if (key == "other") {
            postMessage("\"Other\" is the fallback category and can't be deleted.")
            return@launch
        }
        runCatching { repository.deleteCategory(key) }
            .onSuccess { postMessage("Category deleted. Past entries and any recurring expenses using it now fall under \"Other\".") }
            .onFailure { postMessage("Couldn't delete the category.") }
    }

    // ── Recurring expenses ──────────────────────────────────────────────────

    fun addRecurring(category: String, amount: Double, note: String, dayOfMonth: Int) = viewModelScope.launch {
        runCatching {
            repository.addRecurring(
                RecurringExpense(
                    category = category, amount = amount, note = note.trim(),
                    dayOfMonth = dayOfMonth, active = true, createdAt = todayStr()
                )
            )
        }.onFailure { postMessage("Couldn't save the recurring expense.") }
    }

    fun updateRecurring(id: String, category: String, amount: Double, note: String, dayOfMonth: Int) = viewModelScope.launch {
        runCatching { repository.updateRecurring(id, category, amount, note.trim(), dayOfMonth) }
            .onFailure { postMessage("Couldn't update the recurring expense.") }
    }

    fun setRecurringActive(id: String, active: Boolean) = viewModelScope.launch {
        runCatching { repository.setRecurringActive(id, active) }
            .onFailure { postMessage("Couldn't change the recurring expense.") }
    }

    fun deleteRecurring(recurring: RecurringExpense) = viewModelScope.launch {
        runCatching { repository.deleteRecurring(recurring.id) }
            .onSuccess {
                postMessage("Recurring expense removed.", actionLabel = "Undo") {
                    addRecurring(recurring.category, recurring.amount, recurring.note, recurring.dayOfMonth)
                }
            }
            .onFailure { postMessage("Couldn't remove the recurring expense.") }
    }

    // ── All-time search ─────────────────────────────────────────────────────

    fun refreshAllExpenses() = viewModelScope.launch {
        _allExpensesLoading.value = true
        runCatching { repository.loadAllExpenses() }
            .onSuccess { _allExpenses.value = it }
            .onFailure { postMessage("Couldn't load expenses for search.") }
        _allExpensesLoading.value = false
    }
}

class FinanceViewModelFactory(private val repository: FinanceRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FinanceViewModel(repository) as T
    }
}