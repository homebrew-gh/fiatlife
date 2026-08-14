package com.fiatlife.app.domain.model

import java.util.Calendar

private const val MAX_MONTHS = 1200

enum class PayoffStrategy(val label: String, val description: String) {
    AVALANCHE("Avalanche", "Highest interest rate first — saves the most money."),
    SNOWBALL("Snowball", "Smallest balance first — quickest early wins.")
}

data class AccountPlanResult(
    val accountId: String,
    val name: String,
    /** 1-based order in which this account is fully paid off. */
    val order: Int,
    /** Months from now until this account hits zero (Int.MAX_VALUE if never). */
    val payoffMonths: Int,
    /** Estimated payoff date in epoch millis. */
    val payoffDateMillis: Long?,
    /** Total interest paid on this account under the plan. */
    val totalInterest: Double,
    val startingBalance: Double
)

data class DebtPlan(
    val strategy: PayoffStrategy,
    /** Extra dollars per month applied on top of all minimum payments. */
    val extraMonthly: Double,
    /** Total committed budget per month (minimums + per-account extras + extra). */
    val monthlyBudget: Double,
    /** Whether all debts are repaid within the simulation horizon. */
    val feasible: Boolean,
    /** Months until the last debt is paid off (Int.MAX_VALUE if not feasible). */
    val months: Int,
    /** Date the final debt is cleared. */
    val debtFreeDateMillis: Long?,
    /** Total interest paid across all accounts under the plan. */
    val totalInterest: Double,
    /** Interest paid if only minimums were paid with no rollover/extra. */
    val baselineInterest: Double,
    /** Interest saved versus the minimums-only baseline. */
    val interestSaved: Double,
    /** Months saved versus the minimums-only baseline. */
    val monthsSaved: Int,
    /** Per-account payoff order and dates. */
    val accounts: List<AccountPlanResult>,
    /** Total remaining balance at the end of each month (index 0 = today's total). */
    val timeline: List<Double>
)

private class PlanSimState(
    val account: CreditAccount,
    var balance: Double,
    val minPayment: Double,
    /** Fixed extra the user committed to this specific account each month. */
    val accountExtra: Double,
    var interestPaid: Double = 0.0,
    var payoffMonth: Int? = null
)

private fun List<PlanSimState>.strategyOrder(
    strategy: PayoffStrategy,
    asOfMillis: Long
): List<PlanSimState> =
    filter { it.balance > 0.005 }.sortedWith(
        when (strategy) {
            PayoffStrategy.AVALANCHE ->
                compareByDescending<PlanSimState> {
                    it.account.effectiveApr(asOfMillis)
                }.thenBy { it.balance }
            PayoffStrategy.SNOWBALL ->
                compareBy<PlanSimState> { it.balance }.thenByDescending {
                    it.account.effectiveApr(asOfMillis)
                }
        }
    )

/**
 * Simulate a debt-payoff plan using the snowball/avalanche rollover method.
 * The total monthly budget (sum of minimums + extra) is held constant; as each
 * account is cleared, its freed payment rolls into the next target account.
 */
fun buildDebtPlan(
    accounts: List<CreditAccount>,
    strategy: PayoffStrategy,
    extraMonthly: Double,
    perAccountExtra: Map<String, Double> = emptyMap(),
    nowMillis: Long = System.currentTimeMillis()
): DebtPlan {
    val extra = extraMonthly.coerceAtLeast(0.0)
    val states = accounts
        .filter { it.currentBalance > 0.005 }
        .map {
            PlanSimState(
                account = it,
                balance = it.currentBalance,
                minPayment = it.effectiveMonthlyPayment().coerceAtLeast(0.0),
                accountExtra = (perAccountExtra[it.id] ?: 0.0).coerceAtLeast(0.0)
            )
        }

    val minimumsTotal = states.sumOf { it.minPayment }
    val accountExtrasTotal = states.sumOf { it.accountExtra }
    val monthlyBudget = minimumsTotal + accountExtrasTotal + extra
    val baseline = summarizeDebtPayoff(accounts, nowMillis)

    val timeline = mutableListOf(states.sumOf { it.balance })
    var month = 0
    if (states.isNotEmpty() && monthlyBudget > 0) {
        while (states.any { it.balance > 0.005 } && month < MAX_MONTHS) {
            month += 1
            val monthStart = addMonths(nowMillis, month - 1)

            for (s in states) {
                if (s.balance <= 0.005) continue
                val interest =
                    s.balance * (s.account.effectiveApr(monthStart) / 12.0)
                s.balance += interest
                s.interestPaid += interest
            }

            var available = monthlyBudget

            for (s in states) {
                if (s.balance <= 0.005 || available <= 0) continue
                val committed = s.minPayment + s.accountExtra
                val pay = minOf(committed, s.balance, available)
                s.balance -= pay
                available -= pay
            }

            for (s in states.strategyOrder(strategy, monthStart)) {
                if (available <= 0.005) break
                val pay = minOf(available, s.balance)
                s.balance -= pay
                available -= pay
            }

            for (s in states) {
                if (s.payoffMonth == null && s.balance <= 0.005) {
                    s.balance = 0.0
                    s.payoffMonth = month
                }
            }

            timeline.add(states.sumOf { it.balance.coerceAtLeast(0.0) })
        }
    }

    val feasible = states.all { it.payoffMonth != null }
    val months = if (feasible) month else Int.MAX_VALUE
    val totalInterest = states.sumOf { it.interestPaid }

    val accountResults = states
        .map { s ->
            AccountPlanResult(
                accountId = s.account.id,
                name = s.account.name,
                order = 0,
                payoffMonths = s.payoffMonth ?: Int.MAX_VALUE,
                payoffDateMillis = s.payoffMonth?.let { addMonths(nowMillis, it) },
                totalInterest = s.interestPaid,
                startingBalance = s.account.currentBalance
            )
        }
        .sortedBy { it.payoffMonths }
        .mapIndexed { index, r -> r.copy(order = index + 1) }

    val debtFreeDate = if (feasible) addMonths(nowMillis, month) else null
    val interestSaved = if (feasible) (baseline.totalInterest - totalInterest).coerceAtLeast(0.0) else 0.0
    val monthsSaved = if (feasible && baseline.allFeasible)
        (baseline.longestMonths - month).coerceAtLeast(0)
    else 0

    return DebtPlan(
        strategy = strategy,
        extraMonthly = extra,
        monthlyBudget = monthlyBudget,
        feasible = feasible,
        months = months,
        debtFreeDateMillis = debtFreeDate,
        totalInterest = totalInterest,
        baselineInterest = baseline.totalInterest,
        interestSaved = interestSaved,
        monthsSaved = monthsSaved,
        accounts = accountResults,
        timeline = timeline
    )
}

private fun addMonths(nowMillis: Long, months: Int): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        add(Calendar.MONTH, months)
    }
    return cal.timeInMillis
}

data class PromoExpiryWarning(
    val accountId: String,
    val name: String,
    val monthsUntilExpiry: Int,
    val payoffMonths: Int,
    val deferredInterest: Boolean
)

/** Accounts whose planned payoff is after the promotional APR ends. */
fun promoExpiryWarnings(
    accounts: List<CreditAccount>,
    plan: DebtPlan,
    asOfMillis: Long = System.currentTimeMillis()
): List<PromoExpiryWarning> {
    val byId = plan.accounts.associateBy { it.accountId }
    return accounts.mapNotNull { account ->
        val monthsLeft = account.monthsUntilPromotionEnds(asOfMillis) ?: return@mapNotNull null
        if (monthsLeft <= 0) return@mapNotNull null
        val result = byId[account.id] ?: return@mapNotNull null
        if (result.payoffMonths == Int.MAX_VALUE || result.payoffMonths > monthsLeft) {
            PromoExpiryWarning(
                accountId = account.id,
                name = account.name,
                monthsUntilExpiry = monthsLeft,
                payoffMonths = result.payoffMonths,
                deferredInterest = account.deferredInterest
            )
        } else {
            null
        }
    }
}
