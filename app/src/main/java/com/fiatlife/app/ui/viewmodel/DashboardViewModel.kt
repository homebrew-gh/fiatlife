package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.data.repository.BillRepository
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
    val monthlyTakeHomeSource: MonthlyTakeHomeSource = MonthlyTakeHomeSource.ESTIMATED,
    val monthlyLoggedTakeHome: Double = 0.0,
    val monthlyProjectedRemainder: Double = 0.0,
    val monthlyLoggedPaycheckCount: Int = 0,
    val monthlyRemainingPaycheckCount: Int = 0,
    val monthlyLoggedOvertimeHours: Double = 0.0,
    val monthlyLoggedBonus: Double = 0.0,
    val monthlyPerPaycheckEstimate: Double = 0.0,
    val ytdNetPay: Double = 0.0,
    val ytdSource: DashboardYtdSource = DashboardYtdSource.NONE,
    val isConnected: Boolean = false,
    val hasData: Boolean = false,
    val topGoals: List<FinancialGoal> = emptyList(),
    val upcomingBills: List<UpcomingBillRow> = emptyList()
)

enum class DashboardYtdSource { NONE, LOGGED, ESTIMATED }

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
            creditAccountRepository.getAllCreditAccounts()
        ) { inputs, creditAccounts ->
            inputs to creditAccounts
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
    data: Pair<DashboardInputs, List<CreditAccount>>,
    currentMonthAnchor: Long
): DashboardState {
    val (inputs, creditAccounts) = data
    val (salary, nativeBills, cypherLogBills, goals, connected) = inputs
    val accountsById = creditAccounts.associateBy { it.id }
    val allBills = (nativeBills + cypherLogBills.map { it.bill }).filterNot { bill ->
        bill.isCancelled
    }
    val visibleBills = allBills.filterNot { bill ->
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
    val monthlyProjection = salary?.let {
        SalarySummary.computeMonthlyTakeHome(it, currentMonthAnchor)
    }
    val monthlyTakeHome = monthlyProjection?.totalTakeHome ?: 0.0
    val monthlyGross = monthlyProjection?.totalGross ?: 0.0
    val monthlyTaxes = monthlyProjection?.totalTaxes ?: 0.0
    val monthlyDeductions = monthlyProjection?.totalDeductions ?: 0.0
    val monthlyDisposable = monthlyTakeHome - monthlyBills
    val effectiveTaxRate = if (monthlyGross > 0.0) {
        monthlyTaxes / monthlyGross
    } else {
        calculation?.effectiveTaxRate ?: 0.0
    }

    var ytdNetPay = 0.0
    var ytdSource = DashboardYtdSource.NONE
    if (salary != null && calculation != null) {
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val annual = PaycheckCalculator.calculateAnnual(salary, 0.0, year)
        val ytd = SalarySummary.summarize(salary, calculation, annual, year)
        ytdNetPay = ytd.netPay
        ytdSource = when (ytd.source) {
            YtdSummary.Source.LOGGED -> DashboardYtdSource.LOGGED
            YtdSummary.Source.ESTIMATED -> DashboardYtdSource.ESTIMATED
        }
    }

    return DashboardState(
        takeHomePay = monthlyTakeHome,
        grossPay = monthlyGross,
        totalTaxes = monthlyTaxes,
        totalDeductions = monthlyDeductions,
        effectiveTaxRate = effectiveTaxRate,
        monthlyBills = monthlyBills,
        monthlyTakeHome = monthlyTakeHome,
        monthlyTakeHomeSource = monthlyProjection?.source ?: MonthlyTakeHomeSource.ESTIMATED,
        monthlyLoggedTakeHome = monthlyProjection?.loggedTakeHome ?: 0.0,
        monthlyProjectedRemainder = monthlyProjection?.projectedRemainder ?: 0.0,
        monthlyLoggedPaycheckCount = monthlyProjection?.loggedPaycheckCount ?: 0,
        monthlyRemainingPaycheckCount = monthlyProjection?.remainingPaycheckCount ?: 0,
        monthlyLoggedOvertimeHours = monthlyProjection?.loggedOvertimeHours ?: 0.0,
        monthlyLoggedBonus = monthlyProjection?.loggedBonusTotal ?: 0.0,
        monthlyPerPaycheckEstimate = monthlyProjection?.perPaycheckNet ?: 0.0,
        ytdNetPay = ytdNetPay,
        ytdSource = ytdSource,
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
        upcomingBills = buildUpcomingBillRows(
            visibleBills = visibleBills,
            threeMonthsFromNow = threeMonthsFromNow,
            linkedCreditBalance = ::linkedCreditBalance
        )
    )
}

private val upcomingBillDateFormat = ThreadLocal.withInitial {
    java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault())
}

private fun buildUpcomingBillRows(
    visibleBills: List<Bill>,
    threeMonthsFromNow: Long,
    linkedCreditBalance: (Bill) -> Double
): List<UpcomingBillRow> {
    return visibleBills
        .filter { bill ->
            val nextDue = bill.nextDueDateMillis()
            val withinWindow = bill.isPastDue() ||
                (nextDue != null && nextDue <= threeMonthsFromNow)
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
                amountDue = bill.effectiveAmountDue()
            )
        }
}

