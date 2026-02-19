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
)

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
