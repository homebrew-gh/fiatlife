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

object PaycheckCalculator {

    fun calculate(config: SalaryConfig): PaycheckCalculation {
        val regularPay = config.hourlyRate * config.standardHoursPerPeriod
        val overtimePay = config.hourlyRate * config.overtimeMultiplier * config.overtimeHours
        val grossPay = regularPay + overtimePay

        val enabledPreTax = config.preTaxDeductions.filter { it.isEnabled }
        val enabledPostTax = config.postTaxDeductions.filter { it.isEnabled }

        val preTaxBreakdown = enabledPreTax.map { d ->
            DeductionLine(
                name = d.name,
                amount = if (d.isPercentage) grossPay * (d.amount / 100.0) else d.amount,
                category = d.category
            )
        }
        val totalPreTax = preTaxBreakdown.sumOf { it.amount }

        val taxableGross = grossPay - totalPreTax
        val periodsPerYear = config.payFrequency.periodsPerYear

        val annualTaxable = taxableGross * periodsPerYear
        val standardDeduction = FederalTaxTables.standardDeduction(config.filingStatus)
        val federalTaxableAnnual = (annualTaxable - standardDeduction).coerceAtLeast(0.0)

        val federalMarginalRate = findMarginalRate(federalTaxableAnnual, config.filingStatus)
        val federalTax = if (config.taxOverrides.isExemptFromFederal) 0.0
        else {
            val annualFederal = calculateFederalTax(federalTaxableAnnual, config.filingStatus)
            (annualFederal / periodsPerYear) + config.taxOverrides.federalAdditionalWithholding
        }

        val stateTaxRate = if (config.taxOverrides.isExemptFromState) 0.0
        else config.taxOverrides.customStateTaxRate ?: estimateStateTaxRate(config.state)
        val stateTax = if (config.taxOverrides.isExemptFromState) 0.0
        else {
            val annualState = annualTaxable * stateTaxRate
            (annualState / periodsPerYear) + config.taxOverrides.stateAdditionalWithholding
        }

        val countyTaxRate = if (config.taxOverrides.isExemptFromLocal) 0.0
        else config.taxOverrides.customCountyTaxRate ?: 0.0
        val countyTax = if (config.taxOverrides.isExemptFromLocal) 0.0
        else (annualTaxable * countyTaxRate) / periodsPerYear

        val annualGross = grossPay * periodsPerYear
        val socialSecurity = calculateSocialSecurity(taxableGross, annualGross, periodsPerYear)
        val medicare = calculateMedicare(taxableGross, annualGross, periodsPerYear, config.filingStatus)

        val totalTaxes = federalTax + stateTax + countyTax + socialSecurity + medicare

        val postTaxBreakdown = enabledPostTax.map { d ->
            DeductionLine(
                name = d.name,
                amount = if (d.isPercentage) grossPay * (d.amount / 100.0) else d.amount,
                category = d.category
            )
        }
        val totalPostTax = postTaxBreakdown.sumOf { it.amount }

        val netPay = grossPay - totalPreTax - totalTaxes - totalPostTax

        val depositAllocations = calculateDeposits(config.directDeposits, netPay)
        val allocatedTotal = depositAllocations.sumOf { it.calculatedAmount }

        return PaycheckCalculation(
            grossPay = grossPay,
            regularPay = regularPay,
            overtimePay = overtimePay,
            totalPreTaxDeductions = totalPreTax,
            preTaxDeductionBreakdown = preTaxBreakdown,
            federalTaxableIncome = federalTaxableAnnual / periodsPerYear,
            federalTax = federalTax,
            federalMarginalRate = federalMarginalRate,
            stateTax = stateTax,
            stateTaxRate = stateTaxRate,
            countyTax = countyTax,
            countyTaxRate = countyTaxRate,
            socialSecurity = socialSecurity,
            socialSecurityRate = FicaTaxRates.SOCIAL_SECURITY_RATE,
            medicare = medicare,
            medicareRate = FicaTaxRates.MEDICARE_RATE,
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

    private fun calculateFederalTax(taxableIncome: Double, status: FilingStatus): Double {
        val brackets = FederalTaxTables.bracketsFor(status)
        for (bracket in brackets.reversed()) {
            if (taxableIncome > bracket.min) {
                return bracket.baseTax + (taxableIncome - bracket.min) * bracket.rate
            }
        }
        return 0.0
    }

    private fun calculateSocialSecurity(
        periodTaxable: Double,
        annualGross: Double,
        periodsPerYear: Int
    ): Double {
        val annualSS = (annualGross.coerceAtMost(FicaTaxRates.SOCIAL_SECURITY_WAGE_BASE)) *
                FicaTaxRates.SOCIAL_SECURITY_RATE
        return annualSS / periodsPerYear
    }

    private fun calculateMedicare(
        periodTaxable: Double,
        annualGross: Double,
        periodsPerYear: Int,
        status: FilingStatus
    ): Double {
        val threshold = when (status) {
            FilingStatus.MARRIED_FILING_JOINTLY -> FicaTaxRates.ADDITIONAL_MEDICARE_THRESHOLD_JOINT
            else -> FicaTaxRates.ADDITIONAL_MEDICARE_THRESHOLD_SINGLE
        }
        val baseMedicare = annualGross * FicaTaxRates.MEDICARE_RATE
        val additionalMedicare = if (annualGross > threshold) {
            (annualGross - threshold) * FicaTaxRates.ADDITIONAL_MEDICARE_RATE
        } else 0.0
        return (baseMedicare + additionalMedicare) / periodsPerYear
    }

    private fun calculateDeposits(
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

    fun calculateAnnual(config: SalaryConfig, annualOvertimeHours: Double): AnnualProjection {
        val periodsPerYear = config.payFrequency.periodsPerYear
        val annualRegularPay = config.hourlyRate * config.standardHoursPerPeriod * periodsPerYear
        val annualOvertimePay = config.hourlyRate * config.overtimeMultiplier * annualOvertimeHours
        val annualGross = annualRegularPay + annualOvertimePay

        val perPeriodGross = annualGross / periodsPerYear

        val enabledPreTax = config.preTaxDeductions.filter { it.isEnabled }
        val enabledPostTax = config.postTaxDeductions.filter { it.isEnabled }

        val preTaxBreakdown = enabledPreTax.map { d ->
            val perPeriod = if (d.isPercentage) perPeriodGross * (d.amount / 100.0) else d.amount
            DeductionLine(name = d.name, amount = perPeriod * periodsPerYear, category = d.category)
        }
        val annualPreTax = preTaxBreakdown.sumOf { it.amount }

        val annualTaxable = annualGross - annualPreTax
        val standardDeduction = FederalTaxTables.standardDeduction(config.filingStatus)
        val federalTaxableAnnual = (annualTaxable - standardDeduction).coerceAtLeast(0.0)

        val annualFederalTax = if (config.taxOverrides.isExemptFromFederal) 0.0
        else {
            calculateFederalTax(federalTaxableAnnual, config.filingStatus) +
                    config.taxOverrides.federalAdditionalWithholding * periodsPerYear
        }

        val annualStateTax = if (config.taxOverrides.isExemptFromState) 0.0
        else {
            val rate = config.taxOverrides.customStateTaxRate ?: estimateStateTaxRate(config.state)
            annualTaxable * rate + config.taxOverrides.stateAdditionalWithholding * periodsPerYear
        }

        val annualCountyTax = if (config.taxOverrides.isExemptFromLocal) 0.0
        else {
            val rate = config.taxOverrides.customCountyTaxRate ?: 0.0
            annualTaxable * rate
        }

        val annualSS = annualGross.coerceAtMost(FicaTaxRates.SOCIAL_SECURITY_WAGE_BASE) *
                FicaTaxRates.SOCIAL_SECURITY_RATE

        val medicareThreshold = when (config.filingStatus) {
            FilingStatus.MARRIED_FILING_JOINTLY -> FicaTaxRates.ADDITIONAL_MEDICARE_THRESHOLD_JOINT
            else -> FicaTaxRates.ADDITIONAL_MEDICARE_THRESHOLD_SINGLE
        }
        val annualMedicare = annualGross * FicaTaxRates.MEDICARE_RATE +
                if (annualGross > medicareThreshold)
                    (annualGross - medicareThreshold) * FicaTaxRates.ADDITIONAL_MEDICARE_RATE
                else 0.0

        val annualTotalTaxes = annualFederalTax + annualStateTax + annualCountyTax + annualSS + annualMedicare

        val postTaxBreakdown = enabledPostTax.map { d ->
            val perPeriod = if (d.isPercentage) perPeriodGross * (d.amount / 100.0) else d.amount
            DeductionLine(name = d.name, amount = perPeriod * periodsPerYear, category = d.category)
        }
        val annualPostTax = postTaxBreakdown.sumOf { it.amount }

        val annualNet = annualGross - annualPreTax - annualTotalTaxes - annualPostTax

        val marginalRate = findMarginalRate(federalTaxableAnnual, config.filingStatus)

        return AnnualProjection(
            annualRegularPay = annualRegularPay,
            annualOvertimePay = annualOvertimePay,
            annualGrossPay = annualGross,
            annualPreTaxDeductions = annualPreTax,
            preTaxDeductionBreakdown = preTaxBreakdown,
            annualFederalTaxableIncome = federalTaxableAnnual,
            annualFederalTax = annualFederalTax,
            annualStateTax = annualStateTax,
            annualCountyTax = annualCountyTax,
            annualSocialSecurity = annualSS,
            annualMedicare = annualMedicare,
            annualTotalTaxes = annualTotalTaxes,
            annualPostTaxDeductions = annualPostTax,
            postTaxDeductionBreakdown = postTaxBreakdown,
            annualNetPay = annualNet,
            effectiveTaxRate = if (annualGross > 0) annualTotalTaxes / annualGross else 0.0,
            marginalFederalRate = marginalRate,
            overtimeHoursUsed = annualOvertimeHours,
            perPaycheckNet = if (periodsPerYear > 0) annualNet / periodsPerYear else 0.0
        )
    }

    private fun findMarginalRate(federalTaxableIncome: Double, status: FilingStatus): Double {
        val brackets = FederalTaxTables.bracketsFor(status)
        for (bracket in brackets.reversed()) {
            if (federalTaxableIncome > bracket.min) return bracket.rate
        }
        return brackets.first().rate
    }

    @Suppress("unused")
    private fun estimateStateTaxRate(state: String): Double = when (state.uppercase()) {
        "AL" -> 0.050; "AK" -> 0.000; "AZ" -> 0.025; "AR" -> 0.044
        "CA" -> 0.093; "CO" -> 0.044; "CT" -> 0.050; "DE" -> 0.066
        "FL" -> 0.000; "GA" -> 0.055; "HI" -> 0.075; "ID" -> 0.058
        "IL" -> 0.0495; "IN" -> 0.0315; "IA" -> 0.060; "KS" -> 0.057
        "KY" -> 0.040; "LA" -> 0.0425; "ME" -> 0.0715; "MD" -> 0.0575
        "MA" -> 0.050; "MI" -> 0.0425; "MN" -> 0.0985; "MS" -> 0.050
        "MO" -> 0.048; "MT" -> 0.0575; "NE" -> 0.0564; "NV" -> 0.000
        "NH" -> 0.000; "NJ" -> 0.0897; "NM" -> 0.059; "NY" -> 0.0685
        "NC" -> 0.045; "ND" -> 0.0195; "OH" -> 0.040; "OK" -> 0.0475
        "OR" -> 0.099; "PA" -> 0.0307; "RI" -> 0.0599; "SC" -> 0.065
        "SD" -> 0.000; "TN" -> 0.000; "TX" -> 0.000; "UT" -> 0.0465
        "VT" -> 0.0875; "VA" -> 0.0575; "WA" -> 0.000; "WV" -> 0.055
        "WI" -> 0.0765; "WY" -> 0.000; "DC" -> 0.0895
        else -> 0.05
    }
}
