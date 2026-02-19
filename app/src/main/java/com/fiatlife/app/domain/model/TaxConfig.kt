package com.fiatlife.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TaxOverrides(
    val federalAdditionalWithholding: Double = 0.0,
    val stateAdditionalWithholding: Double = 0.0,
    val isExemptFromFederal: Boolean = false,
    val isExemptFromState: Boolean = false,
    val isExemptFromLocal: Boolean = false,
    val customStateTaxRate: Double? = null,
    val customCountyTaxRate: Double? = null
)

data class FederalTaxBracket(
    val min: Double,
    val max: Double,
    val rate: Double,
    val baseTax: Double
)

object FederalTaxTables {
    // 2025 Federal Tax Brackets (approximate - updated annually)
    val singleBrackets = listOf(
        FederalTaxBracket(0.0, 11_925.0, 0.10, 0.0),
        FederalTaxBracket(11_925.0, 48_475.0, 0.12, 1_192.50),
        FederalTaxBracket(48_475.0, 103_350.0, 0.22, 5_578.50),
        FederalTaxBracket(103_350.0, 197_300.0, 0.24, 17_651.00),
        FederalTaxBracket(197_300.0, 250_525.0, 0.32, 40_199.00),
        FederalTaxBracket(250_525.0, 626_350.0, 0.35, 57_231.00),
        FederalTaxBracket(626_350.0, Double.MAX_VALUE, 0.37, 188_769.75)
    )

    val marriedFilingJointlyBrackets = listOf(
        FederalTaxBracket(0.0, 23_850.0, 0.10, 0.0),
        FederalTaxBracket(23_850.0, 96_950.0, 0.12, 2_385.00),
        FederalTaxBracket(96_950.0, 206_700.0, 0.22, 11_157.00),
        FederalTaxBracket(206_700.0, 394_600.0, 0.24, 35_302.00),
        FederalTaxBracket(394_600.0, 501_050.0, 0.32, 80_398.00),
        FederalTaxBracket(501_050.0, 751_600.0, 0.35, 114_462.00),
        FederalTaxBracket(751_600.0, Double.MAX_VALUE, 0.37, 202_154.50)
    )

    val marriedFilingSeparatelyBrackets = listOf(
        FederalTaxBracket(0.0, 11_925.0, 0.10, 0.0),
        FederalTaxBracket(11_925.0, 48_475.0, 0.12, 1_192.50),
        FederalTaxBracket(48_475.0, 103_350.0, 0.22, 5_578.50),
        FederalTaxBracket(103_350.0, 197_300.0, 0.24, 17_651.00),
        FederalTaxBracket(197_300.0, 250_525.0, 0.32, 40_199.00),
        FederalTaxBracket(250_525.0, 375_800.0, 0.35, 57_231.00),
        FederalTaxBracket(375_800.0, Double.MAX_VALUE, 0.37, 101_077.25)
    )

    val headOfHouseholdBrackets = listOf(
        FederalTaxBracket(0.0, 17_000.0, 0.10, 0.0),
        FederalTaxBracket(17_000.0, 64_850.0, 0.12, 1_700.00),
        FederalTaxBracket(64_850.0, 103_350.0, 0.22, 7_442.00),
        FederalTaxBracket(103_350.0, 197_300.0, 0.24, 15_912.00),
        FederalTaxBracket(197_300.0, 250_500.0, 0.32, 38_460.00),
        FederalTaxBracket(250_500.0, 626_350.0, 0.35, 55_484.00),
        FederalTaxBracket(626_350.0, Double.MAX_VALUE, 0.37, 187_031.50)
    )

    fun bracketsFor(status: FilingStatus): List<FederalTaxBracket> = when (status) {
        FilingStatus.SINGLE -> singleBrackets
        FilingStatus.MARRIED_FILING_JOINTLY -> marriedFilingJointlyBrackets
        FilingStatus.MARRIED_FILING_SEPARATELY -> marriedFilingSeparatelyBrackets
        FilingStatus.HEAD_OF_HOUSEHOLD -> headOfHouseholdBrackets
    }

    fun standardDeduction(status: FilingStatus): Double = when (status) {
        FilingStatus.SINGLE -> 15_000.0
        FilingStatus.MARRIED_FILING_JOINTLY -> 30_000.0
        FilingStatus.MARRIED_FILING_SEPARATELY -> 15_000.0
        FilingStatus.HEAD_OF_HOUSEHOLD -> 22_500.0
    }
}

object FicaTaxRates {
    const val SOCIAL_SECURITY_RATE = 0.062
    const val SOCIAL_SECURITY_WAGE_BASE = 176_100.0
    const val MEDICARE_RATE = 0.0145
    const val ADDITIONAL_MEDICARE_RATE = 0.009
    const val ADDITIONAL_MEDICARE_THRESHOLD_SINGLE = 200_000.0
    const val ADDITIONAL_MEDICARE_THRESHOLD_JOINT = 250_000.0
}
