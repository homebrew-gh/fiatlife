package com.fiatlife.app.domain.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Hard cap on simulated months (100 years) — beyond this we call it "won't pay off". */
private const val MAX_MONTHS = 1200

/** Result of projecting how a balance is repaid at a fixed monthly payment. */
data class PayoffProjection(
    /** Whether the balance is fully repaid within MAX_MONTHS at the given payment. */
    val feasible: Boolean,
    /** Months until paid off (0 if already zero). Only meaningful when feasible. */
    val months: Int,
    /** Total interest paid over the life of the payoff. */
    val totalInterest: Double,
    /** Estimated payoff date in epoch millis, or null when not feasible / already paid. */
    val payoffDateMillis: Long?,
    /** Interest accruing in the first month at the current balance. */
    val monthlyInterest: Double
)

/** Interest that accrues this month at the current balance. */
fun CreditAccount.monthlyInterest(): Double {
    val rate = effectiveApr()
    if (currentBalance <= 0 || rate <= 0) return 0.0
    return currentBalance * (rate / 12.0)
}

/**
 * Simulate paying off a balance with a fixed monthly payment.
 * Models the intuitive "at $X/mo you'll be done in Y" — the payment is held
 * constant rather than recomputed (which matches the figure shown to the user).
 */
fun projectPayoff(
    balance: Double,
    annualRate: Double,
    monthlyPayment: Double,
    nowMillis: Long = System.currentTimeMillis()
): PayoffProjection {
    val balance0 = balance.coerceAtLeast(0.0)
    val monthlyRate = annualRate.coerceAtLeast(0.0) / 12.0
    val payment = monthlyPayment.coerceAtLeast(0.0)
    val startInterest = balance0 * monthlyRate

    if (balance0 <= 0.0) {
        return PayoffProjection(
            feasible = true,
            months = 0,
            totalInterest = 0.0,
            payoffDateMillis = null,
            monthlyInterest = 0.0
        )
    }

    val notFeasible = PayoffProjection(
        feasible = false,
        months = Int.MAX_VALUE,
        totalInterest = Double.POSITIVE_INFINITY,
        payoffDateMillis = null,
        monthlyInterest = startInterest
    )

    // Payment can't keep up with interest, or there's no payment at all.
    if (payment <= 0.0) return notFeasible
    if (monthlyRate > 0 && payment <= startInterest + 1e-9) return notFeasible

    var remaining = balance0
    var totalInterest = 0.0
    var months = 0
    while (remaining > 0.005 && months < MAX_MONTHS) {
        val interest = remaining * monthlyRate
        val applied = minOf(payment, remaining + interest)
        remaining = remaining + interest - applied
        totalInterest += interest
        months += 1
        if (remaining < 0) remaining = 0.0
    }

    if (remaining > 0.005) return notFeasible

    val cal = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        add(Calendar.MONTH, months)
    }
    return PayoffProjection(
        feasible = true,
        months = months,
        totalInterest = totalInterest,
        payoffDateMillis = cal.timeInMillis,
        monthlyInterest = startInterest
    )
}

/** Project payoff for an account using its current effective monthly payment. */
fun CreditAccount.projectPayoff(
    extraPayment: Double = 0.0,
    nowMillis: Long = System.currentTimeMillis()
): PayoffProjection {
    val balance0 = currentBalance.coerceAtLeast(0.0)
    val payment = effectiveMonthlyPayment() + extraPayment.coerceAtLeast(0.0)
    val initialRate = effectiveApr(nowMillis) / 12.0
    val startInterest = balance0 * initialRate
    if (balance0 <= 0.0) {
        return PayoffProjection(true, 0, 0.0, null, 0.0)
    }
    if (payment <= 0.0) {
        return PayoffProjection(
            false,
            Int.MAX_VALUE,
            Double.POSITIVE_INFINITY,
            null,
            startInterest
        )
    }

    var remaining = balance0
    var totalInterest = 0.0
    var months = 0
    val cursor = Calendar.getInstance().apply { timeInMillis = nowMillis }
    while (remaining > 0.005 && months < MAX_MONTHS) {
        val monthlyRate = effectiveApr(cursor.timeInMillis) / 12.0
        val interest = remaining * monthlyRate
        if (monthlyRate > 0.0 && payment <= interest + 1e-9) {
            return PayoffProjection(
                false,
                Int.MAX_VALUE,
                Double.POSITIVE_INFINITY,
                null,
                startInterest
            )
        }
        remaining += interest - minOf(payment, remaining + interest)
        totalInterest += interest
        months += 1
        cursor.add(Calendar.MONTH, 1)
    }
    if (remaining > 0.005) {
        return PayoffProjection(
            false,
            Int.MAX_VALUE,
            Double.POSITIVE_INFINITY,
            null,
            startInterest
        )
    }
    return PayoffProjection(
        true,
        months,
        totalInterest,
        cursor.timeInMillis,
        startInterest
    )
}

/**
 * Whether a revolving account is at risk of the "minimum payment trap":
 * the current payment barely beats interest, so payoff is impossible or very slow.
 */
fun CreditAccount.isMinimumPaymentTrap(): Boolean {
    if (!type.isRevolving) return false
    if (currentBalance <= 0 || effectiveApr() <= 0) return false
    val proj = projectPayoff()
    return !proj.feasible || proj.months > 360 // > 30 years
}

/** Aggregate payoff outlook across a set of accounts. */
data class DebtPayoffSummary(
    val debtFreeDateMillis: Long?,
    val longestMonths: Int,
    val totalInterest: Double,
    val monthlyInterest: Double,
    val infeasibleCount: Int,
    val allFeasible: Boolean,
    val hasInterestBearingDebt: Boolean
)

fun summarizeDebtPayoff(
    accounts: List<CreditAccount>,
    nowMillis: Long = System.currentTimeMillis()
): DebtPayoffSummary {
    var debtFreeDate: Long? = null
    var longestMonths = 0
    var totalInterest = 0.0
    var monthlyInterest = 0.0
    var infeasibleCount = 0
    var hasInterestBearing = false

    for (account in accounts) {
        if (account.currentBalance <= 0) continue
        val interest = account.monthlyInterest()
        if (interest > 0) hasInterestBearing = true
        monthlyInterest += interest

        val proj = account.projectPayoff(nowMillis = nowMillis)
        if (!proj.feasible) {
            infeasibleCount += 1
            continue
        }
        totalInterest += proj.totalInterest
        if (proj.months > longestMonths) longestMonths = proj.months
        proj.payoffDateMillis?.let { date ->
            if (debtFreeDate == null || date > debtFreeDate!!) debtFreeDate = date
        }
    }

    return DebtPayoffSummary(
        debtFreeDateMillis = debtFreeDate,
        longestMonths = longestMonths,
        totalInterest = totalInterest,
        monthlyInterest = monthlyInterest,
        infeasibleCount = infeasibleCount,
        allFeasible = infeasibleCount == 0,
        hasInterestBearingDebt = hasInterestBearing
    )
}

/** Format a month count as "3 yr 2 mo" / "8 mo" / "1 yr". */
fun formatMonths(months: Int): String {
    if (months <= 0) return "0 mo"
    val years = months / 12
    val rem = months % 12
    return when {
        years == 0 -> "$rem mo"
        rem == 0 -> "$years yr"
        else -> "$years yr $rem mo"
    }
}

/** Format an epoch-millis payoff date as "Mar 2031". */
fun formatPayoffDate(millis: Long): String =
    SimpleDateFormat("MMM yyyy", Locale.US).format(java.util.Date(millis))
