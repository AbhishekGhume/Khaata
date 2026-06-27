package com.khaata.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.khaata.app.data.model.AnalyticsSnapshot
import com.khaata.app.data.model.Expense
import com.khaata.app.data.model.Goal
import com.khaata.app.data.model.MonthSummary
import com.khaata.app.data.model.currentMonthKey
import com.khaata.app.data.model.monthKeyFromDate
import com.khaata.app.data.model.shiftMonth
import com.khaata.app.data.model.todayStr
import com.khaata.app.data.repository.FinanceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
        repository.addGoal(Goal(name = name, targetAmount = targetAmount, targetDate = targetDate, createdAt = todayStr()))
    }

    fun deleteGoal(goalId: String) = viewModelScope.launch {
        repository.deleteGoal(goalId)
    }

    fun logContribution(goalId: String, amount: Double, date: String) = viewModelScope.launch {
        repository.logContribution(goalId, monthKeyFromDate(date), amount, date)
    }
}

class FinanceViewModelFactory(private val repository: FinanceRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FinanceViewModel(repository) as T
    }
}
