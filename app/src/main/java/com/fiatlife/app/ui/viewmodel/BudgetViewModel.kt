package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.repository.BillRepository
import com.fiatlife.app.data.repository.BudgetRepository
import com.fiatlife.app.data.repository.CypherLogSubscriptionRepository
import com.fiatlife.app.data.repository.SalaryRepository
import com.fiatlife.app.data.repository.stateWhileSubscribed
import com.fiatlife.app.domain.model.Bill
import com.fiatlife.app.domain.model.BillGeneralCategory
import com.fiatlife.app.domain.model.BudgetCategoryKind
import com.fiatlife.app.domain.model.BudgetConfig
import com.fiatlife.app.domain.model.BudgetSummary
import com.fiatlife.app.domain.model.SalaryConfig
import com.fiatlife.app.domain.model.SalarySummary
import com.fiatlife.app.domain.model.computeBudgetSummary
import com.fiatlife.app.domain.model.defaultBudgetConfig
import com.fiatlife.app.domain.model.rollBudgetPeriod
import com.fiatlife.app.domain.model.setCategoryBudget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BudgetUiState(
    val config: BudgetConfig = defaultBudgetConfig(),
    val summary: BudgetSummary = BudgetSummary(),
    val hasSalary: Boolean = false,
    val hasData: Boolean = false,
    val isSaving: Boolean = false
)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val billRepository: BillRepository,
    private val cypherLogSubscriptionRepository: CypherLogSubscriptionRepository,
    private val salaryRepository: SalaryRepository
) : ViewModel() {

    private val monthAnchor = MutableStateFlow(System.currentTimeMillis())
    private val localConfig = MutableStateFlow(defaultBudgetConfig())
    private val hasLocalEdits = MutableStateFlow(false)
    private val isSaving = MutableStateFlow(false)
    private var persistJob: Job? = null

    companion object {
        private const val AUTO_SAVE_MS = 500L
    }

    init {
        MonthAnchor.startUpdates(viewModelScope, monthAnchor)
        // Seed the working copy from stored/relay data, superseding stale local edits.
        budgetRepository.getBudgetConfig()
            .onEach { repoConfig ->
                if (repoConfig == null) return@onEach
                val rolled = rollBudgetPeriod(repoConfig)
                val local = localConfig.value
                val repoIsNewer = repoConfig.updatedAt > local.updatedAt
                if (!hasLocalEdits.value || repoIsNewer) {
                    if (hasLocalEdits.value && repoIsNewer) {
                        hasLocalEdits.value = false
                        persistJob?.cancel()
                    }
                    localConfig.value = rolled
                }
            }
            .launchIn(viewModelScope)
    }

    private val billsFlow = combine(
        billRepository.getAllBills(),
        cypherLogSubscriptionRepository.getAllAsBills()
    ) { native, cypherLog ->
        (native + cypherLog.map { it.bill }).filterNot { it.isCancelled }
    }

    val state: StateFlow<BudgetUiState> = run {
        val inputs = combine(
            localConfig,
            billsFlow,
            salaryRepository.getSalaryConfig(),
            monthAnchor,
            isSaving
        ) { config, bills, salary, anchor, saving ->
            buildState(config, bills, salary, anchor, saving)
        }
        inputs
            .flowOn(Dispatchers.Default)
            .distinctUntilChanged()
            .stateWhileSubscribed(viewModelScope, BudgetUiState())
    }

    fun updateTarget(key: String, kind: BudgetCategoryKind, target: Double) {
        edit { setCategoryBudget(it, key, kind, target = target) }
    }

    fun updateSpent(key: String, manualSpent: Double) {
        edit { setCategoryBudget(it, key, BudgetCategoryKind.VARIABLE, manualSpent = manualSpent) }
    }

    private fun edit(update: (BudgetConfig) -> BudgetConfig) {
        hasLocalEdits.value = true
        localConfig.update { update(it) }
        schedulePersist()
    }

    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(AUTO_SAVE_MS)
            persist()
        }
    }

    private suspend fun persist() {
        isSaving.update { true }
        try {
            budgetRepository.saveBudgetConfig(localConfig.value)
            hasLocalEdits.value = false
        } finally {
            isSaving.update { false }
        }
    }
}

private fun buildState(
    config: BudgetConfig,
    bills: List<Bill>,
    salary: SalaryConfig?,
    monthAnchor: Long,
    saving: Boolean
): BudgetUiState {
    val billCategoryTotals: Map<BillGeneralCategory, Double> =
        bills.groupBy { it.effectiveGeneralCategory }
            .mapValues { (_, list) -> list.sumOf { b -> b.dueAmountInMonth(monthAnchor) } }

    val takeHome = salary?.let {
        SalarySummary.computeMonthlyTakeHome(it, monthAnchor).totalTakeHome
    } ?: 0.0

    val summary = computeBudgetSummary(config, billCategoryTotals, takeHome)
    val hasSalary = takeHome > 0.0
    val hasData = hasSalary ||
        summary.billRows.isNotEmpty() ||
        summary.totalTarget > 0.0 ||
        summary.totalVariableActual > 0.0

    return BudgetUiState(
        config = config,
        summary = summary,
        hasSalary = hasSalary,
        hasData = hasData,
        isSaving = saving
    )
}
