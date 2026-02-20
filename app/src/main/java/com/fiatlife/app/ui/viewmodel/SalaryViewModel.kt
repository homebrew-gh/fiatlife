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

enum class SalaryTab { PAYCHECK, ANNUAL }

data class SalaryState(
    val config: SalaryConfig = SalaryConfig(),
    val calculation: PaycheckCalculation = PaycheckCalculation(),
    val activeTab: SalaryTab = SalaryTab.PAYCHECK,
    val annualOvertimeHours: Double = 0.0,
    val annualProjection: AnnualProjection = AnnualProjection(),
    val annualBaseProjection: AnnualProjection = AnnualProjection(),
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
                        state.copy(
                            config = it,
                            calculation = PaycheckCalculator.calculate(it)
                        )
                    }
                }
            }
        }
    }

    fun setActiveTab(tab: SalaryTab) {
        _state.update { state ->
            val newState = state.copy(activeTab = tab)
            if (tab == SalaryTab.ANNUAL) recalcAnnual(newState) else newState
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

    fun updateFilingStatus(status: FilingStatus) {
        updateConfig { it.copy(filingStatus = status) }
    }

    fun updateState(state: String) {
        updateConfig { it.copy(state = state) }
    }

    fun updateCounty(county: String) {
        updateConfig { it.copy(county = county) }
    }

    fun updateCustomStateTaxRate(rate: Double?) {
        updateConfig { it.copy(taxOverrides = it.taxOverrides.copy(customStateTaxRate = rate)) }
    }

    fun updateCustomCountyTaxRate(rate: Double?) {
        updateConfig { it.copy(taxOverrides = it.taxOverrides.copy(customCountyTaxRate = rate)) }
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
                _state.update { it.copy(isSaving = false, message = "Saved") }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, message = "Error: ${e.message}") }
            }
        }
    }

    private fun updateConfig(update: (SalaryConfig) -> SalaryConfig) {
        _state.update { state ->
            val newConfig = update(state.config)
            val newState = state.copy(
                config = newConfig,
                calculation = PaycheckCalculator.calculate(newConfig)
            )
            if (state.activeTab == SalaryTab.ANNUAL) recalcAnnual(newState) else newState
        }
    }
}
