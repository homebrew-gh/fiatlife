package com.fiatlife.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DirectDeposit(
    val id: String = "",
    val accountName: String = "",
    val bankName: String = "",
    val accountType: AccountType = AccountType.CHECKING,
    val amount: Double = 0.0,
    val isPercentage: Boolean = false,
    val isRemainder: Boolean = false,
    val sortOrder: Int = 0
)

@Serializable
enum class AccountType {
    CHECKING,
    SAVINGS,
    MONEY_MARKET,
    INVESTMENT,
    OTHER;

    val displayName: String
        get() = name.replace("_", " ").lowercase()
            .replaceFirstChar { it.uppercase() }
}
