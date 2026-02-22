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

/** How the credit card minimum payment is determined. */
@Serializable
enum class CreditCardMinPaymentType {
    /** Fixed dollar amount (e.g. $25). */
    FIXED,
    /** Percentage of current balance (e.g. 2%). */
    PERCENT_OF_BALANCE,
    /** Pay full balance (minimum = balance). */
    FULL_BALANCE
}

@Serializable
data class CreditCardDetails(
    /** Current balance owed (updated when you record a payment or enter a new statement). */
    val currentBalance: Double = 0.0,
    /** Annual percentage rate (e.g. 0.1999 for 19.99% APR). */
    val apr: Double = 0.0,
    val minimumPaymentType: CreditCardMinPaymentType = CreditCardMinPaymentType.PERCENT_OF_BALANCE,
    /** For FIXED: dollar amount. For PERCENT_OF_BALANCE: percentage 0–100. Ignored for FULL_BALANCE. */
    val minimumPaymentValue: Double = 2.0,
    /** Optional: interest charged last period (for tracking). */
    val interestChargedLastPeriod: Double = 0.0
) {
    /** Compute minimum payment due based on current balance. */
    fun minimumDue(balance: Double): Double = when (minimumPaymentType) {
        CreditCardMinPaymentType.FIXED -> minimumPaymentValue.coerceAtLeast(0.0)
        CreditCardMinPaymentType.PERCENT_OF_BALANCE -> (balance * (minimumPaymentValue / 100.0)).coerceAtLeast(0.0)
        CreditCardMinPaymentType.FULL_BALANCE -> balance.coerceAtLeast(0.0)
    }

    /** Estimated interest next month: balance * APR/12. */
    fun estimatedMonthlyInterest(balance: Double): Double =
        if (apr <= 0 || balance <= 0) 0.0 else balance * (apr / 12.0)
}

/** General bill category for high-level grouping (header totals, dashboard). */
@Serializable
enum class BillGeneralCategory {
    AUTO,
    UTILITIES,
    HOME,
    HEALTH,
    CREDIT_LOANS,
    SUBSCRIPTION,
    PERSONAL,
    OTHER;

    val displayName: String
        get() = when (this) {
            AUTO -> "Auto"
            UTILITIES -> "Utilities"
            HOME -> "Home"
            HEALTH -> "Health"
            CREDIT_LOANS -> "Credit/Loans"
            SUBSCRIPTION -> "Subscription"
            PERSONAL -> "Personal"
            OTHER -> "Other"
        }
}

/** Specific subcategory under a general category; each has a [generalCategory]. */
@Serializable
enum class BillSubcategory(val generalCategory: BillGeneralCategory) {
    // Home
    MORTGAGE_RENT(BillGeneralCategory.HOME),
    HOME_INSURANCE(BillGeneralCategory.HOME),
    INTERNET(BillGeneralCategory.HOME),
    HOA(BillGeneralCategory.HOME),
    PROPERTY_TAX(BillGeneralCategory.HOME),
    // Utilities
    ELECTRIC(BillGeneralCategory.UTILITIES),
    GAS_HEATING(BillGeneralCategory.UTILITIES),
    WATER_SEWER(BillGeneralCategory.UTILITIES),
    TRASH(BillGeneralCategory.UTILITIES),
    // Auto
    CAR_PAYMENT(BillGeneralCategory.AUTO),
    CAR_INSURANCE(BillGeneralCategory.AUTO),
    GAS_FUEL(BillGeneralCategory.AUTO),
    // Credit/Loans
    CREDIT_CARD(BillGeneralCategory.CREDIT_LOANS),
    STUDENT_LOAN(BillGeneralCategory.CREDIT_LOANS),
    OTHER_LOAN(BillGeneralCategory.CREDIT_LOANS),
    // Subscription
    EDUCATION(BillGeneralCategory.SUBSCRIPTION),
    FINANCE(BillGeneralCategory.SUBSCRIPTION),
    FOOD(BillGeneralCategory.SUBSCRIPTION),
    GAMING(BillGeneralCategory.SUBSCRIPTION),
    HEALTH_WELLNESS(BillGeneralCategory.SUBSCRIPTION),
    SUB_HOME(BillGeneralCategory.SUBSCRIPTION),
    MUSIC(BillGeneralCategory.SUBSCRIPTION),
    NEWS_MEDIA(BillGeneralCategory.SUBSCRIPTION),
    PET_CARE(BillGeneralCategory.SUBSCRIPTION),
    TRAVEL(BillGeneralCategory.SUBSCRIPTION),
    SHOPPING(BillGeneralCategory.SUBSCRIPTION),
    SOFTWARE(BillGeneralCategory.SUBSCRIPTION),
    STREAMING(BillGeneralCategory.SUBSCRIPTION),
    VEHICLE(BillGeneralCategory.SUBSCRIPTION),
    OTHER_SUBSCRIPTION(BillGeneralCategory.SUBSCRIPTION),
    // Health
    GYM_FITNESS(BillGeneralCategory.HEALTH),
    MEDICAL(BillGeneralCategory.HEALTH),
    // Personal
    GROCERIES(BillGeneralCategory.PERSONAL),
    CHILDCARE(BillGeneralCategory.PERSONAL),
    PET(BillGeneralCategory.PERSONAL),
    // Other
    OTHER(BillGeneralCategory.OTHER);

    val displayName: String
        get() = when (this) {
            MORTGAGE_RENT -> "Mortgage/Rent"
            HOME_INSURANCE -> "Home Insurance"
            INTERNET -> "Internet"
            HOA -> "HOA"
            PROPERTY_TAX -> "Property Tax"
            ELECTRIC -> "Electric"
            GAS_HEATING -> "Gas/Heating"
            WATER_SEWER -> "Water/Sewer"
            TRASH -> "Trash"
            CAR_PAYMENT -> "Car Payment"
            CAR_INSURANCE -> "Car Insurance"
            GAS_FUEL -> "Gas/Fuel"
            CREDIT_CARD -> "Credit Card"
            STUDENT_LOAN -> "Student Loan"
            OTHER_LOAN -> "Other Loan"
            EDUCATION -> "Education"
            FINANCE -> "Finance"
            FOOD -> "Food"
            GAMING -> "Gaming"
            HEALTH_WELLNESS -> "Health/Wellness"
            SUB_HOME -> "Home"
            MUSIC -> "Music"
            NEWS_MEDIA -> "News/Media"
            PET_CARE -> "Pet Care"
            TRAVEL -> "Travel"
            SHOPPING -> "Shopping"
            SOFTWARE -> "Software"
            STREAMING -> "Streaming"
            VEHICLE -> "Vehicle"
            OTHER_SUBSCRIPTION -> "Other Subscription"
            GYM_FITNESS -> "Gym/Fitness"
            MEDICAL -> "Medical"
            GROCERIES -> "Groceries"
            CHILDCARE -> "Childcare"
            PET -> "Pet"
            OTHER -> "Other"
        }

    val icon: String
        get() = when (this) {
            MORTGAGE_RENT -> "home"
            HOME_INSURANCE -> "shield"
            INTERNET -> "wifi"
            HOA -> "apartment"
            PROPERTY_TAX -> "account_balance"
            ELECTRIC -> "bolt"
            GAS_HEATING -> "local_fire_department"
            WATER_SEWER -> "water_drop"
            TRASH -> "delete"
            CAR_PAYMENT -> "directions_car"
            CAR_INSURANCE -> "shield"
            GAS_FUEL -> "local_gas_station"
            CREDIT_CARD -> "credit_card"
            STUDENT_LOAN -> "school"
            OTHER_LOAN -> "account_balance"
            EDUCATION -> "school"
            FINANCE -> "account_balance"
            FOOD -> "restaurant"
            GAMING -> "sports_esports"
            HEALTH_WELLNESS -> "fitness_center"
            SUB_HOME -> "home"
            MUSIC -> "music_note"
            NEWS_MEDIA -> "newspaper"
            PET_CARE -> "pets"
            TRAVEL -> "flight"
            SHOPPING -> "shopping_cart"
            SOFTWARE -> "computer"
            STREAMING -> "tv"
            VEHICLE -> "directions_car"
            OTHER_SUBSCRIPTION -> "subscriptions"
            GYM_FITNESS -> "fitness_center"
            MEDICAL -> "medical_services"
            GROCERIES -> "shopping_cart"
            CHILDCARE -> "child_care"
            PET -> "pets"
            OTHER -> "receipt"
        }
}

@Serializable
data class Bill(
    val id: String = "",
    val name: String = "",
    val amount: Double = 0.0,
    @Deprecated("Use subcategory instead") val category: BillCategory = BillCategory.OTHER,
    val subcategory: BillSubcategory? = null,
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
    val updatedAt: Long = 0L,
    val creditCardDetails: CreditCardDetails? = null,
    /** When set, this bill represents the monthly payment for the linked credit/loan account (Debt tab). */
    val linkedCreditAccountId: String? = null
) {
    /** Resolved subcategory: [subcategory] if set, else derived from legacy [category]. */
    val effectiveSubcategory: BillSubcategory
        get() = subcategory ?: fromLegacyCategory(category)

    /** General category for this bill (from [effectiveSubcategory]). */
    val effectiveGeneralCategory: BillGeneralCategory
        get() = effectiveSubcategory.generalCategory

    /** True if this bill is tracked as a credit card (has credit card–specific fields). */
    fun isCreditCard(): Boolean = creditCardDetails != null

    /** True if this bill is a credit card or linked to a credit/loan account (Debt tab). */
    fun isCreditOrLoan(): Boolean = isCreditCard() || linkedCreditAccountId != null

    /** Amount due this period: for credit cards, computed minimum due; otherwise bill.amount. */
    fun effectiveAmountDue(): Double = creditCardDetails?.let { cc ->
        cc.minimumDue(cc.currentBalance)
    } ?: amount

    /** Estimated interest next month (credit cards only). */
    fun estimatedMonthlyInterest(): Double = creditCardDetails?.estimatedMonthlyInterest(creditCardDetails.currentBalance) ?: 0.0

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

    /** Most recent due date (start of day) that is <= now; null if none. Used to detect past due. */
    fun lastDueDateMillis(): Long? {
        val next = nextDueDateMillis() ?: return null
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = next
        when (frequency) {
            BillFrequency.MONTHLY -> cal.add(java.util.Calendar.MONTH, -1)
            BillFrequency.WEEKLY -> cal.add(java.util.Calendar.WEEK_OF_YEAR, -1)
            BillFrequency.BIWEEKLY -> cal.add(java.util.Calendar.WEEK_OF_YEAR, -2)
            BillFrequency.BIMONTHLY -> cal.add(java.util.Calendar.MONTH, -2)
            BillFrequency.QUARTERLY -> cal.add(java.util.Calendar.MONTH, -3)
            BillFrequency.SEMIANNUALLY -> cal.add(java.util.Calendar.MONTH, -6)
            BillFrequency.ANNUALLY -> cal.add(java.util.Calendar.YEAR, -1)
        }
        val lastDue = cal.timeInMillis
        return if (lastDue <= System.currentTimeMillis()) lastDue else null
    }

    /** True if this bill is not paid and the due date (end of due day) has passed. */
    fun isPastDue(): Boolean {
        if (isPaid) return false
        val lastDue = lastDueDateMillis() ?: return false
        val endOfDueDay = lastDue + 86400_000L - 1
        return System.currentTimeMillis() > endOfDueDay
    }
}

private fun fromLegacyCategory(category: BillCategory): BillSubcategory = when (category) {
    BillCategory.MORTGAGE_RENT -> BillSubcategory.MORTGAGE_RENT
    BillCategory.ELECTRIC -> BillSubcategory.ELECTRIC
    BillCategory.GAS_HEATING -> BillSubcategory.GAS_HEATING
    BillCategory.WATER_SEWER -> BillSubcategory.WATER_SEWER
    BillCategory.TRASH -> BillSubcategory.TRASH
    BillCategory.INTERNET -> BillSubcategory.INTERNET
    BillCategory.PHONE -> BillSubcategory.OTHER_SUBSCRIPTION
    BillCategory.CABLE_STREAMING -> BillSubcategory.STREAMING
    BillCategory.CAR_PAYMENT -> BillSubcategory.CAR_PAYMENT
    BillCategory.CAR_INSURANCE -> BillSubcategory.CAR_INSURANCE
    BillCategory.HOME_INSURANCE -> BillSubcategory.HOME_INSURANCE
    BillCategory.PROPERTY_TAX -> BillSubcategory.PROPERTY_TAX
    BillCategory.HOA -> BillSubcategory.HOA
    BillCategory.GROCERIES -> BillSubcategory.GROCERIES
    BillCategory.GAS_FUEL -> BillSubcategory.GAS_FUEL
    BillCategory.CHILDCARE -> BillSubcategory.CHILDCARE
    BillCategory.STUDENT_LOAN -> BillSubcategory.STUDENT_LOAN
    BillCategory.CREDIT_CARD -> BillSubcategory.CREDIT_CARD
    BillCategory.SUBSCRIPTION -> BillSubcategory.OTHER_SUBSCRIPTION
    BillCategory.GYM_FITNESS -> BillSubcategory.GYM_FITNESS
    BillCategory.PET -> BillSubcategory.PET
    BillCategory.OTHER -> BillSubcategory.OTHER
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
