package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.repository.GoalRepository
import com.fiatlife.app.domain.model.FinancialGoal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GoalsState(
    val goals: List<FinancialGoal> = emptyList(),
    val totalTarget: Double = 0.0,
    val totalSaved: Double = 0.0,
    val overallProgress: Double = 0.0,
    val showAddDialog: Boolean = false,
    val editingGoal: FinancialGoal? = null,
    val showUpdateProgressDialog: Boolean = false,
    val updatingGoal: FinancialGoal? = null,
    val isSaving: Boolean = false,
    val message: String = ""
)

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val repository: GoalRepository
) : ViewModel() {

    private val _state = MutableStateFlow(GoalsState())
    val state: StateFlow<GoalsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllGoals().collect { goals ->
                val totalTarget = goals.sumOf { it.targetAmount }
                val totalSaved = goals.sumOf { it.currentAmount }
                val overallProgress = if (totalTarget > 0) totalSaved / totalTarget * 100 else 0.0

                _state.update {
                    it.copy(
                        goals = goals,
                        totalTarget = totalTarget,
                        totalSaved = totalSaved,
                        overallProgress = overallProgress
                    )
                }
            }
        }
    }

    fun showAddGoal() {
        _state.update { it.copy(showAddDialog = true, editingGoal = null) }
    }

    fun showEditGoal(goal: FinancialGoal) {
        _state.update { it.copy(showAddDialog = true, editingGoal = goal) }
    }

    fun dismissDialog() {
        _state.update { it.copy(showAddDialog = false, editingGoal = null) }
    }

    fun showUpdateProgress(goal: FinancialGoal) {
        _state.update { it.copy(showUpdateProgressDialog = true, updatingGoal = goal) }
    }

    fun dismissUpdateProgress() {
        _state.update { it.copy(showUpdateProgressDialog = false, updatingGoal = null) }
    }

    fun saveGoal(goal: FinancialGoal) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                repository.saveGoal(goal)
                _state.update {
                    it.copy(isSaving = false, showAddDialog = false, editingGoal = null)
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, message = "Error: ${e.message}") }
            }
        }
    }

    fun updateProgress(goalId: String, newAmount: Double) {
        viewModelScope.launch {
            try {
                repository.updateGoalProgress(goalId, newAmount)
                _state.update {
                    it.copy(showUpdateProgressDialog = false, updatingGoal = null)
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = "Error: ${e.message}") }
            }
        }
    }

    fun deleteGoal(goal: FinancialGoal) {
        viewModelScope.launch {
            repository.deleteGoal(goal)
        }
    }
}
