package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.data.repository.BillRepository
import com.fiatlife.app.data.repository.BudgetRepository
import com.fiatlife.app.data.repository.CreditAccountRepository
import com.fiatlife.app.data.repository.CypherLogSubscriptionRepository
import com.fiatlife.app.data.repository.GoalRepository
import com.fiatlife.app.data.repository.SalaryRepository
import com.fiatlife.app.data.repository.stateWhileSubscribed
import com.fiatlife.app.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class DashboardState(
    val takeHomePay: Double = 0.0,
    val monthlyBills: Double = 0.0,
    /** Unpaid bills with next due date in the next 7 days (not overdue). */
    val billsComingDueCount: Int = 0,
    /** Unpaid bills that are past due. */
    val overdueBillCount: Int = 0,
    val housingMonthly: Double = 0.0,
    val mortgageAccountId: String? = null,
    val missingPaycheckCount: Int = 0,
    val goalCount: Int = 0,
    val monthlyDisposable: Double = 0.0,
    val monthlyTakeHomeSource: MonthlyTakeHomeSource = MonthlyTakeHomeSource.ESTIMATED,
    val monthlyLoggedTakeHome: Double = 0.0,
    val monthlyProjectedRemainder: Double = 0.0,
    val monthlyLoggedPaycheckCount: Int = 0,
    val monthlyRemainingPaycheckCount: Int = 0,
    val monthlyLoggedOvertimeHours: Double = 0.0,
    val monthlyLoggedBonus: Double = 0.0,
    val monthlyPerPaycheckEstimate: Double = 0.0,
    val isConnected: Boolean = false,
    val hasSalary: Boolean = false,
    val hasData: Boolean = false,
    val primaryGoal: FinancialGoal? = null,
    val upcomingBills: List<UpcomingBillRow> = emptyList(),
    val budgetUnbudgeted: Double = 0.0,
    val hasBudgetTargets: Boolean = false,
    val totalDebt: Double = 0.0,
    val debtFreeDateMs: Long? = null,
    val debtPayoffFeasible: Boolean = true,
    val debtAccountCount: Int = 0,
)

data class UpcomingBillRow(
    val id: String,
    val name: String,
    val subcategoryName: String,
    val dueDateText: String,
    val isPastDue: Boolean,
    val amountDue: Double
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val salaryRepository: SalaryRepository,
    private val billRepository: BillRepository,
    private val cypherLogSubscriptionRepository: CypherLogSubscriptionRepository,
    private val goalRepository: GoalRepository,
    private val creditAccountRepository: CreditAccountRepository,
    private val budgetRepository: BudgetRepository,
    private val nostrClient: NostrClient
) : ViewModel() {

    private val monthAnchor = MutableStateFlow(System.currentTimeMillis())

    val state: StateFlow<DashboardState> = run {
        MonthAnchor.startUpdates(viewModelScope, monthAnchor)
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
            creditAccountRepository.getAllCreditAccounts(),
            budgetRepository.getBudgetConfig()
        ) { inputs, creditAccounts, budgetConfig ->
            Triple(inputs, creditAccounts, budgetConfig)
        }

        combine(baseFlow, monthAnchor) { data, currentMonthAnchor ->
            buildDashboardState(data, currentMonthAnchor)
        }
            .flowOn(Dispatchers.Default)
            .distinctUntilChanged()
            .stateWhileSubscribed(viewModelScope, DashboardState())
    }
}

private data class DashboardInputs(
    val salary: SalaryConfig?,
    val nativeBills: List<Bill>,
    val cypherLogBills: List<BillWithSource>,
    val goals: List<FinancialGoal>,
    val connected: Boolean
)

private fun buildDashboardState(
    data: Triple<DashboardInputs, List<CreditAccount>, BudgetConfig?>,
    currentMonthAnchor: Long
): DashboardState {
    val (inputs, creditAccounts, budgetConfig) = data
    val (salary, nativeBills, cypherLogBills, goals, connected) = inputs
    val accountsById = creditAccounts.associateBy { it.id }
    val allBills = (nativeBills + cypherLogBills.map { it.bill }).filterNot { bill ->
        bill.isCancelled
    }
    val visibleBills = allBills.filterNot { bill ->
        bill.effectiveGeneralCategory == BillGeneralCategory.UTILITIES &&
            bill.isPaidForCurrentCycle()
    }
    val monthlyBills = allBills.sumOf { b -> b.dueAmountInMonth(currentMonthAnchor) }
    val billCategoryTotals = allBills.groupBy { it.effectiveGeneralCategory }
        .mapValues { (_, list) ->
            list.sumOf { b -> b.dueAmountInMonth(currentMonthAnchor) }
        }
    val now = System.currentTimeMillis()
    val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
    val fourteenDaysFromNow = now + 14L * 24 * 60 * 60 * 1000
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

    fun linkedAmountDue(bill: Bill): Double {
        bill.linkedCreditAccountId?.let { id ->
            accountsById[id]?.let { return it.effectiveAmountDue() }
        }
        val matched = creditAccounts.firstOrNull { acc ->
            acc.linkedBillId == bill.id || acc.name.equals(bill.name, ignoreCase = true)
        }
        return matched?.effectiveAmountDue() ?: bill.effectiveAmountDue()
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
    val monthlyProjection = salary?.let {
        SalarySummary.computeMonthlyTakeHome(it, currentMonthAnchor)
    }
    val monthlyTakeHome = monthlyProjection?.totalTakeHome ?: 0.0
    val monthlyDisposable = monthlyTakeHome - monthlyBills
    val year = java.util.Calendar.getInstance().apply { timeInMillis = now }
        .get(java.util.Calendar.YEAR)
    val missingPaycheckCount = salary?.let {
        SalarySummary.missingPaydaysForYear(it, year, now).size
    } ?: 0
    val mortgage = creditAccounts.firstOrNull { it.type == CreditAccountType.MORTGAGE }

    val budgetSummary = computeBudgetSummary(
        config = budgetConfig ?: defaultBudgetConfig(currentMonthAnchor),
        billCategoryTotals = billCategoryTotals,
        takeHome = monthlyTakeHome
    )
    val payoff = summarizeDebtPayoff(creditAccounts)
    val totalDebt = creditAccounts.sumOf { it.currentBalance }

    return DashboardState(
        takeHomePay = monthlyTakeHome,
        monthlyBills = monthlyBills,
        monthlyTakeHomeSource = monthlyProjection?.source ?: MonthlyTakeHomeSource.ESTIMATED,
        monthlyLoggedTakeHome = monthlyProjection?.loggedTakeHome ?: 0.0,
        monthlyProjectedRemainder = monthlyProjection?.projectedRemainder ?: 0.0,
        monthlyLoggedPaycheckCount = monthlyProjection?.loggedPaycheckCount ?: 0,
        monthlyRemainingPaycheckCount = monthlyProjection?.remainingPaycheckCount ?: 0,
        monthlyLoggedOvertimeHours = monthlyProjection?.loggedOvertimeHours ?: 0.0,
        monthlyLoggedBonus = monthlyProjection?.loggedBonusTotal ?: 0.0,
        monthlyPerPaycheckEstimate = monthlyProjection?.perPaycheckNet ?: 0.0,
        billsComingDueCount = comingDueCount,
        overdueBillCount = overdueCount,
        housingMonthly = creditAccounts.sumOf { it.housingPitiMonthly() },
        mortgageAccountId = mortgage?.id,
        missingPaycheckCount = missingPaycheckCount,
        goalCount = goals.size,
        monthlyDisposable = monthlyDisposable,
        isConnected = connected,
        hasSalary = salary != null,
        hasData = salary != null || nativeBills.isNotEmpty() || cypherLogBills.isNotEmpty() || goals.isNotEmpty(),
        primaryGoal = pickPrimaryGoal(goals),
        upcomingBills = buildUpcomingBillRows(
            visibleBills = visibleBills,
            dueSoonUntil = fourteenDaysFromNow,
            linkedCreditBalance = ::linkedCreditBalance,
            linkedAmountDue = ::linkedAmountDue
        ),
        budgetUnbudgeted = budgetSummary.unbudgeted,
        hasBudgetTargets = budgetSummary.totalTarget > 0.0,
        totalDebt = totalDebt,
        debtFreeDateMs = payoff.debtFreeDateMillis,
        debtPayoffFeasible = payoff.allFeasible,
        debtAccountCount = creditAccounts.size,
    )
}

private fun pickPrimaryGoal(goals: List<FinancialGoal>): FinancialGoal? {
    val incomplete = goals.filter { !it.isComplete }
    if (incomplete.isEmpty()) return null
    val withDates = incomplete.filter { goal -> (goal.targetDate ?: 0L) > 0L }
    if (withDates.isNotEmpty()) {
        return withDates.minBy { it.targetDate ?: Long.MAX_VALUE }
    }
    return incomplete.minBy { it.progressPercent }
}

private val upcomingBillDateFormat = ThreadLocal.withInitial {
    java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault())
}

private fun buildUpcomingBillRows(
    visibleBills: List<Bill>,
    dueSoonUntil: Long,
    linkedCreditBalance: (Bill) -> Double,
    linkedAmountDue: (Bill) -> Double
): List<UpcomingBillRow> {
    return visibleBills
        .filter { bill ->
            val nextDue = bill.nextDueDateMillis()
            val withinWindow = bill.isPastDue() ||
                (nextDue != null && nextDue <= dueSoonUntil)
            if (!withinWindow) return@filter false
            if (bill.isCreditOrLoan()) {
                linkedCreditBalance(bill) > 0.0 && !bill.isPaidForCurrentCycle()
            } else {
                !bill.isPaidForCurrentCycle()
            }
        }
        .sortedWith(
            compareBy<Bill> { !it.isPastDue() }
                .thenBy { bill ->
                    if (bill.isPastDue()) bill.lastDueDateMillis() ?: 0L
                    else bill.nextDueDateMillis() ?: Long.MAX_VALUE
                }
        )
        .take(5)
        .map { bill ->
            val isPastDue = bill.isPastDue()
            val dueMillis = if (isPastDue) bill.lastDueDateMillis() else bill.nextDueDateMillis()
            val formatted = dueMillis?.let { upcomingBillDateFormat.get().format(java.util.Date(it)) }.orEmpty()
            val dueDateText = when {
                formatted.isEmpty() -> ""
                isPastDue -> "$formatted (Overdue)"
                else -> formatted
            }
            UpcomingBillRow(
                id = bill.id,
                name = bill.name,
                subcategoryName = bill.effectiveSubcategory.displayName,
                dueDateText = dueDateText,
                isPastDue = isPastDue,
                amountDue = linkedAmountDue(bill)
            )
        }
}

