package com.fiatlife.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class BillPayment(
    val date: Long = 0L,
    val amount: Double = 0.0
)

@Serializable
data class StatementEntry(
    val hash: String = "",
    val addedAt: Long = 0L,
    val label: String = ""
)

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
    val statementEntries: List<StatementEntry> = emptyList(),
    val paymentHistory: List<BillPayment> = emptyList(),
    val isPaid: Boolean = false,
    val lastPaidDate: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    /** Statements to show: statementEntries if present, else legacy attachmentHashes with placeholder dates. */
    fun statementsOrderedByDate(): List<StatementEntry> {
        if (statementEntries.isNotEmpty()) return statementEntries.sortedByDescending { it.addedAt }
        return attachmentHashes.map { StatementEntry(hash = it, addedAt = updatedAt, label = "Statement") }
            .sortedByDescending { it.addedAt }
    }

    /** Total amount paid in the current calendar year. */
    fun annualTotalPaidSoFar(): Double {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.MONTH, java.util.Calendar.JANUARY)
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val yearStart = cal.timeInMillis
        return paymentHistory.filter { it.date >= yearStart }.sumOf { it.amount }
    }

    /** Next due date (start of day) in ms. Handles MONTHLY; other frequencies use similar logic. */
    fun nextDueDateMillis(): Long? {
        val cal = java.util.Calendar.getInstance()
        val day = dueDay.coerceIn(1, 31)
        when (frequency) {
            BillFrequency.MONTHLY -> {
                cal.set(java.util.Calendar.DAY_OF_MONTH, day.coerceIn(1, cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)))
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                if (cal.timeInMillis <= System.currentTimeMillis())
                    cal.add(java.util.Calendar.MONTH, 1)
                cal.set(java.util.Calendar.DAY_OF_MONTH, day.coerceIn(1, cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)))
                return cal.timeInMillis
            }
            BillFrequency.ANNUALLY -> {
                cal.set(java.util.Calendar.MONTH, java.util.Calendar.JANUARY)
                cal.set(java.util.Calendar.DAY_OF_MONTH, day.coerceIn(1, 31))
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                if (cal.timeInMillis <= System.currentTimeMillis())
                    cal.add(java.util.Calendar.YEAR, 1)
                return cal.timeInMillis
            }
            else -> {
                cal.set(java.util.Calendar.DAY_OF_MONTH, day.coerceIn(1, cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)))
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                while (cal.timeInMillis <= System.currentTimeMillis()) {
                    when (frequency) {
                        BillFrequency.WEEKLY -> cal.add(java.util.Calendar.WEEK_OF_YEAR, 1)
                        BillFrequency.BIWEEKLY -> cal.add(java.util.Calendar.WEEK_OF_YEAR, 2)
                        BillFrequency.BIMONTHLY -> cal.add(java.util.Calendar.MONTH, 2)
                        BillFrequency.QUARTERLY -> cal.add(java.util.Calendar.MONTH, 3)
                        BillFrequency.SEMIANNUALLY -> cal.add(java.util.Calendar.MONTH, 6)
                        else -> cal.add(java.util.Calendar.MONTH, 1)
                    }
                    cal.set(java.util.Calendar.DAY_OF_MONTH, day.coerceIn(1, cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)))
                }
                return cal.timeInMillis
            }
        }
    }
}

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
