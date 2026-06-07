package com.fiatlife.app.domain.model

import kotlinx.serialization.Serializable
import java.util.Calendar
import java.util.UUID

/** A single editable money line on a logged paystub (earning, tax, or deduction). */
@Serializable
data class PaycheckLineItem(
    val id: String = "",
    val label: String = "",
    val amount: Double = 0.0,
    /** Hours, only meaningful for earnings lines like Regular/Overtime. */
    val hours: Double? = null
)

/** An effective-dated pay-rate change (raise). */
@Serializable
data class PayRateChange(
    val id: String = "",
    val effectiveDate: Long = 0L,
    val payType: PayType? = null,
    val hourlyRate: Double? = null,
    val annualSalary: Double? = null,
    val standardHoursPerPeriod: Double? = null,
    val note: String? = null
)

/** A logged actual paycheck with an itemized breakdown. */
@Serializable
data class PaycheckLogEntry(
    val id: String = "",
    val payDate: Long = 0L,
    val grossPay: Double = 0.0,
    val netPay: Double = 0.0,
    val totalTaxes: Double? = null,
    val totalPreTaxDeductions: Double? = null,
    val totalPostTaxDeductions: Double? = null,
    val overtimeHours: Double? = null,
    val notes: String? = null,
    val earnings: List<PaycheckLineItem> = emptyList(),
    val taxes: List<PaycheckLineItem> = emptyList(),
    val preTaxDeductions: List<PaycheckLineItem> = emptyList(),
    val postTaxDeductions: List<PaycheckLineItem> = emptyList(),
    val employerContributions: List<PaycheckLineItem> = emptyList(),
    val attachmentHash: String? = null,
    val attachmentLabel: String? = null
)

data class EffectiveRate(
    val payType: PayType,
    val hourlyRate: Double,
    val annualSalary: Double,
    val standardHoursPerPeriod: Double
)

data class YtdBreakdownLine(
    val label: String,
    val amount: Double,
    val hours: Double = 0.0
)

data class YtdSummary(
    val year: Int,
    val source: Source,
    val paycheckCount: Int,
    val scheduledPaychecksYtd: Int,
    val scheduledPaychecksInYear: Int,
    val grossPay: Double,
    val netPay: Double,
    val totalTaxes: Double,
    val totalPreTaxDeductions: Double,
    val totalPostTaxDeductions: Double,
    val overtimeHours: Double,
    val earnings: List<YtdBreakdownLine>,
    val taxes: List<YtdBreakdownLine>,
    val preTaxDeductions: List<YtdBreakdownLine>,
    val postTaxDeductions: List<YtdBreakdownLine>,
    val employerContributions: List<YtdBreakdownLine>,
    val annualNetTarget: Double,
    val progressPercent: Double,
    val remainingPaychecks: Int,
    val expectedNetToDate: Double,
    val netVariance: Double,
    val projectedAnnualTaxes: Double
) {
    enum class Source { LOGGED, ESTIMATED }
}

/**
 * Year-to-date earnings/tax/deduction aggregation. Mirrors the web `summarizeYtd`
 * so both platforms present the same itemized summary.
 */
object SalarySummary {

    fun newId(): String = UUID.randomUUID().toString()

    /** The pay rate in effect at [whenMs], resolved from base config + raise history. */
    fun effectiveRateAt(config: SalaryConfig, whenMs: Long): EffectiveRate {
        val base = EffectiveRate(
            payType = config.payType,
            hourlyRate = config.hourlyRate,
            annualSalary = config.annualSalary,
            standardHoursPerPeriod = config.standardHoursPerPeriod
        )
        val applicable = config.payRateHistory
            .filter { it.effectiveDate <= whenMs }
            .maxByOrNull { it.effectiveDate }
            ?: return base
        return EffectiveRate(
            payType = applicable.payType ?: base.payType,
            hourlyRate = applicable.hourlyRate ?: base.hourlyRate,
            annualSalary = applicable.annualSalary ?: base.annualSalary,
            standardHoursPerPeriod = applicable.standardHoursPerPeriod
                ?: base.standardHoursPerPeriod
        )
    }

    fun periodRegularGross(rate: EffectiveRate, frequency: PayFrequency): Double {
        return if (rate.payType == PayType.SALARY) {
            rate.annualSalary / frequency.periodsPerYear
        } else {
            rate.hourlyRate * rate.standardHoursPerPeriod
        }
    }

    fun logsForYear(config: SalaryConfig, year: Int): List<PaycheckLogEntry> {
        return config.paycheckLog
            .filter { yearOf(it.payDate) == year }
            .sortedByDescending { it.payDate }
    }

    /** Whether scheduled paydays can be compared to the log for this pay frequency. */
    fun canDetectMissingPaychecks(config: SalaryConfig): Boolean =
        when (config.payFrequency) {
            PayFrequency.WEEKLY, PayFrequency.BIWEEKLY ->
                config.firstPaydayOfYearMillis != null
            else -> true
        }

    /** Scheduled paydays in [year] through [asOf] that have no matching log entry. */
    fun missingPaydaysForYear(
        config: SalaryConfig,
        year: Int,
        asOf: Long = System.currentTimeMillis()
    ): List<Long> {
        if (!canDetectMissingPaychecks(config)) return emptyList()
        val anchor = config.firstPaydayOfYearMillis ?: yearStart(year)
        val scheduled = enumeratePaydays(
            anchor,
            config.payFrequency,
            yearStart(year),
            minOf(asOf, yearEnd(year))
        )
        if (scheduled.isEmpty()) return emptyList()
        val loggedDays = logsForYear(config, year).map { startOfDay(it.payDate) }.toSet()
        return scheduled.filter { it !in loggedDays }
    }

    private fun yearOf(ms: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        return cal.get(Calendar.YEAR)
    }

    private fun yearStart(year: Int): Long =
        Calendar.getInstance().apply {
            clear(); set(year, Calendar.JANUARY, 1, 0, 0, 0)
        }.timeInMillis

    private fun yearEnd(year: Int): Long =
        Calendar.getInstance().apply {
            clear(); set(year, Calendar.DECEMBER, 31, 23, 59, 59)
        }.timeInMillis

    private fun startOfDay(ms: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = ms
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    fun countPaychecksInRange(
        firstPaydayMillis: Long,
        frequency: PayFrequency,
        rangeStart: Long,
        rangeEnd: Long
    ): Int = enumeratePaydays(firstPaydayMillis, frequency, rangeStart, rangeEnd).size

    fun enumeratePaydays(
        firstPaydayMillis: Long,
        frequency: PayFrequency,
        rangeStart: Long,
        rangeEnd: Long
    ): List<Long> {
        val days = mutableListOf<Long>()
        when (frequency) {
            PayFrequency.SEMIMONTHLY -> {
                val cursor = Calendar.getInstance().apply {
                    timeInMillis = rangeStart
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                while (cursor.timeInMillis <= rangeEnd) {
                    val y = cursor.get(Calendar.YEAR)
                    val m = cursor.get(Calendar.MONTH)
                    val mid = Calendar.getInstance().apply {
                        clear(); set(y, m, 15)
                    }.timeInMillis
                    val monthEnd = Calendar.getInstance().apply {
                        clear(); set(y, m, 1); set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    }.timeInMillis
                    if (mid in rangeStart..rangeEnd) days.add(mid)
                    if (monthEnd in rangeStart..rangeEnd) days.add(monthEnd)
                    cursor.add(Calendar.MONTH, 1)
                }
            }
            PayFrequency.MONTHLY -> {
                val cursor = Calendar.getInstance().apply {
                    timeInMillis = rangeStart
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                while (cursor.timeInMillis <= rangeEnd) {
                    val y = cursor.get(Calendar.YEAR)
                    val m = cursor.get(Calendar.MONTH)
                    val maxDay = cursor.getActualMaximum(Calendar.DAY_OF_MONTH)
                    val payday = Calendar.getInstance().apply {
                        clear(); set(y, m, minOf(maxDay, 15))
                    }.timeInMillis
                    if (payday in rangeStart..rangeEnd) days.add(payday)
                    cursor.add(Calendar.MONTH, 1)
                }
            }
            else -> {
                val stepMs = if (frequency == PayFrequency.WEEKLY)
                    7L * 24 * 60 * 60 * 1000 else 14L * 24 * 60 * 60 * 1000
                var payday = startOfDay(firstPaydayMillis)
                var i = 0
                while (i < 500 && payday <= rangeEnd) {
                    if (payday >= rangeStart) days.add(payday)
                    payday += stepMs
                    i++
                }
            }
        }
        return days.sorted()
    }

    fun scheduledPaychecksYtd(config: SalaryConfig, year: Int, asOf: Long = System.currentTimeMillis()): Int {
        val anchor = config.firstPaydayOfYearMillis
        val start = yearStart(year)
        val end = minOf(asOf, yearEnd(year))
        if (anchor == null) {
            val periods = config.payFrequency.periodsPerYear
            val elapsed = (end - start).toDouble() / (yearEnd(year) - start + 1)
            return (elapsed * periods).toInt().coerceAtLeast(1)
        }
        return countPaychecksInRange(anchor, config.payFrequency, start, end).coerceAtLeast(0)
    }

    fun scheduledPaychecksInYear(config: SalaryConfig, year: Int): Int {
        val anchor = config.firstPaydayOfYearMillis ?: return config.payFrequency.periodsPerYear
        return countPaychecksInRange(anchor, config.payFrequency, yearStart(year), yearEnd(year))
    }

    private fun mergeLines(groups: List<List<PaycheckLineItem>>): List<YtdBreakdownLine> {
        val map = LinkedHashMap<String, YtdBreakdownLine>()
        for (group in groups) {
            for (item in group) {
                val label = item.label.ifBlank { "Other" }
                val existing = map[label]
                if (existing != null) {
                    map[label] = existing.copy(
                        amount = existing.amount + item.amount,
                        hours = existing.hours + (item.hours ?: 0.0)
                    )
                } else {
                    map[label] = YtdBreakdownLine(label, item.amount, item.hours ?: 0.0)
                }
            }
        }
        return map.values.filter { it.amount != 0.0 || it.hours != 0.0 }
    }

    private fun entryOvertimeHours(e: PaycheckLogEntry): Double {
        e.overtimeHours?.let { return it }
        val ot = e.earnings.firstOrNull { Regex("overtime|^ot\\b", RegexOption.IGNORE_CASE).containsMatchIn(it.label) }
        return ot?.hours ?: 0.0
    }

    private fun entryEarnings(e: PaycheckLogEntry): List<PaycheckLineItem> =
        if (e.earnings.isNotEmpty()) e.earnings
        else listOf(PaycheckLineItem(e.id, "Earnings", e.grossPay))

    private fun entryTaxes(e: PaycheckLogEntry): List<PaycheckLineItem> =
        if (e.taxes.isNotEmpty()) e.taxes
        else e.totalTaxes?.let { listOf(PaycheckLineItem(e.id, "Taxes", it)) } ?: emptyList()

    fun summarize(
        config: SalaryConfig,
        calc: PaycheckCalculation,
        annual: AnnualProjection,
        year: Int,
        asOf: Long = System.currentTimeMillis()
    ): YtdSummary {
        val logs = logsForYear(config, year)
        val scheduledYtd = scheduledPaychecksYtd(config, year, asOf)
        val scheduledInYear = scheduledPaychecksInYear(config, year)
        val remaining = (scheduledInYear - scheduledYtd).coerceAtLeast(0)
        val perPaycheckNet = annual.perPaycheckNet

        if (logs.isNotEmpty()) {
            val grossPay = logs.sumOf { it.grossPay }
            val netPay = logs.sumOf { it.netPay }
            val totalTaxes = logs.sumOf { it.totalTaxes ?: 0.0 }
            val totalPre = logs.sumOf { it.totalPreTaxDeductions ?: 0.0 }
            val totalPost = logs.sumOf { it.totalPostTaxDeductions ?: 0.0 }
            val overtimeHours = logs.sumOf { entryOvertimeHours(it) }
            val expectedNet = perPaycheckNet * logs.size
            return YtdSummary(
                year = year,
                source = YtdSummary.Source.LOGGED,
                paycheckCount = logs.size,
                scheduledPaychecksYtd = scheduledYtd,
                scheduledPaychecksInYear = scheduledInYear,
                grossPay = grossPay,
                netPay = netPay,
                totalTaxes = totalTaxes,
                totalPreTaxDeductions = totalPre,
                totalPostTaxDeductions = totalPost,
                overtimeHours = overtimeHours,
                earnings = mergeLines(logs.map { entryEarnings(it) }),
                taxes = mergeLines(logs.map { entryTaxes(it) }),
                preTaxDeductions = mergeLines(logs.map { it.preTaxDeductions }),
                postTaxDeductions = mergeLines(logs.map { it.postTaxDeductions }),
                employerContributions = mergeLines(logs.map { it.employerContributions }),
                annualNetTarget = annual.annualNetPay,
                progressPercent = if (annual.annualNetPay > 0) netPay / annual.annualNetPay * 100 else 0.0,
                remainingPaychecks = remaining,
                expectedNetToDate = expectedNet,
                netVariance = netPay - expectedNet,
                projectedAnnualTaxes = annual.annualTotalTaxes
            )
        }

        val n = scheduledYtd
        val earnings = buildList {
            add(YtdBreakdownLine("Regular", calc.regularPay * n))
            if (calc.overtimePay > 0) {
                add(YtdBreakdownLine("Overtime", calc.overtimePay * n, config.overtimeHours * n))
            }
        }
        val taxes = buildList {
            add(YtdBreakdownLine("Federal income tax", calc.federalTax * n))
            add(YtdBreakdownLine("State income tax", calc.stateTax * n))
            if (calc.countyTax > 0) {
                add(YtdBreakdownLine(if (config.county.isNotBlank()) "${config.county} tax" else "Local tax", calc.countyTax * n))
            }
            add(YtdBreakdownLine("Social Security", calc.socialSecurity * n))
            add(YtdBreakdownLine("Medicare", calc.medicare * n))
        }.filter { it.amount != 0.0 }
        return YtdSummary(
            year = year,
            source = YtdSummary.Source.ESTIMATED,
            paycheckCount = n,
            scheduledPaychecksYtd = scheduledYtd,
            scheduledPaychecksInYear = scheduledInYear,
            grossPay = calc.grossPay * n,
            netPay = calc.netPay * n,
            totalTaxes = calc.totalTaxes * n,
            totalPreTaxDeductions = calc.totalPreTaxDeductions * n,
            totalPostTaxDeductions = calc.totalPostTaxDeductions * n,
            overtimeHours = config.overtimeHours * n,
            earnings = earnings,
            taxes = taxes,
            preTaxDeductions = calc.preTaxDeductionBreakdown.map { YtdBreakdownLine(it.name.ifBlank { "Pre-tax" }, it.amount * n) },
            postTaxDeductions = calc.postTaxDeductionBreakdown.map { YtdBreakdownLine(it.name.ifBlank { "Post-tax" }, it.amount * n) },
            employerContributions = emptyList(),
            annualNetTarget = annual.annualNetPay,
            progressPercent = if (annual.annualNetPay > 0) calc.netPay * n / annual.annualNetPay * 100 else 0.0,
            remainingPaychecks = remaining,
            expectedNetToDate = perPaycheckNet * n,
            netVariance = 0.0,
            projectedAnnualTaxes = annual.annualTotalTaxes
        )
    }

    /** Build itemized lines from a calculation (used for "prefill from calculator"). */
    fun lineItemsFromCalculation(calc: PaycheckCalculation, overtimeHours: Double?): LogLines {
        val earnings = buildList {
            add(PaycheckLineItem(newId(), "Regular", calc.regularPay))
            if (calc.overtimePay > 0) {
                add(PaycheckLineItem(newId(), "Overtime", calc.overtimePay, overtimeHours))
            }
        }
        val taxes = buildList {
            add(PaycheckLineItem(newId(), "Federal income tax", calc.federalTax))
            add(PaycheckLineItem(newId(), "State income tax", calc.stateTax))
            add(PaycheckLineItem(newId(), "Social Security", calc.socialSecurity))
            add(PaycheckLineItem(newId(), "Medicare", calc.medicare))
            if (calc.countyTax > 0) add(PaycheckLineItem(newId(), "Local tax", calc.countyTax))
        }
        return LogLines(
            earnings = earnings,
            taxes = taxes,
            preTaxDeductions = calc.preTaxDeductionBreakdown.map { PaycheckLineItem(newId(), it.name, it.amount) },
            postTaxDeductions = calc.postTaxDeductionBreakdown.map { PaycheckLineItem(newId(), it.name, it.amount) }
        )
    }

    data class LogLines(
        val earnings: List<PaycheckLineItem>,
        val taxes: List<PaycheckLineItem>,
        val preTaxDeductions: List<PaycheckLineItem>,
        val postTaxDeductions: List<PaycheckLineItem>
    )

    val earningsCategories = listOf(
        "Regular", "Overtime", "Bonus", "Commission", "Holiday", "PTO", "Tips", "Reimbursement", "Other"
    )
}
