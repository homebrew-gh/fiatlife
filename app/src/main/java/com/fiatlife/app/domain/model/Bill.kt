package com.fiatlife.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Bill(
    val id: String = "",
    val name: String = "",
    val amount: Double = 0.0,
    val category: BillCategory = BillCategory.OTHER,
    val frequency: BillFrequency = BillFrequency.MONTHLY,
    val dueDay: Int = 1,
    val autoPay: Boolean = false,
    val accountName: String = "",
    val notes: String = "",
    val attachmentHashes: List<String> = emptyList(),
    val isPaid: Boolean = false,
    val lastPaidDate: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
enum class BillCategory {
    MORTGAGE_RENT,
    ELECTRIC,
    GAS_HEATING,
    WATER_SEWER,
    TRASH,
    INTERNET,
    PHONE,
    CABLE_STREAMING,
    CAR_PAYMENT,
    CAR_INSURANCE,
    HOME_INSURANCE,
    PROPERTY_TAX,
    HOA,
    GROCERIES,
    GAS_FUEL,
    CHILDCARE,
    STUDENT_LOAN,
    CREDIT_CARD,
    SUBSCRIPTION,
    GYM_FITNESS,
    PET,
    OTHER;

    val displayName: String
        get() = when (this) {
            MORTGAGE_RENT -> "Mortgage/Rent"
            ELECTRIC -> "Electric"
            GAS_HEATING -> "Gas/Heating"
            WATER_SEWER -> "Water/Sewer"
            TRASH -> "Trash"
            INTERNET -> "Internet"
            PHONE -> "Phone"
            CABLE_STREAMING -> "Cable/Streaming"
            CAR_PAYMENT -> "Car Payment"
            CAR_INSURANCE -> "Car Insurance"
            HOME_INSURANCE -> "Home Insurance"
            PROPERTY_TAX -> "Property Tax"
            HOA -> "HOA"
            GROCERIES -> "Groceries"
            GAS_FUEL -> "Gas/Fuel"
            CHILDCARE -> "Childcare"
            STUDENT_LOAN -> "Student Loan"
            CREDIT_CARD -> "Credit Card"
            SUBSCRIPTION -> "Subscription"
            GYM_FITNESS -> "Gym/Fitness"
            PET -> "Pet"
            OTHER -> "Other"
        }

    val icon: String
        get() = when (this) {
            MORTGAGE_RENT -> "home"
            ELECTRIC -> "bolt"
            GAS_HEATING -> "local_fire_department"
            WATER_SEWER -> "water_drop"
            TRASH -> "delete"
            INTERNET -> "wifi"
            PHONE -> "phone_android"
            CABLE_STREAMING -> "tv"
            CAR_PAYMENT -> "directions_car"
            CAR_INSURANCE -> "shield"
            HOME_INSURANCE -> "security"
            PROPERTY_TAX -> "account_balance"
            HOA -> "apartment"
            GROCERIES -> "shopping_cart"
            GAS_FUEL -> "local_gas_station"
            CHILDCARE -> "child_care"
            STUDENT_LOAN -> "school"
            CREDIT_CARD -> "credit_card"
            SUBSCRIPTION -> "subscriptions"
            GYM_FITNESS -> "fitness_center"
            PET -> "pets"
            OTHER -> "receipt"
        }
}

@Serializable
enum class BillFrequency(val timesPerYear: Int) {
    WEEKLY(52),
    BIWEEKLY(26),
    MONTHLY(12),
    BIMONTHLY(6),
    QUARTERLY(4),
    SEMIANNUALLY(2),
    ANNUALLY(1);

    val displayName: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }
}
