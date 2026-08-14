package com.fiatlife.app.domain.model

import java.util.Calendar

data class MortgageScheduleRow(
    val paymentNumber: Int,
    val dateMs: Long,
    val payment: Double,
    val principal: Double,
    val interest: Double,
    val extraPrincipal: Double,
    val balance: Double
)

data class MortgageSummary(
    val loanAmount: Double,
    val monthlyPayment: Double,
    val estimatedMonthlyTotal: Double,
    val termMonths: Int,
    val totalInterest: Double,
    val payoffDateMs: Long?,
    val paymentsRemaining: Int,
    val paymentsElapsed: Int,
    val principalPaid: Double,
    val interestPaid: Double
)

data class MortgageScheduleResult(
    val rows: List<MortgageScheduleRow>,
    val summary: MortgageSummary
)

/** This month's split from the current balance, plus schedule totals. */
data class MortgagePaymentSnapshot(
    val principal: Double,
    val interest: Double,
    val extraPrincipal: Double,
    val escrow: Double,
    val pmi: Double,
    val remainingBalance: Double,
    val principalPaid: Double,
    val interestPaid: Double,
    val paymentsRemaining: Int,
    val paymentsElapsed: Int,
    val payoffDateMs: Long?,
    val monthlyPi: Double,
    val servicerDraft: Double
)

fun calculateMonthlyPayment(principal: Double, annualRate: Double, termMonths: Int): Double {
    if (principal <= 0.0 || termMonths <= 0) return 0.0
    if (annualRate <= 0.0) return principal / termMonths
    val r = annualRate / 12.0
    val factor = Math.pow(1.0 + r, termMonths.toDouble())
    return (principal * r * factor) / (factor - 1.0)
}

private fun addMonths(startMs: Long, months: Int): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = startMs }
    cal.add(Calendar.MONTH, months)
    return cal.timeInMillis
}

private fun monthsBetween(startMs: Long, endMs: Long): Int {
    val start = Calendar.getInstance().apply { timeInMillis = startMs }
    val end = Calendar.getInstance().apply { timeInMillis = endMs }
    return (end.get(Calendar.YEAR) - start.get(Calendar.YEAR)) * 12 +
        end.get(Calendar.MONTH) - start.get(Calendar.MONTH)
}

fun buildAmortizationSchedule(
    principal: Double,
    annualRate: Double,
    termMonths: Int,
    startDateMs: Long? = null,
    monthlyPayment: Double? = null,
    extraMonthlyPayment: Double = 0.0,
    monthlyTaxInsurance: Double = 0.0,
    monthlyPmi: Double = 0.0,
    pmiDropBalance: Double = 0.0,
    nowMs: Long = System.currentTimeMillis()
): MortgageScheduleResult {
    val loan = principal.coerceAtLeast(0.0)
    val months = termMonths.coerceAtLeast(0)
    val rate = annualRate.coerceAtLeast(0.0)
    val extra = extraMonthlyPayment.coerceAtLeast(0.0)
    val taxIns = monthlyTaxInsurance.coerceAtLeast(0.0)
    val pmi = monthlyPmi.coerceAtLeast(0.0)
    val drop = pmiDropBalance.coerceAtLeast(0.0)
    val computed = calculateMonthlyPayment(loan, rate, months)
    val pi = if (monthlyPayment != null && monthlyPayment > 0.0) monthlyPayment else computed
    val startMs = startDateMs ?: nowMs
    val paymentsElapsed = if (startDateMs != null) {
        monthsBetween(startMs, nowMs).coerceAtLeast(0)
    } else {
        0
    }

    val rows = mutableListOf<MortgageScheduleRow>()
    var balance = loan
    var totalInterest = 0.0
    val initialPmiActive = pmi > 0.0 && (drop <= 0.0 || loan > drop)

    var n = 1
    while (n <= months && balance > 0.005) {
        val monthlyRate = rate / 12.0
        val interest = balance * monthlyRate
        var principalPortion = (pi - interest).coerceAtLeast(0.0)
        if (principalPortion > balance) principalPortion = balance
        val extraPrincipal = extra.coerceAtMost((balance - principalPortion).coerceAtLeast(0.0))
        val totalPrincipal = principalPortion + extraPrincipal
        balance = (balance - totalPrincipal).coerceAtLeast(0.0)
        totalInterest += interest
        rows.add(
            MortgageScheduleRow(
                paymentNumber = n,
                dateMs = addMonths(startMs, n - 1),
                payment = principalPortion + interest + extraPrincipal,
                principal = principalPortion,
                interest = interest,
                extraPrincipal = extraPrincipal,
                balance = balance
            )
        )
        n += 1
    }

    val elapsedRows = rows.take(paymentsElapsed.coerceAtMost(rows.size))
    return MortgageScheduleResult(
        rows = rows,
        summary = MortgageSummary(
            loanAmount = loan,
            monthlyPayment = pi,
            estimatedMonthlyTotal = pi + taxIns + if (initialPmiActive) pmi else 0.0,
            termMonths = months,
            totalInterest = totalInterest,
            payoffDateMs = rows.lastOrNull()?.dateMs,
            paymentsRemaining = (rows.size - paymentsElapsed).coerceAtLeast(0),
            paymentsElapsed = paymentsElapsed,
            principalPaid = elapsedRows.sumOf { it.principal + it.extraPrincipal },
            interestPaid = elapsedRows.sumOf { it.interest }
        )
    )
}

fun scheduleForMortgageAccount(
    account: CreditAccount,
    nowMs: Long = System.currentTimeMillis()
): MortgageScheduleResult? {
    if (account.type != CreditAccountType.MORTGAGE) return null
    val principal = when {
        account.originalPrincipal > 0.0 -> account.originalPrincipal
        account.currentBalance > 0.0 -> account.currentBalance
        else -> return null
    }
    val termMonths = account.termMonths ?: return null
    if (termMonths <= 0) return null
    val constantEscrow =
        (if (account.propertyTaxEscrowed) account.monthlyPropertyTax() else 0.0) +
            (if (account.homeInsuranceEscrowed) account.monthlyHomeInsurance() else 0.0) +
            (if (account.hoaEscrowed) account.monthlyHoa.coerceAtLeast(0.0) else 0.0)
    val pmi = if (account.pmiEscrowed) account.monthlyPmi.coerceAtLeast(0.0) else 0.0
    return buildAmortizationSchedule(
        principal = principal,
        annualRate = account.apr,
        termMonths = termMonths,
        startDateMs = account.startDate ?: account.createdAt.takeIf { it > 0L },
        monthlyPayment = account.monthlyPaymentAmount,
        extraMonthlyPayment = account.extraMonthlyPrincipal,
        monthlyTaxInsurance = constantEscrow,
        monthlyPmi = pmi,
        pmiDropBalance = if (account.homePrice > 0.0) account.homePrice * 0.8 else 0.0,
        nowMs = nowMs
    )
}

fun currentMortgagePaymentSnapshot(
    account: CreditAccount,
    nowMs: Long = System.currentTimeMillis()
): MortgagePaymentSnapshot? {
    if (account.type != CreditAccountType.MORTGAGE) return null
    val pi = account.principalAndInterestPayment()
    if (pi <= 0.0 && account.currentBalance <= 0.0) return null
    val balance = account.currentBalance.coerceAtLeast(0.0)
    val interest = if (account.apr > 0.0) balance * (account.apr / 12.0) else 0.0
    var principal = (pi - interest).coerceAtLeast(0.0)
    if (principal > balance) principal = balance
    val extra = account.extraMonthlyPrincipal.coerceAtMost(
        (balance - principal).coerceAtLeast(0.0)
    )
    val pmi = if (account.pmiEscrowed) account.currentMonthlyPmi() else 0.0
    val escrow = account.escrowedMonthlyAmount() - pmi
    val schedule = scheduleForMortgageAccount(account, nowMs)
    return MortgagePaymentSnapshot(
        principal = principal,
        interest = interest,
        extraPrincipal = extra,
        escrow = escrow.coerceAtLeast(0.0),
        pmi = pmi,
        remainingBalance = balance,
        principalPaid = schedule?.summary?.principalPaid ?: 0.0,
        interestPaid = schedule?.summary?.interestPaid ?: 0.0,
        paymentsRemaining = schedule?.summary?.paymentsRemaining ?: 0,
        paymentsElapsed = schedule?.summary?.paymentsElapsed ?: 0,
        payoffDateMs = schedule?.summary?.payoffDateMs,
        monthlyPi = pi,
        servicerDraft = account.effectiveMonthlyPayment()
    )
}

fun suggestedEmergencyFundTarget(housingMonthly: Double, months: Int): Double =
    (housingMonthly.coerceAtLeast(0.0) * months).coerceAtLeast(0.0)

fun suggestedMaintenanceAnnual(homePrice: Double): Double =
    (homePrice.coerceAtLeast(0.0) * 0.01)
