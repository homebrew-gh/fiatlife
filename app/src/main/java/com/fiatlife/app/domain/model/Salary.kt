package com.fiatlife.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SalaryConfig(
    val id: String = "",
    val name: String = "My Salary",
    val payType: PayType = PayType.HOURLY,
    val hourlyRate: Double = 0.0,
    /** Annual salary used when payType is SALARY. */
    val annualSalary: Double = 0.0,
    val standardHoursPerPeriod: Double = 80.0,
    val overtimeHours: Double = 0.0,
    val overtimeMultiplier: Double = 1.5,
    val payFrequency: PayFrequency = PayFrequency.BIWEEKLY,
    val filingStatus: FilingStatus = FilingStatus.SINGLE,
    val state: String = "",
    val county: String = "",
    val allowances: Int = 0,
    val preTaxDeductions: List<Deduction> = emptyList(),
    val postTaxDeductions: List<Deduction> = emptyList(),
    val directDeposits: List<DirectDeposit> = emptyList(),
    val taxOverrides: TaxOverrides = TaxOverrides(),
    /** Optional anchor for exact paycheck counting (first paycheck date in the year). */
    val firstPaydayOfYearMillis: Long? = null,
    /** Effective-dated raises; the rate in effect = latest change on/before a date. */
    val payRateHistory: List<PayRateChange> = emptyList(),
    /** Logged actual paychecks for year-to-date tracking. */
    val paycheckLog: List<PaycheckLogEntry> = emptyList(),
    val updatedAt: Long = 0L
)

/** True when tax location still needs user input for accurate withholding. */
fun SalaryConfig.isTaxSetupIncomplete(): Boolean = state.isBlank()

@Serializable
enum class PayType {
    HOURLY,
    SALARY;

    val displayName: String
        get() = when (this) {
            HOURLY -> "Hourly"
            SALARY -> "Salaried"
        }
}

@Serializable
enum class PayFrequency(val periodsPerYear: Int) {
    WEEKLY(52),
    BIWEEKLY(26),
    SEMIMONTHLY(24),
    MONTHLY(12);
}

@Serializable
enum class FilingStatus {
    SINGLE,
    MARRIED_FILING_JOINTLY,
    MARRIED_FILING_SEPARATELY,
    HEAD_OF_HOUSEHOLD;

    val displayName: String
        get() = name.replace("_", " ").lowercase()
            .replaceFirstChar { it.uppercase() }
}
