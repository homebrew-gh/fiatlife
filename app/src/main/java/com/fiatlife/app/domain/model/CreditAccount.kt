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
    val currentBalance: Double = 0.0,
    val dueDay: Int = 1,
    val linkedBillId: String? = null,
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
    val endDate: Long? = null
) {
    /** Minimum payment due (revolving). */
    fun minimumDue(): Double = when (minimumPaymentType) {
        CreditCardMinPaymentType.FIXED -> minimumPaymentValue.coerceAtLeast(0.0)
        CreditCardMinPaymentType.PERCENT_OF_BALANCE -> (currentBalance * (minimumPaymentValue / 100.0)).coerceAtLeast(0.0)
        CreditCardMinPaymentType.FULL_BALANCE -> currentBalance.coerceAtLeast(0.0)
    }

    /** Monthly payment to use for totals and display. Revolving: only count when balance > 0 (use minimum due). Amortizing: fixed monthly payment. */
    fun effectiveMonthlyPayment(): Double = when {
        type.isRevolving -> if (currentBalance > 0) minimumDue() else 0.0
        type.isAmortizing -> monthlyPaymentAmount ?: 0.0
        else -> 0.0
    }
}
