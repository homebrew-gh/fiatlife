package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.data.repository.BillRepository
import com.fiatlife.app.data.repository.GoalRepository
import com.fiatlife.app.data.repository.SalaryRepository
import com.fiatlife.app.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardState(
    val takeHomePay: Double = 0.0,
    val grossPay: Double = 0.0,
    val totalTaxes: Double = 0.0,
    val totalDeductions: Double = 0.0,
    val effectiveTaxRate: Double = 0.0,
    val monthlyBills: Double = 0.0,
    val billCount: Int = 0,
    val unpaidBillCount: Int = 0,
    val goalCount: Int = 0,
    val goalsProgress: Double = 0.0,
    val totalSaved: Double = 0.0,
    val totalGoalTarget: Double = 0.0,
    val monthlyDisposable: Double = 0.0,
    val isConnected: Boolean = false,
    val topGoals: List<FinancialGoal> = emptyList(),
    val upcomingBills: List<Bill> = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val salaryRepository: SalaryRepository,
    private val billRepository: BillRepository,
    private val goalRepository: GoalRepository,
    private val nostrClient: NostrClient
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                salaryRepository.getSalaryConfig(),
                billRepository.getAllBills(),
                goalRepository.getAllGoals(),
                nostrClient.connectionState
            ) { salary, bills, goals, connected ->
                val calculation = salary?.let { PaycheckCalculator.calculate(it) }
                val monthlyBills = bills.sumOf { b ->
                    b.amount * b.frequency.timesPerYear / 12.0
                }
                val unpaidCount = bills.count { !it.isPaid }
                val totalSaved = goals.sumOf { it.currentAmount }
                val totalTarget = goals.sumOf { it.targetAmount }
                val goalsProgress = if (totalTarget > 0) totalSaved / totalTarget * 100 else 0.0
                val monthlyTakeHome = (calculation?.netPay ?: 0.0) *
                        (salary?.payFrequency?.periodsPerYear ?: 26) / 12.0
                val monthlyDisposable = monthlyTakeHome - monthlyBills

                DashboardState(
                    takeHomePay = calculation?.netPay ?: 0.0,
                    grossPay = calculation?.grossPay ?: 0.0,
                    totalTaxes = calculation?.totalTaxes ?: 0.0,
                    totalDeductions = (calculation?.totalPreTaxDeductions ?: 0.0) +
                            (calculation?.totalPostTaxDeductions ?: 0.0),
                    effectiveTaxRate = calculation?.effectiveTaxRate ?: 0.0,
                    monthlyBills = monthlyBills,
                    billCount = bills.size,
                    unpaidBillCount = unpaidCount,
                    goalCount = goals.size,
                    goalsProgress = goalsProgress,
                    totalSaved = totalSaved,
                    totalGoalTarget = totalTarget,
                    monthlyDisposable = monthlyDisposable,
                    isConnected = connected,
                    topGoals = goals.sortedByDescending { it.progressPercent }.take(3),
                    upcomingBills = bills.filter { !it.isPaid }.take(5)
                )
            }.collect { state ->
                _state.value = state
            }
        }
    }
}
