package com.fiatlife.app.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.ceil

@Serializable
data class TaxOverrides(
    val federalAdditionalWithholding: Double = 0.0,
    val stateAdditionalWithholding: Double = 0.0,
    val isExemptFromFederal: Boolean = false,
    val isExemptFromState: Boolean = false,
    val isExemptFromLocal: Boolean = false,
    val customFederalTaxRate: Double? = null,
    val customStateTaxRate: Double? = null,
    val customCountyTaxRate: Double? = null,
    val customSocialSecurityRate: Double? = null,
    val customMedicareRate: Double? = null
)

data class FederalTaxBracket(
    val min: Double,
    val max: Double,
    val rate: Double,
    val baseTax: Double
)

/** Per-year federal tables. Mirrors web `lib/tax.ts`. */
object FederalTaxTables {
    private val RATES = listOf(0.10, 0.12, 0.22, 0.24, 0.32, 0.35, 0.37)

    private class TaxYear(
        /** Upper bound of each bracket except the last (37%). */
        val bracketTops: Map<FilingStatus, List<Double>>,
        val standardDeduction: Map<FilingStatus, Double>,
        val socialSecurityWageBase: Double
    )

    /**
     * 2025: IRS Rev. Proc. 2024-40 brackets; standard deduction as raised by the
     * One Big Beautiful Bill Act (P.L. 119-21). 2026: IRS Rev. Proc. 2025-32.
     * Social Security wage bases from SSA.
     */
    private val YEARS: Map<Int, TaxYear> = mapOf(
        2025 to TaxYear(
            bracketTops = mapOf(
                FilingStatus.SINGLE to listOf(11_925.0, 48_475.0, 103_350.0, 197_300.0, 250_525.0, 626_350.0),
                FilingStatus.MARRIED_FILING_JOINTLY to listOf(23_850.0, 96_950.0, 206_700.0, 394_600.0, 501_050.0, 751_600.0),
                FilingStatus.MARRIED_FILING_SEPARATELY to listOf(11_925.0, 48_475.0, 103_350.0, 197_300.0, 250_525.0, 375_800.0),
                FilingStatus.HEAD_OF_HOUSEHOLD to listOf(17_000.0, 64_850.0, 103_350.0, 197_300.0, 250_500.0, 626_350.0)
            ),
            standardDeduction = mapOf(
                FilingStatus.SINGLE to 15_750.0,
                FilingStatus.MARRIED_FILING_JOINTLY to 31_500.0,
                FilingStatus.MARRIED_FILING_SEPARATELY to 15_750.0,
                FilingStatus.HEAD_OF_HOUSEHOLD to 23_625.0
            ),
            socialSecurityWageBase = 176_100.0
        ),
        2026 to TaxYear(
            bracketTops = mapOf(
                FilingStatus.SINGLE to listOf(12_400.0, 50_400.0, 105_700.0, 201_775.0, 256_225.0, 640_600.0),
                FilingStatus.MARRIED_FILING_JOINTLY to listOf(24_800.0, 100_800.0, 211_400.0, 403_550.0, 512_450.0, 768_700.0),
                FilingStatus.MARRIED_FILING_SEPARATELY to listOf(12_400.0, 50_400.0, 105_700.0, 201_775.0, 256_225.0, 384_350.0),
                FilingStatus.HEAD_OF_HOUSEHOLD to listOf(17_700.0, 67_450.0, 105_700.0, 201_750.0, 256_200.0, 640_600.0)
            ),
            standardDeduction = mapOf(
                FilingStatus.SINGLE to 16_100.0,
                FilingStatus.MARRIED_FILING_JOINTLY to 32_200.0,
                FilingStatus.MARRIED_FILING_SEPARATELY to 16_100.0,
                FilingStatus.HEAD_OF_HOUSEHOLD to 24_150.0
            ),
            socialSecurityWageBase = 184_500.0
        )
    )

    private fun currentYear(): Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

    /** Tax year whose tables are used for [year] (clamped to the years on file). */
    fun tableYear(year: Int): Int = year.coerceIn(YEARS.keys.min(), YEARS.keys.max())

    private fun tableFor(year: Int): TaxYear = YEARS.getValue(tableYear(year))

    fun bracketsFor(status: FilingStatus, year: Int = currentYear()): List<FederalTaxBracket> {
        val tops = tableFor(year).bracketTops.getValue(status)
        var min = 0.0
        var baseTax = 0.0
        return RATES.mapIndexed { i, rate ->
            val max = tops.getOrElse(i) { Double.MAX_VALUE }
            val bracket = FederalTaxBracket(min, max, rate, baseTax)
            if (max != Double.MAX_VALUE) baseTax += (max - min) * rate
            min = max
            bracket
        }
    }

    fun standardDeduction(status: FilingStatus, year: Int = currentYear()): Double =
        tableFor(year).standardDeduction.getValue(status)

    fun socialSecurityWageBase(year: Int = currentYear()): Double =
        tableFor(year).socialSecurityWageBase

    fun calculateTax(taxableIncome: Double, status: FilingStatus, year: Int = currentYear()): Double {
        for (bracket in bracketsFor(status, year).reversed()) {
            if (taxableIncome > bracket.min) {
                return bracket.baseTax + (taxableIncome - bracket.min) * bracket.rate
            }
        }
        return 0.0
    }

    fun marginalRate(taxableIncome: Double, status: FilingStatus, year: Int = currentYear()): Double {
        val brackets = bracketsFor(status, year)
        for (bracket in brackets.reversed()) {
            if (taxableIncome > bracket.min) return bracket.rate
        }
        return brackets.first().rate
    }

    /**
     * "No tax on overtime" deduction (One Big Beautiful Bill Act, tax years
     * 2025–2028): the premium part of FLSA overtime, capped and phased out by MAGI.
     * Not available to married-filing-separately filers.
     */
    fun overtimeDeduction(
        overtimePremium: Double,
        modifiedAgi: Double,
        status: FilingStatus,
        year: Int
    ): Double {
        if (year !in 2025..2028) return 0.0
        if (status == FilingStatus.MARRIED_FILING_SEPARATELY) return 0.0
        val joint = status == FilingStatus.MARRIED_FILING_JOINTLY
        val cap = if (joint) 25_000.0 else 12_500.0
        val phaseoutStart = if (joint) 300_000.0 else 150_000.0
        val reduction = ceil((modifiedAgi - phaseoutStart).coerceAtLeast(0.0) / 1_000.0) * 100.0
        return (overtimePremium.coerceAtLeast(0.0).coerceAtMost(cap) - reduction).coerceAtLeast(0.0)
    }

    /**
     * Rough per-state rate applied to taxable wages. Flat-tax states use their
     * actual 2026 rate; graduated states use a typical middle-income bracket, so
     * set a custom rate from a paystub for accuracy.
     */
    fun estimateStateTaxRate(state: String): Double = when (state.uppercase()) {
        "AL" -> 0.05; "AK" -> 0.0; "AZ" -> 0.025; "AR" -> 0.039
        "CA" -> 0.093; "CO" -> 0.044; "CT" -> 0.055; "DE" -> 0.066
        "FL" -> 0.0; "GA" -> 0.0519; "HI" -> 0.0825; "ID" -> 0.053
        "IL" -> 0.0495; "IN" -> 0.0295; "IA" -> 0.038; "KS" -> 0.0558
        "KY" -> 0.035; "LA" -> 0.03; "ME" -> 0.0675; "MD" -> 0.0475
        "MA" -> 0.05; "MI" -> 0.0425; "MN" -> 0.068; "MS" -> 0.04
        "MO" -> 0.047; "MT" -> 0.059; "NE" -> 0.0455; "NV" -> 0.0
        "NH" -> 0.0; "NJ" -> 0.05525; "NM" -> 0.049; "NY" -> 0.055
        "NC" -> 0.0399; "ND" -> 0.0195; "OH" -> 0.0275; "OK" -> 0.0475
        "OR" -> 0.0875; "PA" -> 0.0307; "RI" -> 0.0475; "SC" -> 0.062
        "SD" -> 0.0; "TN" -> 0.0; "TX" -> 0.0; "UT" -> 0.045
        "VT" -> 0.066; "VA" -> 0.0575; "WA" -> 0.0; "WV" -> 0.0482
        "WI" -> 0.053; "WY" -> 0.0; "DC" -> 0.085
        else -> 0.05
    }
}

object FicaTaxRates {
    const val SOCIAL_SECURITY_RATE = 0.062
    const val MEDICARE_RATE = 0.0145
    const val ADDITIONAL_MEDICARE_RATE = 0.009
    /** Employers withhold Additional Medicare above $200k regardless of filing status. */
    const val ADDITIONAL_MEDICARE_WITHHOLDING_THRESHOLD = 200_000.0
}
