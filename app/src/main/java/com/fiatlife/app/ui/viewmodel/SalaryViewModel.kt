package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.data.repository.SalaryRepository
import com.fiatlife.app.data.repository.stateWhileSubscribed
import com.fiatlife.app.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class SalaryTab { SUMMARY, WHATIF }

enum class DepositEditTarget { SUMMARY, WHATIF }

data class SalaryState(
    val config: SalaryConfig = SalaryConfig(),
    val calculation: PaycheckCalculation = PaycheckCalculation(),
    val activeTab: SalaryTab = SalaryTab.SUMMARY,
    val annualOvertimeHours: Double = 0.0,
    val projectOvertimeForward: Boolean = true,
    val annualProjection: AnnualProjection = AnnualProjection(),
    val annualBaseProjection: AnnualProjection = AnnualProjection(),
    val summaryYear: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
    val ytdSummary: YtdSummary? = null,
    val showLogDialog: Boolean = false,
    val editingLog: PaycheckLogEntry? = null,
    val logPayDateHint: Long? = null,
    val isEditing: Boolean = false,
    val showDeductionDialog: Boolean = false,
    val showDepositDialog: Boolean = false,
    val editingDeduction: Deduction? = null,
    val editingDeposit: DirectDeposit? = null,
    val depositEditTarget: DepositEditTarget = DepositEditTarget.SUMMARY,
    val whatIfDirectDeposits: List<DirectDeposit>? = null,
    val isPreTaxDeduction: Boolean = true,
    val isSaving: Boolean = false,
    val message: String = ""
)

@HiltViewModel
class SalaryViewModel @Inject constructor(
    private val repository: SalaryRepository,
    private val nostrClient: NostrClient
) : ViewModel() {

    private val hasLocalEdits = MutableStateFlow(false)
    private val localState = MutableStateFlow(SalaryState())
    private var persistJob: Job? = null

    companion object {
        private const val AUTO_SAVE_MS = 500L
    }

    val state: StateFlow<SalaryState> = combine(
        repository.getSalaryConfig()
            .map { config ->
                config?.let { cfg -> cfg to PaycheckCalculator.calculate(cfg) }
            }
            .flowOn(Dispatchers.Default)
            .distinctUntilChanged(),
        localState,
        hasLocalEdits
    ) { repoPair, local, edits ->
        if (repoPair != null && !edits) {
            val (config, calculation) = repoPair
            recalcAll(local.copy(config = config, calculation = calculation))
        } else {
            local
        }
    }.stateWhileSubscribed(viewModelScope, SalaryState())

    fun setActiveTab(tab: SalaryTab) {
        localState.update { it.copy(activeTab = tab) }
    }

    private fun recalcAll(state: SalaryState): SalaryState =
        recalcSummary(recalcAnnual(state))

    private fun recalcSummary(state: SalaryState): SalaryState {
        val annual = PaycheckCalculator.calculateAnnual(
            state.config,
            state.annualOvertimeHours,
            state.summaryYear
        )
        val summary = SalarySummary.summarize(
            state.config,
            state.calculation,
            annual,
            state.summaryYear,
            projectOvertimeForward = state.projectOvertimeForward
        )
        return state.copy(ytdSummary = summary)
    }

    fun setSummaryYear(year: Int) {
        hasLocalEdits.value = true
        localState.update { state ->
            val config = SalarySummary.applyInferredPayRatesToConfig(state.config, year)
            recalcSummary(
                state.copy(
                    summaryYear = year,
                    config = config,
                    calculation = PaycheckCalculator.calculate(config)
                )
            )
        }
        schedulePersist()
    }

    fun updatePayType(type: PayType) {
        updateConfig { it.copy(payType = type) }
    }

    fun updateAnnualSalary(amount: Double) {
        updateConfig { it.copy(annualSalary = amount) }
    }

    fun showLogPaycheck(entry: PaycheckLogEntry? = null, payDateHint: Long? = null) {
        localState.update {
            it.copy(
                showLogDialog = true,
                editingLog = entry,
                logPayDateHint = if (entry == null) payDateHint else null
            )
        }
    }

    fun dismissLogDialog() {
        localState.update { it.copy(showLogDialog = false, editingLog = null, logPayDateHint = null) }
    }

    fun generateMissingPaycheckLogs(year: Int) {
        updateConfig { config ->
            val entries = SalarySummary.generatePaycheckLogsForMissingDates(config, year)
            if (entries.isEmpty()) config
            else SalarySummary.applyInferredPayRatesForAllLogYears(
                config.copy(paycheckLog = config.paycheckLog + entries)
            )
        }
    }

    fun saveLogPaycheck(entry: PaycheckLogEntry) {
        val e = (if (entry.id.isEmpty()) entry.copy(id = UUID.randomUUID().toString()) else entry)
            .copy(autoGenerated = null)
        updateConfig { config ->
            val updated = config.paycheckLog.toMutableList()
            val idx = updated.indexOfFirst { it.id == e.id }
            if (idx >= 0) updated[idx] = e else updated.add(e)
            SalarySummary.applyInferredPayRatesForAllLogYears(
                config.copy(paycheckLog = updated)
            )
        }
        dismissLogDialog()
        flushPersist()
    }

    fun removeLogPaycheck(id: String) {
        updateConfig { config ->
            SalarySummary.applyInferredPayRatesForAllLogYears(
                config.copy(paycheckLog = config.paycheckLog.filter { it.id != id })
            )
        }
        flushPersist()
    }

    fun addRaise() {
        updateConfig { config ->
            config.copy(
                payRateHistory = config.payRateHistory + PayRateChange(
                    id = UUID.randomUUID().toString(),
                    effectiveDate = System.currentTimeMillis(),
                    payType = config.payType,
                    hourlyRate = if (config.payType == PayType.HOURLY) config.hourlyRate else null,
                    annualSalary = if (config.payType == PayType.SALARY) config.annualSalary else null
                )
            )
        }
    }

    fun updateRaise(change: PayRateChange) {
        updateConfig { config ->
            config.copy(payRateHistory = config.payRateHistory.map {
                if (it.id == change.id) change else it
            })
        }
    }

    fun removeRaise(id: String) {
        updateConfig { config ->
            config.copy(payRateHistory = config.payRateHistory.filter { it.id != id })
        }
    }

    fun updateAnnualOvertimeHours(hours: Double) {
        localState.update { state ->
            recalcAll(state.copy(annualOvertimeHours = hours))
        }
    }

    fun setProjectOvertimeForward(enabled: Boolean) {
        localState.update { recalcAll(it.copy(projectOvertimeForward = enabled)) }
    }

    private fun recalcAnnual(state: SalaryState): SalaryState {
        val withOT = PaycheckCalculator.calculateAnnual(state.config, state.annualOvertimeHours)
        val baseline = PaycheckCalculator.calculateAnnual(state.config, 0.0)
        return state.copy(annualProjection = withOT, annualBaseProjection = baseline)
    }

    fun updateHourlyRate(rate: Double) {
        updateConfig { it.copy(hourlyRate = rate) }
    }

    fun updateStandardHours(hours: Double) {
        updateConfig { it.copy(standardHoursPerPeriod = hours) }
    }

    fun updateOvertimeHours(hours: Double) {
        updateConfig { it.copy(overtimeHours = hours) }
    }

    fun updateOvertimeMultiplier(multiplier: Double) {
        updateConfig { it.copy(overtimeMultiplier = multiplier) }
    }

    fun updatePayFrequency(frequency: PayFrequency) {
        updateConfig { it.copy(payFrequency = frequency) }
    }

    fun updateFirstPaydayOfYear(firstPaydayMillis: Long?) {
        updateConfig { it.copy(firstPaydayOfYearMillis = firstPaydayMillis) }
    }

    fun updateFilingStatus(status: FilingStatus) {
        updateConfig { it.copy(filingStatus = status) }
    }

    fun updateState(state: String) {
        updateConfig { it.copy(state = state) }
    }

    fun updateCounty(county: String) {
        updateConfig { it.copy(county = county) }
    }

    fun updateCustomFederalTaxRate(rate: Double?) {
        updateConfig { it.copy(taxOverrides = it.taxOverrides.copy(customFederalTaxRate = rate)) }
    }

    fun updateCustomStateTaxRate(rate: Double?) {
        updateConfig { it.copy(taxOverrides = it.taxOverrides.copy(customStateTaxRate = rate)) }
    }

    fun updateCustomCountyTaxRate(rate: Double?) {
        updateConfig { it.copy(taxOverrides = it.taxOverrides.copy(customCountyTaxRate = rate)) }
    }

    fun updateCustomSocialSecurityRate(rate: Double?) {
        updateConfig { it.copy(taxOverrides = it.taxOverrides.copy(customSocialSecurityRate = rate)) }
    }

    fun updateCustomMedicareRate(rate: Double?) {
        updateConfig { it.copy(taxOverrides = it.taxOverrides.copy(customMedicareRate = rate)) }
    }

    fun showAddDeduction(isPreTax: Boolean) {
        localState.update {
            it.copy(
                showDeductionDialog = true,
                isPreTaxDeduction = isPreTax,
                editingDeduction = null
            )
        }
    }

    fun showEditDeduction(deduction: Deduction, isPreTax: Boolean) {
        localState.update {
            it.copy(
                showDeductionDialog = true,
                isPreTaxDeduction = isPreTax,
                editingDeduction = deduction
            )
        }
    }

    fun dismissDeductionDialog() {
        localState.update { it.copy(showDeductionDialog = false, editingDeduction = null) }
    }

    fun saveDeduction(deduction: Deduction) {
        val isPreTax = localState.value.isPreTaxDeduction
        val d = if (deduction.id.isEmpty()) deduction.copy(id = UUID.randomUUID().toString()) else deduction

        updateConfig { config ->
            if (isPreTax) {
                val updated = config.preTaxDeductions.toMutableList()
                val idx = updated.indexOfFirst { it.id == d.id }
                if (idx >= 0) updated[idx] = d else updated.add(d)
                config.copy(preTaxDeductions = updated)
            } else {
                val updated = config.postTaxDeductions.toMutableList()
                val idx = updated.indexOfFirst { it.id == d.id }
                if (idx >= 0) updated[idx] = d else updated.add(d)
                config.copy(postTaxDeductions = updated)
            }
        }
        dismissDeductionDialog()
    }

    fun removeDeduction(deductionId: String, isPreTax: Boolean) {
        updateConfig { config ->
            if (isPreTax) {
                config.copy(preTaxDeductions = config.preTaxDeductions.filter { it.id != deductionId })
            } else {
                config.copy(postTaxDeductions = config.postTaxDeductions.filter { it.id != deductionId })
            }
        }
    }

    fun showAddDeposit(target: DepositEditTarget = DepositEditTarget.SUMMARY) {
        localState.update {
            it.copy(
                showDepositDialog = true,
                editingDeposit = null,
                depositEditTarget = target
            )
        }
    }

    fun showEditDeposit(
        deposit: DirectDeposit,
        target: DepositEditTarget = DepositEditTarget.SUMMARY
    ) {
        localState.update {
            it.copy(
                showDepositDialog = true,
                editingDeposit = deposit,
                depositEditTarget = target
            )
        }
    }

    fun dismissDepositDialog() {
        localState.update {
            it.copy(
                showDepositDialog = false,
                editingDeposit = null,
                depositEditTarget = DepositEditTarget.SUMMARY
            )
        }
    }

    private fun effectiveWhatIfDeposits(state: SalaryState): List<DirectDeposit> =
        state.whatIfDirectDeposits ?: state.config.directDeposits

    private fun updateWhatIfDeposits(
        state: SalaryState,
        updater: (List<DirectDeposit>) -> List<DirectDeposit>
    ): SalaryState {
        val base = effectiveWhatIfDeposits(state)
        return state.copy(whatIfDirectDeposits = updater(base))
    }

    fun saveDeposit(deposit: DirectDeposit) {
        val d = if (deposit.id.isEmpty()) deposit.copy(id = UUID.randomUUID().toString()) else deposit

        when (localState.value.depositEditTarget) {
            DepositEditTarget.SUMMARY -> updateConfig { config ->
                val updated = config.directDeposits.toMutableList()
                val idx = updated.indexOfFirst { it.id == d.id }
                if (idx >= 0) updated[idx] = d else updated.add(d)
                config.copy(directDeposits = updated)
            }
            DepositEditTarget.WHATIF -> {
                hasLocalEdits.value = true
                localState.update { state ->
                    updateWhatIfDeposits(state) { deposits ->
                        val updated = deposits.toMutableList()
                        val idx = updated.indexOfFirst { it.id == d.id }
                        if (idx >= 0) updated[idx] = d else updated.add(d)
                        updated
                    }
                }
            }
        }
        dismissDepositDialog()
    }

    fun removeDeposit(
        depositId: String,
        target: DepositEditTarget = DepositEditTarget.SUMMARY
    ) {
        when (target) {
            DepositEditTarget.SUMMARY -> updateConfig { config ->
                config.copy(directDeposits = config.directDeposits.filter { it.id != depositId })
            }
            DepositEditTarget.WHATIF -> {
                hasLocalEdits.value = true
                localState.update { state ->
                    updateWhatIfDeposits(state) { deposits ->
                        deposits.filter { it.id != depositId }
                    }
                }
            }
        }
    }

    fun resetWhatIfDirectDeposits() {
        localState.update { it.copy(whatIfDirectDeposits = null) }
    }

    fun clearMessage() {
        localState.update { it.copy(message = "") }
    }

    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(AUTO_SAVE_MS)
            persistConfig()
        }
    }

    private fun flushPersist() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch { persistConfig() }
    }

    private suspend fun persistConfig() {
        localState.update { it.copy(isSaving = true) }
        try {
            repository.saveSalaryConfig(localState.value.config)
            hasLocalEdits.value = false
            localState.update { it.copy(isSaving = false) }
        } catch (e: Exception) {
            localState.update {
                it.copy(isSaving = false, message = "Error: ${e.message}")
            }
        }
    }

    private fun updateConfig(update: (SalaryConfig) -> SalaryConfig) {
        hasLocalEdits.value = true
        localState.update { state ->
            val newConfig = update(state.config)
            val newState = state.copy(
                config = newConfig,
                calculation = PaycheckCalculator.calculate(newConfig)
            )
            recalcAll(newState)
        }
        schedulePersist()
    }
}
