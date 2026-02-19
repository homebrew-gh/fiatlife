package com.fiatlife.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FinancialGoal(
    val id: String = "",
    val name: String = "",
    val category: GoalCategory = GoalCategory.GENERAL_SAVINGS,
    val targetAmount: Double = 0.0,
    val currentAmount: Double = 0.0,
    val monthlyContribution: Double = 0.0,
    val targetDate: Long? = null,
    val notes: String = "",
    val color: String = "#4CAF50",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val progressPercent: Double
        get() = if (targetAmount > 0) (currentAmount / targetAmount * 100).coerceIn(0.0, 100.0) else 0.0

    val remainingAmount: Double
        get() = (targetAmount - currentAmount).coerceAtLeast(0.0)

    val isComplete: Boolean
        get() = currentAmount >= targetAmount

    val monthsRemaining: Int?
        get() = if (monthlyContribution > 0 && remainingAmount > 0) {
            kotlin.math.ceil(remainingAmount / monthlyContribution).toInt()
        } else null
}

@Serializable
enum class GoalCategory {
    EMERGENCY_FUND,
    RETIREMENT,
    HOUSE_DOWN_PAYMENT,
    CAR_PURCHASE,
    VACATION,
    WEDDING,
    EDUCATION,
    DEBT_PAYOFF,
    GENERAL_SAVINGS,
    INVESTMENT,
    HOME_IMPROVEMENT,
    MEDICAL,
    OTHER;

    val displayName: String
        get() = when (this) {
            EMERGENCY_FUND -> "Emergency Fund"
            RETIREMENT -> "Retirement"
            HOUSE_DOWN_PAYMENT -> "House Down Payment"
            CAR_PURCHASE -> "New Car"
            VACATION -> "Trip/Vacation"
            WEDDING -> "Wedding"
            EDUCATION -> "Education"
            DEBT_PAYOFF -> "Debt Payoff"
            GENERAL_SAVINGS -> "Cash Savings"
            INVESTMENT -> "Investment"
            HOME_IMPROVEMENT -> "Home Improvement"
            MEDICAL -> "Medical"
            OTHER -> "Other"
        }

    val defaultIcon: String
        get() = when (this) {
            EMERGENCY_FUND -> "savings"
            RETIREMENT -> "elderly"
            HOUSE_DOWN_PAYMENT -> "house"
            CAR_PURCHASE -> "directions_car"
            VACATION -> "flight"
            WEDDING -> "favorite"
            EDUCATION -> "school"
            DEBT_PAYOFF -> "money_off"
            GENERAL_SAVINGS -> "account_balance_wallet"
            INVESTMENT -> "trending_up"
            HOME_IMPROVEMENT -> "home_repair_service"
            MEDICAL -> "local_hospital"
            OTHER -> "flag"
        }

    val suggestedColor: String
        get() = when (this) {
            EMERGENCY_FUND -> "#F44336"
            RETIREMENT -> "#9C27B0"
            HOUSE_DOWN_PAYMENT -> "#2196F3"
            CAR_PURCHASE -> "#FF9800"
            VACATION -> "#00BCD4"
            WEDDING -> "#E91E63"
            EDUCATION -> "#3F51B5"
            DEBT_PAYOFF -> "#795548"
            GENERAL_SAVINGS -> "#4CAF50"
            INVESTMENT -> "#009688"
            HOME_IMPROVEMENT -> "#607D8B"
            MEDICAL -> "#F44336"
            OTHER -> "#9E9E9E"
        }
}
