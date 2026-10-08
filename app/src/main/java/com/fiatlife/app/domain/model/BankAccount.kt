package com.fiatlife.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Named checking/savings/retirement/investment account (e.g. "Chase Checking"). No credentials
 * stored. Checking/savings can be a bill's pay-from account; balances may be synced from
 * SimpleFIN by the web app. Mirrors web `lib/bankAccount.ts`.
 */
@Serializable
data class BankAccount(
    val id: String = "",
    val name: String = "",
    /** CHECKING, SAVINGS, RETIREMENT, or INVESTMENT; null on records that predate account types. */
    val type: String? = null,
    val balance: Double? = null,
    val availableBalance: Double? = null,
    /** When the institution reported [balance] (epoch ms). */
    val balanceAsOf: Long? = null,
    /** SimpleFIN `conn_id:account_id`; when set, the web app keeps [balance] in sync. */
    val simplefinAccountKey: String? = null,
    val updatedAt: Long? = null
) {
    val accountType: BankAccountType
        get() = BankAccountType.entries.firstOrNull { it.name == type?.uppercase() }
            ?: BankAccountType.CHECKING

    /** Checking/savings can pay bills; retirement/investment accounts can't. */
    val isCash: Boolean
        get() = accountType == BankAccountType.CHECKING || accountType == BankAccountType.SAVINGS
}

enum class BankAccountType(val displayName: String) {
    CHECKING("Checking"),
    SAVINGS("Savings"),
    RETIREMENT("Retirement"),
    INVESTMENT("Investment")
}
