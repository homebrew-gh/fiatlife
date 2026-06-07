package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.repository.GoalRepository
import com.fiatlife.app.data.repository.stateWhileSubscribed
import com.fiatlife.app.domain.model.FinancialGoal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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

private data class GoalsComputed(
    val goals: List<FinancialGoal> = emptyList(),
    val totalTarget: Double = 0.0,
    val totalSaved: Double = 0.0,
    val overallProgress: Double = 0.0
)

private data class GoalsUiOverlay(
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

    private val uiOverlay = MutableStateFlow(GoalsUiOverlay())

    val state: StateFlow<GoalsState> = combine(
        repository.getAllGoals()
            .map { goals ->
                val totalTarget = goals.sumOf { it.targetAmount }
                val totalSaved = goals.sumOf { it.currentAmount }
                val overallProgress = if (totalTarget > 0) totalSaved / totalTarget * 100 else 0.0
                GoalsComputed(goals, totalTarget, totalSaved, overallProgress)
            }
            .flowOn(Dispatchers.Default)
            .distinctUntilChanged(),
        uiOverlay
    ) { computed, ui ->
        GoalsState(
            goals = computed.goals,
            totalTarget = computed.totalTarget,
            totalSaved = computed.totalSaved,
            overallProgress = computed.overallProgress,
            showAddDialog = ui.showAddDialog,
            editingGoal = ui.editingGoal,
            showUpdateProgressDialog = ui.showUpdateProgressDialog,
            updatingGoal = ui.updatingGoal,
            isSaving = ui.isSaving,
            message = ui.message
        )
    }.stateWhileSubscribed(viewModelScope, GoalsState())

    fun showAddGoal() {
        uiOverlay.update { it.copy(showAddDialog = true, editingGoal = null) }
    }

    fun showEditGoal(goal: FinancialGoal) {
        uiOverlay.update { it.copy(showAddDialog = true, editingGoal = goal) }
    }

    fun dismissDialog() {
        uiOverlay.update { it.copy(showAddDialog = false, editingGoal = null) }
    }

    fun showUpdateProgress(goal: FinancialGoal) {
        uiOverlay.update { it.copy(showUpdateProgressDialog = true, updatingGoal = goal) }
    }

    fun dismissUpdateProgress() {
        uiOverlay.update { it.copy(showUpdateProgressDialog = false, updatingGoal = null) }
    }

    fun saveGoal(goal: FinancialGoal) {
        viewModelScope.launch {
            uiOverlay.update { it.copy(isSaving = true) }
            try {
                repository.saveGoal(goal)
                uiOverlay.update {
                    it.copy(isSaving = false, showAddDialog = false, editingGoal = null)
                }
            } catch (e: Exception) {
                uiOverlay.update { it.copy(isSaving = false, message = "Error: ${e.message}") }
            }
        }
    }

    fun updateProgress(goalId: String, newAmount: Double) {
        viewModelScope.launch {
            try {
                repository.updateGoalProgress(goalId, newAmount)
                uiOverlay.update {
                    it.copy(showUpdateProgressDialog = false, updatingGoal = null)
                }
            } catch (e: Exception) {
                uiOverlay.update { it.copy(message = "Error: ${e.message}") }
            }
        }
    }

    fun deleteGoal(goal: FinancialGoal) {
        viewModelScope.launch {
            repository.deleteGoal(goal)
        }
    }
}
