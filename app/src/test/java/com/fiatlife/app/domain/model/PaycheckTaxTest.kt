package com.fiatlife.app.domain.model

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Same cases as the web check, so both platforms agree on the numbers. */
class PaycheckTaxTest {
    private lateinit var originalZone: TimeZone

    @Before
    fun useDaylightSavingZone() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
    }

    @After
    fun restoreZone() {
        TimeZone.setDefault(originalZone)
    }

    private fun date(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply { clear(); set(year, month, day) }.timeInMillis

    private fun assertMoney(expected: Double, actual: Double) = assertEquals(expected, actual, 0.02)

    @Test
    fun federalTablesByYear() {
        assertMoney(16_914.0, FederalTaxTables.calculateTax(100_000.0, FilingStatus.SINGLE, 2025))
        assertMoney(16_712.0, FederalTaxTables.calculateTax(100_000.0, FilingStatus.SINGLE, 2026))
        assertMoney(
            2_480.0 + 9_120.0 + 0.22 * (200_000.0 - 100_800.0),
            FederalTaxTables.calculateTax(200_000.0, FilingStatus.MARRIED_FILING_JOINTLY, 2026)
        )
        assertMoney(15_750.0, FederalTaxTables.standardDeduction(FilingStatus.SINGLE, 2025))
        assertMoney(16_100.0, FederalTaxTables.standardDeduction(FilingStatus.SINGLE, 2030))
        assertMoney(184_500.0, FederalTaxTables.socialSecurityWageBase(2026))
    }

    @Test
    fun overtimeDeduction() {
        assertMoney(4_000.0, FederalTaxTables.overtimeDeduction(5_000.0, 160_000.0, FilingStatus.SINGLE, 2026))
        assertMoney(12_500.0, FederalTaxTables.overtimeDeduction(20_000.0, 100_000.0, FilingStatus.SINGLE, 2026))
        assertMoney(0.0, FederalTaxTables.overtimeDeduction(5_000.0, 100_000.0, FilingStatus.MARRIED_FILING_SEPARATELY, 2026))
    }

    @Test
    fun classifiesStubTaxLabels() {
        assertEquals(TaxLineKind.FEDERAL, classifyTaxLine("FITW"))
        assertEquals(TaxLineKind.FEDERAL, classifyTaxLine("Federal Income Tax"))
        assertEquals(TaxLineKind.SOCIAL_SECURITY, classifyTaxLine("Fed OASDI/EE"))
        assertEquals(TaxLineKind.MEDICARE, classifyTaxLine("Fed MED/EE"))
        assertEquals(TaxLineKind.STATE, classifyTaxLine("IA SITW"))
        assertEquals(TaxLineKind.LOCAL, classifyTaxLine("School District"))
    }

    private val config = SalaryConfig(
        hourlyRate = 40.0,
        standardHoursPerPeriod = 80.0,
        overtimeMultiplier = 1.5,
        state = "IA",
        preTaxDeductions = listOf(
            Deduction(id = "a", name = "401k", amount = 5.0, isPercentage = true),
            Deduction(id = "b", name = "Medical", amount = 100.0)
        ),
        firstPaydayOfYearMillis = date(2026, Calendar.JANUARY, 9)
    )

    @Test
    fun ficaSkipsSection125ButNot401k() {
        val calc = PaycheckCalculator.calculate(config, date(2026, Calendar.JUNE, 1))
        assertMoney((3200 - 100) * 0.062, calc.socialSecurity)
        assertMoney((3200 - 100) * 0.0145, calc.medicare)
        assertMoney((3200 - 160 - 100) * 0.038, calc.stateTax)

        val zeroFederal = config.copy(taxOverrides = TaxOverrides(customFederalTaxRate = 0.0))
        assertMoney(0.0, PaycheckCalculator.calculate(zeroFederal, date(2026, Calendar.JUNE, 1)).federalTax)
    }

    private fun loggedConfig(gross: Double = 3500.0, ssPerCheck: Double? = null): SalaryConfig {
        val logs = (0 until 11).filter { it != 4 }.map { i ->
            PaycheckLogEntry(
                id = "l$i",
                payDate = date(2026, Calendar.JANUARY, 9 + i * 14),
                grossPay = gross,
                netPay = 2500.0,
                totalTaxes = 700.0,
                totalPreTaxDeductions = 275.0,
                totalPostTaxDeductions = 25.0,
                earnings = listOf(
                    PaycheckLineItem("e1", "Regular Pay", 3200.0, 80.0),
                    PaycheckLineItem("e2", "Overtime 1.5x", 300.0, 5.0)
                ),
                taxes = if (ssPerCheck != null) {
                    listOf(PaycheckLineItem("s", "Social Security", ssPerCheck))
                } else listOf(
                    PaycheckLineItem("t1", "FITW", 350.0),
                    PaycheckLineItem("t2", "OASDI", 200.0),
                    PaycheckLineItem("t3", "Medicare", 50.0),
                    PaycheckLineItem("t4", "IA SITW", 100.0)
                ),
                preTaxDeductions = listOf(
                    PaycheckLineItem("p1", "401k", 175.0),
                    PaycheckLineItem("p2", "Medical", 100.0)
                ),
                postTaxDeductions = listOf(PaycheckLineItem("q1", "Life", 25.0))
            )
        }
        return config.copy(paycheckLog = logs)
    }

    @Test
    fun projectsFromStubsAcrossDaylightSaving() {
        val logged = loggedConfig()
        val asOf = date(2026, Calendar.JUNE, 1)
        val remaining = SalarySummary.remainingPaychecksForYear(logged, 2026, asOf)
        assertEquals(15, remaining.future)
        assertEquals(1, remaining.unlogged)

        val calc = PaycheckCalculator.calculate(logged, asOf)
        val annual = PaycheckCalculator.calculateAnnual(logged, 0.0, 2026)
        val ex = SalarySummary.extrapolateAnnualFromLogs(logged, calc, annual, 2026, asOf = asOf)
        assertMoney(1.5, ex.overtimeMultiplier)
        assertMoney(26 * 350.0, ex.taxes.first { it.label == "FITW" }.amount)
        assertMoney(26 * 2500.0, ex.annualNetPay)

        val federal = SalarySummary.projectFederalTaxReturn(logged, ex, 2026)
        assertMoney(26 * 300.0 / 3, federal.overtimeDeduction)
        assertMoney(26 * 350.0, federal.federalWithheld)
        assertEquals(0, federal.paychecksMissingFederalLine)
    }

    @Test
    fun capsSocialSecurityAtWageBase() {
        val rich = loggedConfig(gross = 20_000.0, ssPerCheck = 600.0).copy(hourlyRate = 250.0)
        val asOf = date(2026, Calendar.JUNE, 1)
        val calc = PaycheckCalculator.calculate(rich, asOf)
        val annual = PaycheckCalculator.calculateAnnual(rich, 0.0, 2026)
        val ex = SalarySummary.extrapolateAnnualFromLogs(rich, calc, annual, 2026, asOf = asOf)
        assertMoney(184_500 * 0.062, ex.taxes.first { it.label == "Social Security" }.amount)
    }
}
