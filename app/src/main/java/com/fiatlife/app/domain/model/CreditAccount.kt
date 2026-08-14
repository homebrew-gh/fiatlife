package com.fiatlife.app.domain.model

import kotlinx.serialization.Serializable

/** Type of credit/loan account. */
@Serializable
enum class CreditAccountType {
    CREDIT_CARD,
    MORTGAGE,
    CAR_LOAN,
    STUDENT_LOAN,
    PERSONAL_LOAN,
    HELOC,
    RETIREMENT_LOAN,
    OTHER;

    val displayName: String
        get() = when (this) {
            CREDIT_CARD -> "Credit Card"
            MORTGAGE -> "Mortgage"
            CAR_LOAN -> "Car Loan"
            STUDENT_LOAN -> "Student Loan"
            PERSONAL_LOAN -> "Personal Loan"
            HELOC -> "HELOC"
            RETIREMENT_LOAN -> "401k/IRA Loan"
            OTHER -> "Other"
        }

    /** Revolving: has credit limit and minimum payment. */
    val isRevolving: Boolean
        get() = this == CREDIT_CARD || this == HELOC

    /** Amortizing: has original principal, term, fixed payment. */
    val isAmortizing: Boolean
        get() = this == MORTGAGE || this == CAR_LOAN || this == STUDENT_LOAN ||
            this == PERSONAL_LOAN || this == RETIREMENT_LOAN
}

@Serializable
enum class PromotionAppliesTo {
    PURCHASES,
    BALANCE_TRANSFER,
    BOTH
}

data class CreditStatementUpdate(
    val statementBalance: Double,
    val statementBalanceAsOfMillis: Long,
    val statementAmountDue: Double? = null,
    val dueDay: Int,
    val paymentAmount: Double = 0.0,
    val balanceAfterPayment: Double = statementBalance
)

data class DueUrgency(
    val days: Int,
    val overdue: Boolean
)

/**
 * A line of credit or loan account (credit card, mortgage, car loan, etc.).
 * Common fields for all types; revolving and amortizing fields used by type.
 */
@Serializable
data class CreditAccount(
    val id: String = "",
    val name: String = "",
    val type: CreditAccountType = CreditAccountType.OTHER,
    val institution: String = "",
    val accountNumberLast4: String = "",
    val apr: Double = 0.0,
    /** Ongoing APR after a promotion; null falls back to legacy [apr]. */
    val standardApr: Double? = null,
    val promotionalApr: Double? = null,
    val promotionalAprEndDate: Long? = null,
    val promotionAppliesTo: PromotionAppliesTo? = null,
    val deferredInterest: Boolean = false,
    val currentBalance: Double = 0.0,
    val statementBalanceAsOfMillis: Long? = null,
    /** Issuer-reported amount due; null uses the configured minimum formula. */
    val statementAmountDue: Double? = null,
    val dueDay: Int = 1,
    val linkedBillId: String? = null,
    val annualFeeLinkedBillId: String? = null,
    val notes: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val statementEntries: List<StatementEntry> = emptyList(),
    val attachmentHashes: List<String> = emptyList(),
    // Revolving (credit card, HELOC)
    val creditLimit: Double = 0.0,
    val minimumPaymentType: CreditCardMinPaymentType = CreditCardMinPaymentType.PERCENT_OF_BALANCE,
    val minimumPaymentValue: Double = 2.0,
    // Amortizing (mortgage, car, student, personal, retirement)
    val originalPrincipal: Double = 0.0,
    val termMonths: Int? = null,
    val monthlyPaymentAmount: Double? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
    // Credit card annual membership fee
    val annualFeeAmount: Double = 0.0,
    val annualFeeRenewalDateMillis: Long? = null,
    val annualFeeFrequency: BillFrequency = BillFrequency.ANNUALLY,
    // Mortgage housing (ignored for other types)
    val homePrice: Double = 0.0,
    val annualPropertyTax: Double = 0.0,
    val annualHomeInsurance: Double = 0.0,
    val monthlyHoa: Double = 0.0,
    val monthlyPmi: Double = 0.0,
    val extraMonthlyPrincipal: Double = 0.0,
    val propertyTaxEscrowed: Boolean = true,
    val homeInsuranceEscrowed: Boolean = true,
    val hoaEscrowed: Boolean = false,
    val pmiEscrowed: Boolean = true,
    val linkedPropertyTaxBillId: String? = null,
    val linkedHomeInsuranceBillId: String? = null,
    val linkedHoaBillId: String? = null,
    val linkedPmiBillId: String? = null
) {
    /** Minimum payment due (revolving). */
    fun minimumDue(): Double = when (minimumPaymentType) {
        CreditCardMinPaymentType.FIXED -> minimumPaymentValue.coerceAtLeast(0.0)
        CreditCardMinPaymentType.PERCENT_OF_BALANCE -> (currentBalance * (minimumPaymentValue / 100.0)).coerceAtLeast(0.0)
        CreditCardMinPaymentType.FULL_BALANCE -> currentBalance.coerceAtLeast(0.0)
    }

    fun statementOverrideActive(asOfMillis: Long = System.currentTimeMillis()): Boolean {
        val override = statementAmountDue ?: return false
        if (override.isNaN()) return false
        val statementAsOf = statementBalanceAsOfMillis ?: return true
        val statementCal = java.util.Calendar.getInstance().apply { timeInMillis = statementAsOf }
        var cycleDue = dueDateInMonth(
            dueDay,
            statementCal.get(java.util.Calendar.YEAR),
            statementCal.get(java.util.Calendar.MONTH)
        )
        if (statementAsOf > endOfDayMillis(cycleDue)) {
            statementCal.add(java.util.Calendar.MONTH, 1)
            cycleDue = dueDateInMonth(
                dueDay,
                statementCal.get(java.util.Calendar.YEAR),
                statementCal.get(java.util.Calendar.MONTH)
            )
        }
        return asOfMillis <= endOfDayMillis(cycleDue)
    }

    fun effectiveAmountDue(asOfMillis: Long = System.currentTimeMillis()): Double =
        if (statementOverrideActive(asOfMillis)) {
            statementAmountDue?.coerceAtLeast(0.0) ?: minimumDue()
        } else if (type.isAmortizing) {
            effectiveMonthlyPayment()
        } else {
            minimumDue()
        }

    fun dueUrgency(
        paidThisCycle: Boolean = false,
        asOfMillis: Long = System.currentTimeMillis()
    ): DueUrgency {
        val now = java.util.Calendar.getInstance().apply { timeInMillis = asOfMillis }
        val thisMonthDue = dueDateInMonth(
            dueDay,
            now.get(java.util.Calendar.YEAR),
            now.get(java.util.Calendar.MONTH)
        )
        if (currentBalance > 0.0 && asOfMillis > endOfDayMillis(thisMonthDue) && !paidThisCycle) {
            val days = ((asOfMillis - startOfDayMillis(thisMonthDue) + 86_400_000L - 1) / 86_400_000L)
                .toInt()
                .coerceAtLeast(1)
            return DueUrgency(days, overdue = true)
        }
        val nextDue = if (asOfMillis > endOfDayMillis(thisMonthDue)) {
            now.add(java.util.Calendar.MONTH, 1)
            dueDateInMonth(
                dueDay,
                now.get(java.util.Calendar.YEAR),
                now.get(java.util.Calendar.MONTH)
            )
        } else {
            thisMonthDue
        }
        val days = ((startOfDayMillis(nextDue) - startOfDayMillis(asOfMillis) + 86_400_000L - 1) /
            86_400_000L).toInt().coerceAtLeast(0)
        return DueUrgency(days, overdue = false)
    }

    fun isPromotionActive(asOfMillis: Long = System.currentTimeMillis()): Boolean =
        promotionalApr != null &&
            promotionalAprEndDate != null &&
            promotionalAprEndDate >= asOfMillis

    fun effectiveApr(asOfMillis: Long = System.currentTimeMillis()): Double =
        if (isPromotionActive(asOfMillis)) {
            promotionalApr?.coerceAtLeast(0.0) ?: 0.0
        } else {
            (standardApr ?: apr).coerceAtLeast(0.0)
        }

    fun monthsUntilPromotionEnds(asOfMillis: Long = System.currentTimeMillis()): Int? {
        val endMillis = promotionalAprEndDate
        if (!isPromotionActive(asOfMillis) || endMillis == null) return null
        val start = java.util.Calendar.getInstance().apply { timeInMillis = asOfMillis }
        val end = java.util.Calendar.getInstance().apply { timeInMillis = endMillis }
        val monthDifference =
            (end.get(java.util.Calendar.YEAR) - start.get(java.util.Calendar.YEAR)) * 12 +
                end.get(java.util.Calendar.MONTH) - start.get(java.util.Calendar.MONTH)
        return (monthDifference +
            if (end.get(java.util.Calendar.DAY_OF_MONTH) >=
                start.get(java.util.Calendar.DAY_OF_MONTH)
            ) 1 else 0).coerceAtLeast(1)
    }

    fun paymentToClearPromotion(asOfMillis: Long = System.currentTimeMillis()): Double? {
        val months = monthsUntilPromotionEnds(asOfMillis) ?: return null
        if (currentBalance <= 0.0) return null
        return currentBalance / months
    }

    fun monthlyPropertyTax(): Double = (annualPropertyTax / 12.0).coerceAtLeast(0.0)

    fun monthlyHomeInsurance(): Double = (annualHomeInsurance / 12.0).coerceAtLeast(0.0)

    fun pmiActive(asOfBalance: Double = currentBalance): Boolean {
        if (monthlyPmi <= 0.0) return false
        if (homePrice > 0.0) return asOfBalance > homePrice * 0.8
        return true
    }

    fun currentMonthlyPmi(): Double =
        if (pmiActive()) monthlyPmi.coerceAtLeast(0.0) else 0.0

    fun principalAndInterestPayment(): Double =
        if (type.isAmortizing) (monthlyPaymentAmount ?: 0.0).coerceAtLeast(0.0) else 0.0

    /** Escrowed tax, insurance, HOA, and active PMI included in the servicer draft. */
    fun escrowedMonthlyAmount(): Double {
        if (type != CreditAccountType.MORTGAGE) return 0.0
        var sum = 0.0
        if (propertyTaxEscrowed) sum += monthlyPropertyTax()
        if (homeInsuranceEscrowed) sum += monthlyHomeInsurance()
        if (hoaEscrowed) sum += monthlyHoa.coerceAtLeast(0.0)
        if (pmiEscrowed) sum += currentMonthlyPmi()
        return sum
    }

    /** Full housing cost (PITI) regardless of escrow. Excludes extra principal. */
    fun housingPitiMonthly(): Double {
        if (type != CreditAccountType.MORTGAGE) return 0.0
        return principalAndInterestPayment() +
            monthlyPropertyTax() +
            monthlyHomeInsurance() +
            monthlyHoa.coerceAtLeast(0.0) +
            currentMonthlyPmi()
    }

    fun housingSatelliteBillIds(): List<String> = listOfNotNull(
        linkedPropertyTaxBillId,
        linkedHomeInsuranceBillId,
        linkedHoaBillId,
        linkedPmiBillId
    )

    /** Monthly payment to use for totals and display. Revolving: only count when balance > 0 (use minimum due). Mortgage: P&I + extra principal + escrow. Other amortizing: fixed monthly payment. */
    fun effectiveMonthlyPayment(): Double = when {
        type.isRevolving -> if (currentBalance > 0) effectiveAmountDue() else 0.0
        type == CreditAccountType.MORTGAGE ->
            principalAndInterestPayment() +
                extraMonthlyPrincipal.coerceAtLeast(0.0) +
                escrowedMonthlyAmount()
        type.isAmortizing -> monthlyPaymentAmount ?: 0.0
        else -> 0.0
    }
}

private fun dueDateInMonth(dueDay: Int, year: Int, month: Int): Long {
    val cal = java.util.Calendar.getInstance().apply {
        clear()
        set(java.util.Calendar.YEAR, year)
        set(java.util.Calendar.MONTH, month)
        set(java.util.Calendar.DAY_OF_MONTH, 1)
        set(
            java.util.Calendar.DAY_OF_MONTH,
            dueDay.coerceIn(1, getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
        )
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

private fun startOfDayMillis(millis: Long): Long {
    val cal = java.util.Calendar.getInstance().apply {
        timeInMillis = millis
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

private fun endOfDayMillis(dayStartMillis: Long): Long =
    startOfDayMillis(dayStartMillis) + 86_400_000L - 1

