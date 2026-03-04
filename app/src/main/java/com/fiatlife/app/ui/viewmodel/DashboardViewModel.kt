package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.data.repository.BillRepository
import com.fiatlife.app.data.repository.CreditAccountRepository
import com.fiatlife.app.data.repository.CypherLogSubscriptionRepository
import com.fiatlife.app.data.repository.GoalRepository
import com.fiatlife.app.data.repository.SalaryRepository
import com.fiatlife.app.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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
    val monthlyTakeHome: Double = 0.0,
    val billCount: Int = 0,
    /** Unpaid bills with next due date in the next 7 days (not overdue). */
    val billsComingDueCount: Int = 0,
    /** Unpaid bills that are past due. */
    val overdueBillCount: Int = 0,
    val billCategoryTotals: Map<BillGeneralCategory, Double> = emptyMap(),
    val goalCount: Int = 0,
    val goalsProgress: Double = 0.0,
    val totalSaved: Double = 0.0,
    val totalGoalTarget: Double = 0.0,
    val monthlyDisposable: Double = 0.0,
    val isConnected: Boolean = false,
    val hasData: Boolean = false,
    val topGoals: List<FinancialGoal> = emptyList(),
    val upcomingBills: List<Bill> = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val salaryRepository: SalaryRepository,
    private val billRepository: BillRepository,
    private val cypherLogSubscriptionRepository: CypherLogSubscriptionRepository,
    private val goalRepository: GoalRepository,
    private val creditAccountRepository: CreditAccountRepository,
    private val nostrClient: NostrClient
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()
    private val monthAnchor = MutableStateFlow(System.currentTimeMillis())

    init {
        startMonthAnchorUpdates()
        viewModelScope.launch {
            val baseFlow = combine(
                combine(
                    salaryRepository.getSalaryConfig(),
                    billRepository.getAllBills(),
                    cypherLogSubscriptionRepository.getAllAsBills(),
                    goalRepository.getAllGoals(),
                    nostrClient.connectionState
                ) { salary, nativeBills, cypherLogBills, goals, connected ->
                    DashboardInputs(salary, nativeBills, cypherLogBills, goals, connected)
                },
                creditAccountRepository.getAllCreditAccounts()
            ) { inputs, creditAccounts ->
                inputs to creditAccounts
            }

            combine(baseFlow, monthAnchor) { data, currentMonthAnchor ->
                data to currentMonthAnchor
            }.collect { (data, currentMonthAnchor) ->
                val (inputs, creditAccounts) = data
                val (salary, nativeBills, cypherLogBills, goals, connected) = inputs
                val accountsById = creditAccounts.associateBy { it.id }
                val allBills = (nativeBills + cypherLogBills.map { it.bill }).filterNot { bill ->
                    bill.isCancelled
                }
                val visibleBills = allBills.filterNot { bill ->
                    // Match Bills tab behavior: hide paid utilities from dashboard until next cycle/update.
                    bill.effectiveGeneralCategory == BillGeneralCategory.UTILITIES &&
                        bill.isPaidForCurrentCycle()
                }
                val calculation = salary?.let { PaycheckCalculator.calculate(it) }
                val monthlyBills = allBills.sumOf { b -> b.dueAmountInMonth(currentMonthAnchor) }
                val billCategoryTotals = allBills.groupBy { it.effectiveGeneralCategory }
                    .mapValues { (_, list) ->
                        list.sumOf { b -> b.dueAmountInMonth(currentMonthAnchor) }
                    }
                val now = System.currentTimeMillis()
                val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
                val threeMonthsFromNow = java.util.Calendar.getInstance().apply {
                    timeInMillis = now
                    add(java.util.Calendar.MONTH, 3)
                }.timeInMillis
                val overdueCount = visibleBills.count {
                    it.effectiveGeneralCategory != BillGeneralCategory.CREDIT_LOANS &&
                        !it.isPaidForCurrentCycle() &&
                        it.isPastDue()
                }
                fun linkedCreditBalance(bill: Bill): Double {
                    if (!bill.isCreditOrLoan()) return 0.0
                    bill.linkedCreditAccountId?.let { id -> return accountsById[id]?.currentBalance ?: 0.0 }
                    val matched = creditAccounts.firstOrNull { acc ->
                        acc.linkedBillId == bill.id || acc.name.equals(bill.name, ignoreCase = true)
                    }
                    return matched?.currentBalance ?: 0.0
                }

                val comingDueCount = visibleBills.count { bill ->
                    val nextDue = bill.nextDueDateMillis() ?: return@count false
                    if (bill.isCreditOrLoan()) {
                        linkedCreditBalance(bill) > 0.0 &&
                            !bill.isPaidForCurrentCycle() &&
                            !bill.isPastDue() &&
                            nextDue <= now + sevenDaysMs
                    } else {
                        !bill.isPaidForCurrentCycle() &&
                            !bill.isPastDue() &&
                            nextDue <= now + sevenDaysMs
                    }
                }
                val totalSaved = goals.sumOf { it.currentAmount }
                val totalTarget = goals.sumOf { it.targetAmount }
                val goalsProgress = if (totalTarget > 0) totalSaved / totalTarget * 100 else 0.0
                val monthlyMultiplier = calculateMonthlyPaycheckMultiplier(salary, currentMonthAnchor)
                val monthlyTakeHome = (calculation?.netPay ?: 0.0) * monthlyMultiplier
                val monthlyGross = (calculation?.grossPay ?: 0.0) * monthlyMultiplier
                val monthlyTaxes = (calculation?.totalTaxes ?: 0.0) * monthlyMultiplier
                val monthlyDeductions = (
                    (calculation?.totalPreTaxDeductions ?: 0.0) +
                        (calculation?.totalPostTaxDeductions ?: 0.0)
                    ) * monthlyMultiplier
                val monthlyDisposable = monthlyTakeHome - monthlyBills

                _state.value = DashboardState(
                    takeHomePay = monthlyTakeHome,
                    grossPay = monthlyGross,
                    totalTaxes = monthlyTaxes,
                    totalDeductions = monthlyDeductions,
                    effectiveTaxRate = calculation?.effectiveTaxRate ?: 0.0,
                    monthlyBills = monthlyBills,
                    monthlyTakeHome = monthlyTakeHome,
                    billCount = visibleBills.size,
                    billsComingDueCount = comingDueCount,
                    overdueBillCount = overdueCount,
                    billCategoryTotals = billCategoryTotals,
                    goalCount = goals.size,
                    goalsProgress = goalsProgress,
                    totalSaved = totalSaved,
                    totalGoalTarget = totalTarget,
                    monthlyDisposable = monthlyDisposable,
                    isConnected = connected,
                    hasData = salary != null || nativeBills.isNotEmpty() || cypherLogBills.isNotEmpty() || goals.isNotEmpty(),
                    topGoals = goals.sortedByDescending { it.progressPercent }.take(3),
                    upcomingBills = visibleBills
                        .filter { bill ->
                            val nextDue = bill.nextDueDateMillis()
                            // Only include bills due within 3 months (or past due). Exclude far-future due dates.
                            val withinWindow = bill.isPastDue() ||
                                (nextDue != null && nextDue <= threeMonthsFromNow)
                            if (!withinWindow) return@filter false
                            // Include credit/loan by due date (even with zero balance so user sees next due).
                            !bill.isPaidForCurrentCycle()
                        }
                        .sortedWith(
                            compareBy<Bill> { !it.isPastDue() }
                                .thenBy { bill ->
                                    if (bill.isPastDue()) bill.lastDueDateMillis() ?: 0L
                                    else bill.nextDueDateMillis() ?: Long.MAX_VALUE
                                }
                        )
                        .take(5)
                )
            }
        }

    }

    private fun startMonthAnchorUpdates() {
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                monthAnchor.value = now
                delay(millisUntilNextMonth(now))
            }
        }
    }

    private fun millisUntilNextMonth(now: Long): Long {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            add(java.util.Calendar.MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return (cal.timeInMillis - now).coerceAtLeast(60_000L)
    }

    private fun calculateMonthlyPaycheckMultiplier(
        salary: SalaryConfig?,
        monthAnchorMillis: Long
    ): Double {
        if (salary == null) return 0.0
        val anchor = salary.firstPaydayOfYearMillis
        if (anchor == null) {
            return salary.payFrequency.periodsPerYear / 12.0
        }
        return paycheckCountInMonth(
            firstPaydayOfYearMillis = anchor,
            frequency = salary.payFrequency,
            monthAnchorMillis = monthAnchorMillis
        ).toDouble()
    }

    private fun paycheckCountInMonth(
        firstPaydayOfYearMillis: Long,
        frequency: PayFrequency,
        monthAnchorMillis: Long
    ): Int {
        if (frequency == PayFrequency.SEMIMONTHLY) return 2
        if (frequency == PayFrequency.MONTHLY) return 1

        val monthStart = java.util.Calendar.getInstance().apply {
            timeInMillis = monthAnchorMillis
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val monthEnd = java.util.Calendar.getInstance().apply {
            timeInMillis = monthStart
            add(java.util.Calendar.MONTH, 1)
        }.timeInMillis - 1

        val stepMillis = when (frequency) {
            PayFrequency.WEEKLY -> 7L * 24L * 60L * 60L * 1000L
            PayFrequency.BIWEEKLY -> 14L * 24L * 60L * 60L * 1000L
            else -> return frequency.periodsPerYear / 12
        }

        var count = 0
        var payday = startOfDay(firstPaydayOfYearMillis)
        val maxIterations = 500
        var i = 0
        while (payday <= monthEnd && i < maxIterations) {
            if (payday in monthStart..monthEnd) count++
            payday += stepMillis
            i++
        }
        return count.coerceAtLeast(1)
    }

    private fun startOfDay(millis: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

private data class DashboardInputs(
    val salary: SalaryConfig?,
    val nativeBills: List<Bill>,
    val cypherLogBills: List<BillWithSource>,
    val goals: List<FinancialGoal>,
    val connected: Boolean
)
