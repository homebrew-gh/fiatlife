package com.fiatlife.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Deduction(
    val id: String = "",
    val name: String = "",
    val amount: Double = 0.0,
    val type: DeductionType = DeductionType.PRE_TAX,
    val category: DeductionCategory = DeductionCategory.OTHER,
    val isPercentage: Boolean = false,
    val isEnabled: Boolean = true
) {
    /** Stored category, or a guess from the name when it was left as Other. Mirrors web `deductionCategory`. */
    val effectiveCategory: DeductionCategory
        get() {
            if (category != DeductionCategory.OTHER) return category
            val n = name.lowercase()
            fun has(pattern: String) = Regex(pattern).containsMatchIn(n)
            return when {
                has("roth") -> DeductionCategory.ROTH_401K
                has("401|403|457|\\btsp\\b|retire|pension") -> DeductionCategory.TRADITIONAL_401K
                has("\\bhsa\\b|health\\s*sav") -> DeductionCategory.HSA
                has("\\bfsa\\b|flex") -> DeductionCategory.FSA
                has("dental") -> DeductionCategory.DENTAL_INSURANCE
                has("vision") -> DeductionCategory.VISION_INSURANCE
                has("medical|health|\\bmed\\b") -> DeductionCategory.MEDICAL_INSURANCE
                has("parking|transit|commut") -> DeductionCategory.PARKING_TRANSIT
                else -> DeductionCategory.OTHER
            }
        }

    /** Section 125 / HSA / transit deductions also skip Social Security and Medicare. */
    val isFicaExempt: Boolean
        get() = effectiveCategory in FICA_EXEMPT_CATEGORIES
}

private val FICA_EXEMPT_CATEGORIES = setOf(
    DeductionCategory.MEDICAL_INSURANCE,
    DeductionCategory.DENTAL_INSURANCE,
    DeductionCategory.VISION_INSURANCE,
    DeductionCategory.HSA,
    DeductionCategory.FSA,
    DeductionCategory.PARKING_TRANSIT
)

enum class TaxLineKind { FEDERAL, STATE, LOCAL, SOCIAL_SECURITY, MEDICARE, OTHER }

/** Classify a paystub tax line by its label ("FITW", "Fed OASDI/EE", "Medicare"…). Mirrors web `classifyTaxLine`. */
fun classifyTaxLine(label: String): TaxLineKind {
    val l = label.lowercase()
    fun has(pattern: String) = Regex(pattern).containsMatchIn(l)
    return when {
        has("medicare|\\bmed\\b|med/ee|\\bmedi\\b|\\bmwt\\b") -> TaxLineKind.MEDICARE
        has("social\\s*sec|oasdi|\\bss\\b|\\bsoc\\s*sec") -> TaxLineKind.SOCIAL_SECURITY
        has("local|city|county|school|\\beit\\b|\\blst\\b|municipal|borough|township") -> TaxLineKind.LOCAL
        has("fed|\\bfitw?\\b|\\bfwt\\b") -> TaxLineKind.FEDERAL
        has("state|\\bsitw?\\b|\\bswt\\b") -> TaxLineKind.STATE
        else -> TaxLineKind.OTHER
    }
}

@Serializable
enum class DeductionType {
    PRE_TAX,
    POST_TAX;

    val displayName: String
        get() = name.replace("_", " ").lowercase()
            .replaceFirstChar { it.uppercase() }
}

@Serializable
enum class DeductionCategory {
    MEDICAL_INSURANCE,
    DENTAL_INSURANCE,
    VISION_INSURANCE,
    HSA,
    FSA,
    TRADITIONAL_401K,
    ROTH_401K,
    LIFE_INSURANCE,
    AD_AND_D,
    CRITICAL_ILLNESS,
    DISABILITY_INSURANCE,
    LEGAL_PLAN,
    UNION_DUES,
    PARKING_TRANSIT,
    OTHER;

    val displayName: String
        get() = when (this) {
            MEDICAL_INSURANCE -> "Medical Insurance"
            DENTAL_INSURANCE -> "Dental Insurance"
            VISION_INSURANCE -> "Vision Insurance"
            HSA -> "HSA"
            FSA -> "FSA"
            TRADITIONAL_401K -> "Traditional 401(k)"
            ROTH_401K -> "Roth 401(k)"
            LIFE_INSURANCE -> "Life Insurance"
            AD_AND_D -> "AD&D"
            CRITICAL_ILLNESS -> "Critical Illness"
            DISABILITY_INSURANCE -> "Disability Insurance"
            LEGAL_PLAN -> "Legal Plan"
            UNION_DUES -> "Union Dues"
            PARKING_TRANSIT -> "Parking/Transit"
            OTHER -> "Other"
        }

    val defaultType: DeductionType
        get() = when (this) {
            MEDICAL_INSURANCE, DENTAL_INSURANCE, VISION_INSURANCE,
            HSA, FSA, TRADITIONAL_401K, PARKING_TRANSIT -> DeductionType.PRE_TAX
            else -> DeductionType.POST_TAX
        }
}
