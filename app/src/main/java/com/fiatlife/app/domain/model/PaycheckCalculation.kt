package com.fiatlife.app.domain.model

data class PaycheckCalculation(
    val grossPay: Double = 0.0,
    val regularPay: Double = 0.0,
    val overtimePay: Double = 0.0,
    val totalPreTaxDeductions: Double = 0.0,
    val preTaxDeductionBreakdown: List<DeductionLine> = emptyList(),
    val federalTaxableIncome: Double = 0.0,
    val federalTax: Double = 0.0,
    val federalMarginalRate: Double = 0.0,
    /** Bracket-based federal withholding ÷ taxable wages (what a custom rate replaces). */
    val federalEffectiveRate: Double = 0.0,
    val stateTax: Double = 0.0,
    val stateTaxRate: Double = 0.0,
    val countyTax: Double = 0.0,
    val countyTaxRate: Double = 0.0,
    val socialSecurity: Double = 0.0,
    val socialSecurityRate: Double = FicaTaxRates.SOCIAL_SECURITY_RATE,
    val medicare: Double = 0.0,
    val medicareRate: Double = FicaTaxRates.MEDICARE_RATE,
    val totalTaxes: Double = 0.0,
    val totalPostTaxDeductions: Double = 0.0,
    val postTaxDeductionBreakdown: List<DeductionLine> = emptyList(),
    val netPay: Double = 0.0,
    val annualizedGross: Double = 0.0,
    val annualizedNet: Double = 0.0,
    val effectiveTaxRate: Double = 0.0,
    val depositAllocations: List<DepositAllocation> = emptyList(),
    val unallocatedAmount: Double = 0.0
)

data class DeductionLine(
    val name: String,
    val amount: Double,
    val category: DeductionCategory
)

data class DepositAllocation(
    val deposit: DirectDeposit,
    val calculatedAmount: Double
)

data class AnnualProjection(
    val annualRegularPay: Double = 0.0,
    val annualOvertimePay: Double = 0.0,
    val annualGrossPay: Double = 0.0,
    val annualPreTaxDeductions: Double = 0.0,
    val preTaxDeductionBreakdown: List<DeductionLine> = emptyList(),
    val annualFederalTaxableIncome: Double = 0.0,
    val annualFederalTax: Double = 0.0,
    val annualStateTax: Double = 0.0,
    val annualCountyTax: Double = 0.0,
    val annualSocialSecurity: Double = 0.0,
    val annualMedicare: Double = 0.0,
    val annualTotalTaxes: Double = 0.0,
    val annualPostTaxDeductions: Double = 0.0,
    val postTaxDeductionBreakdown: List<DeductionLine> = emptyList(),
    val annualNetPay: Double = 0.0,
    val effectiveTaxRate: Double = 0.0,
    val marginalFederalRate: Double = 0.0,
    val overtimeHoursUsed: Double = 0.0,
    val perPaycheckNet: Double = 0.0
)

/** Mirrors web `calculatePaycheck` / `calculateAnnual` in `lib/salary.ts`. */
object PaycheckCalculator {

    private data class AnnualWithholding(
        val federal: Double,
        val state: Double,
        val county: Double,
        val socialSecurity: Double,
        val medicare: Double,
        val federalTaxable: Double,
        val federalMarginalRate: Double,
        val federalEffectiveRate: Double,
        val stateRate: Double,
        val countyRate: Double,
        val socialSecurityRate: Double,
        val medicareRate: Double
    ) {
        val total: Double get() = federal + state + county + socialSecurity + medicare
    }

    /**
     * Annual withholding for a year of wages. Income taxes apply to gross minus all
     * pre-tax deductions; Social Security and Medicare apply to gross minus only the
     * FICA-exempt ones (a traditional 401(k) still owes FICA).
     */
    private fun annualWithholding(
        config: SalaryConfig,
        annualGross: Double,
        annualPreTax: Double,
        annualFicaExempt: Double,
        periods: Int,
        year: Int
    ): AnnualWithholding {
        val overrides = config.taxOverrides
        val annualTaxable = (annualGross - annualPreTax).coerceAtLeast(0.0)
        val federalTaxable =
            (annualTaxable - FederalTaxTables.standardDeduction(config.filingStatus, year)).coerceAtLeast(0.0)
        val customFederal = overrides.customFederalTaxRate
        val bracketFederal = FederalTaxTables.calculateTax(federalTaxable, config.filingStatus, year)
        val baseFederal = if (customFederal != null) annualTaxable * customFederal else bracketFederal
        val federal = if (overrides.isExemptFromFederal) 0.0
        else baseFederal + overrides.federalAdditionalWithholding * periods

        val stateRate = if (overrides.isExemptFromState) 0.0
        else overrides.customStateTaxRate ?: FederalTaxTables.estimateStateTaxRate(config.state)
        val state = if (overrides.isExemptFromState) 0.0
        else annualTaxable * stateRate + overrides.stateAdditionalWithholding * periods

        val countyRate = if (overrides.isExemptFromLocal) 0.0 else overrides.customCountyTaxRate ?: 0.0
        val county = annualTaxable * countyRate

        val ficaWages = (annualGross - annualFicaExempt).coerceAtLeast(0.0)
        val ssRate = overrides.customSocialSecurityRate ?: FicaTaxRates.SOCIAL_SECURITY_RATE
        val socialSecurity = ficaWages.coerceAtMost(FederalTaxTables.socialSecurityWageBase(year)) * ssRate
        val medRate = overrides.customMedicareRate ?: FicaTaxRates.MEDICARE_RATE
        val threshold = FicaTaxRates.ADDITIONAL_MEDICARE_WITHHOLDING_THRESHOLD
        val medicare = ficaWages * medRate +
            if (overrides.customMedicareRate == null && ficaWages > threshold)
                (ficaWages - threshold) * FicaTaxRates.ADDITIONAL_MEDICARE_RATE
            else 0.0

        return AnnualWithholding(
            federal = federal,
            state = state,
            county = county,
            socialSecurity = socialSecurity,
            medicare = medicare,
            federalTaxable = federalTaxable,
            federalMarginalRate = customFederal
                ?: FederalTaxTables.marginalRate(federalTaxable, config.filingStatus, year),
            federalEffectiveRate = if (annualTaxable > 0) bracketFederal / annualTaxable else 0.0,
            stateRate = stateRate,
            countyRate = countyRate,
            socialSecurityRate = ssRate,
            medicareRate = medRate
        )
    }

    private fun deductionAmount(d: Deduction, grossPay: Double): Double =
        if (d.isPercentage) grossPay * (d.amount / 100.0) else d.amount

    private fun deductionLines(deductions: List<Deduction>, grossPay: Double): List<DeductionLine> =
        deductions.filter { it.isEnabled }.map { d ->
            DeductionLine(name = d.name, amount = deductionAmount(d, grossPay), category = d.category)
        }

    private fun ficaExemptPreTax(deductions: List<Deduction>, grossPay: Double): Double =
        deductions.filter { it.isEnabled && it.isFicaExempt }.sumOf { deductionAmount(it, grossPay) }

    fun calculate(config: SalaryConfig, asOf: Long = System.currentTimeMillis()): PaycheckCalculation {
        val rate = SalarySummary.effectiveRateAt(config, asOf)
        val regularPay = SalarySummary.periodRegularGross(rate, config.payFrequency)
        val overtimePay = rate.hourlyRate * config.overtimeMultiplier * config.overtimeHours
        val grossPay = regularPay + overtimePay
        val periodsPerYear = config.payFrequency.periodsPerYear

        val preTaxBreakdown = deductionLines(config.preTaxDeductions, grossPay)
        val totalPreTax = preTaxBreakdown.sumOf { it.amount }
        val annualGross = grossPay * periodsPerYear
        val taxes = annualWithholding(
            config,
            annualGross = annualGross,
            annualPreTax = totalPreTax * periodsPerYear,
            annualFicaExempt = ficaExemptPreTax(config.preTaxDeductions, grossPay) * periodsPerYear,
            periods = periodsPerYear,
            year = SalarySummary.yearOf(asOf)
        )
        val totalTaxes = taxes.total / periodsPerYear

        val postTaxBreakdown = deductionLines(config.postTaxDeductions, grossPay)
        val totalPostTax = postTaxBreakdown.sumOf { it.amount }

        val netPay = grossPay - totalPreTax - totalTaxes - totalPostTax

        val depositAllocations = calculateDepositAllocations(config.directDeposits, netPay)
        val allocatedTotal = depositAllocations.sumOf { it.calculatedAmount }

        return PaycheckCalculation(
            grossPay = grossPay,
            regularPay = regularPay,
            overtimePay = overtimePay,
            totalPreTaxDeductions = totalPreTax,
            preTaxDeductionBreakdown = preTaxBreakdown,
            federalTaxableIncome = taxes.federalTaxable / periodsPerYear,
            federalTax = taxes.federal / periodsPerYear,
            federalMarginalRate = taxes.federalMarginalRate,
            federalEffectiveRate = taxes.federalEffectiveRate,
            stateTax = taxes.state / periodsPerYear,
            stateTaxRate = taxes.stateRate,
            countyTax = taxes.county / periodsPerYear,
            countyTaxRate = taxes.countyRate,
            socialSecurity = taxes.socialSecurity / periodsPerYear,
            socialSecurityRate = taxes.socialSecurityRate,
            medicare = taxes.medicare / periodsPerYear,
            medicareRate = taxes.medicareRate,
            totalTaxes = totalTaxes,
            totalPostTaxDeductions = totalPostTax,
            postTaxDeductionBreakdown = postTaxBreakdown,
            netPay = netPay,
            annualizedGross = annualGross,
            annualizedNet = netPay * periodsPerYear,
            effectiveTaxRate = if (grossPay > 0) totalTaxes / grossPay else 0.0,
            depositAllocations = depositAllocations,
            unallocatedAmount = netPay - allocatedTotal
        )
    }

    fun calculateDepositAllocations(
        deposits: List<DirectDeposit>,
        netPay: Double
    ): List<DepositAllocation> {
        if (deposits.isEmpty()) return emptyList()

        val sorted = deposits.sortedBy { it.sortOrder }
        var remaining = netPay
        val allocations = mutableListOf<DepositAllocation>()

        val remainderDeposit = sorted.find { it.isRemainder }
        val fixedDeposits = sorted.filter { !it.isRemainder }

        for (deposit in fixedDeposits) {
            val amount = if (deposit.isPercentage) {
                netPay * (deposit.amount / 100.0)
            } else {
                deposit.amount
            }
            val allocated = amount.coerceAtMost(remaining).coerceAtLeast(0.0)
            remaining -= allocated
            allocations.add(DepositAllocation(deposit, allocated))
        }

        if (remainderDeposit != null) {
            allocations.add(DepositAllocation(remainderDeposit, remaining.coerceAtLeast(0.0)))
        }

        return allocations.sortedBy { it.deposit.sortOrder }
    }

    /** Sum of base/regular gross across a year's paydays, honoring mid-year raises. */
    private fun annualRegularPayForYear(config: SalaryConfig, year: Int): Double {
        val anchor = config.firstPaydayOfYearMillis
        val paydays = if (anchor != null) {
            SalarySummary.enumeratePaydays(
                anchor,
                config.payFrequency,
                SalarySummary.yearStart(year),
                SalarySummary.yearEnd(year)
            )
        } else emptyList()
        if (paydays.isEmpty()) {
            val rate = SalarySummary.effectiveRateAt(config, System.currentTimeMillis())
            return SalarySummary.periodRegularGross(rate, config.payFrequency) *
                config.payFrequency.periodsPerYear
        }
        return paydays.sumOf {
            SalarySummary.periodRegularGross(SalarySummary.effectiveRateAt(config, it), config.payFrequency)
        }
    }

    fun calculateAnnual(
        config: SalaryConfig,
        annualOvertimeHours: Double,
        year: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    ): AnnualProjection {
        val periodsPerYear = SalarySummary.scheduledPaychecksInYear(config, year)
        val annualRegularPay = annualRegularPayForYear(config, year)
        val latestRate = SalarySummary.effectiveRateAt(config, System.currentTimeMillis())
        val annualOvertimePay = latestRate.hourlyRate * config.overtimeMultiplier * annualOvertimeHours
        val annualGross = annualRegularPay + annualOvertimePay
        val perPeriodGross = annualGross / periodsPerYear

        fun annualize(deductions: List<Deduction>) =
            deductionLines(deductions, perPeriodGross).map { it.copy(amount = it.amount * periodsPerYear) }

        val preTaxBreakdown = annualize(config.preTaxDeductions)
        val annualPreTax = preTaxBreakdown.sumOf { it.amount }
        val taxes = annualWithholding(
            config,
            annualGross = annualGross,
            annualPreTax = annualPreTax,
            annualFicaExempt = ficaExemptPreTax(config.preTaxDeductions, perPeriodGross) * periodsPerYear,
            periods = periodsPerYear,
            year = year
        )
        val postTaxBreakdown = annualize(config.postTaxDeductions)
        val annualPostTax = postTaxBreakdown.sumOf { it.amount }
        val annualNet = annualGross - annualPreTax - taxes.total - annualPostTax

        return AnnualProjection(
            annualRegularPay = annualRegularPay,
            annualOvertimePay = annualOvertimePay,
            annualGrossPay = annualGross,
            annualPreTaxDeductions = annualPreTax,
            preTaxDeductionBreakdown = preTaxBreakdown,
            annualFederalTaxableIncome = taxes.federalTaxable,
            annualFederalTax = taxes.federal,
            annualStateTax = taxes.state,
            annualCountyTax = taxes.county,
            annualSocialSecurity = taxes.socialSecurity,
            annualMedicare = taxes.medicare,
            annualTotalTaxes = taxes.total,
            annualPostTaxDeductions = annualPostTax,
            postTaxDeductionBreakdown = postTaxBreakdown,
            annualNetPay = annualNet,
            effectiveTaxRate = if (annualGross > 0) taxes.total / annualGross else 0.0,
            marginalFederalRate = taxes.federalMarginalRate,
            overtimeHoursUsed = annualOvertimeHours,
            perPaycheckNet = annualNet / periodsPerYear
        )
    }
}
