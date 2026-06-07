package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.data.repository.SalaryRepository
import com.fiatlife.app.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class SalaryTab { SUMMARY, PAYCHECK, ANNUAL }

data class SalaryState(
    val config: SalaryConfig = SalaryConfig(),
    val calculation: PaycheckCalculation = PaycheckCalculation(),
    val activeTab: SalaryTab = SalaryTab.SUMMARY,
    val annualOvertimeHours: Double = 0.0,
    val annualProjection: AnnualProjection = AnnualProjection(),
    val annualBaseProjection: AnnualProjection = AnnualProjection(),
    val summaryYear: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
    val ytdSummary: YtdSummary? = null,
    val showLogDialog: Boolean = false,
    val editingLog: PaycheckLogEntry? = null,
    val isEditing: Boolean = false,
    val showDeductionDialog: Boolean = false,
    val showDepositDialog: Boolean = false,
    val editingDeduction: Deduction? = null,
    val editingDeposit: DirectDeposit? = null,
    val isPreTaxDeduction: Boolean = true,
    val isSaving: Boolean = false,
    val message: String = ""
)

@HiltViewModel
class SalaryViewModel @Inject constructor(
    private val repository: SalaryRepository,
    private val nostrClient: NostrClient
) : ViewModel() {

    private val _state = MutableStateFlow(SalaryState())
    val state: StateFlow<SalaryState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getSalaryConfig().collect { config ->
                config?.let {
                    _state.update { state ->
                        recalcSummary(
                            state.copy(
                                config = it,
                                calculation = PaycheckCalculator.calculate(it)
                            )
                        )
                    }
                }
            }
        }
    }

    fun setActiveTab(tab: SalaryTab) {
        _state.update { state ->
            val newState = state.copy(activeTab = tab)
            when (tab) {
                SalaryTab.ANNUAL -> recalcAnnual(newState)
                SalaryTab.SUMMARY -> recalcSummary(newState)
                else -> newState
            }
        }
    }

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
            state.summaryYear
        )
        return state.copy(ytdSummary = summary)
    }

    fun setSummaryYear(year: Int) {
        _state.update { recalcSummary(it.copy(summaryYear = year)) }
    }

    fun updatePayType(type: PayType) {
        updateConfig { it.copy(payType = type) }
    }

    fun updateAnnualSalary(amount: Double) {
        updateConfig { it.copy(annualSalary = amount) }
    }

    fun showLogPaycheck(entry: PaycheckLogEntry?) {
        _state.update { it.copy(showLogDialog = true, editingLog = entry) }
    }

    fun dismissLogDialog() {
        _state.update { it.copy(showLogDialog = false, editingLog = null) }
    }

    fun saveLogPaycheck(entry: PaycheckLogEntry) {
        val e = if (entry.id.isEmpty()) entry.copy(id = UUID.randomUUID().toString()) else entry
        updateConfig { config ->
            val updated = config.paycheckLog.toMutableList()
            val idx = updated.indexOfFirst { it.id == e.id }
            if (idx >= 0) updated[idx] = e else updated.add(e)
            config.copy(paycheckLog = updated)
        }
        dismissLogDialog()
        save()
    }

    fun removeLogPaycheck(id: String) {
        updateConfig { config ->
            config.copy(paycheckLog = config.paycheckLog.filter { it.id != id })
        }
        save()
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
        _state.update { state ->
            recalcAnnual(state.copy(annualOvertimeHours = hours))
        }
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
        _state.update {
            it.copy(
                showDeductionDialog = true,
                isPreTaxDeduction = isPreTax,
                editingDeduction = null
            )
        }
    }

    fun showEditDeduction(deduction: Deduction, isPreTax: Boolean) {
        _state.update {
            it.copy(
                showDeductionDialog = true,
                isPreTaxDeduction = isPreTax,
                editingDeduction = deduction
            )
        }
    }

    fun dismissDeductionDialog() {
        _state.update { it.copy(showDeductionDialog = false, editingDeduction = null) }
    }

    fun saveDeduction(deduction: Deduction) {
        val isPreTax = _state.value.isPreTaxDeduction
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

    fun showAddDeposit() {
        _state.update { it.copy(showDepositDialog = true, editingDeposit = null) }
    }

    fun showEditDeposit(deposit: DirectDeposit) {
        _state.update { it.copy(showDepositDialog = true, editingDeposit = deposit) }
    }

    fun dismissDepositDialog() {
        _state.update { it.copy(showDepositDialog = false, editingDeposit = null) }
    }

    fun saveDeposit(deposit: DirectDeposit) {
        val d = if (deposit.id.isEmpty()) deposit.copy(id = UUID.randomUUID().toString()) else deposit

        updateConfig { config ->
            val updated = config.directDeposits.toMutableList()
            val idx = updated.indexOfFirst { it.id == d.id }
            if (idx >= 0) updated[idx] = d else updated.add(d)
            config.copy(directDeposits = updated)
        }
        dismissDepositDialog()
    }

    fun removeDeposit(depositId: String) {
        updateConfig { config ->
            config.copy(directDeposits = config.directDeposits.filter { it.id != depositId })
        }
    }

    fun save() {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                repository.saveSalaryConfig(_state.value.config)
                _state.update { it.copy(isSaving = false, message = "Configuration saved") }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, message = "Error: ${e.message}") }
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = "") }
    }

    private fun updateConfig(update: (SalaryConfig) -> SalaryConfig) {
        _state.update { state ->
            val newConfig = update(state.config)
            val newState = state.copy(
                config = newConfig,
                calculation = PaycheckCalculator.calculate(newConfig)
            )
            when (state.activeTab) {
                SalaryTab.ANNUAL -> recalcAnnual(newState)
                SalaryTab.SUMMARY -> recalcSummary(newState)
                else -> newState
            }
        }
    }
}
