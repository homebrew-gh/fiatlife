package com.fiatlife.app.domain.model

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * Manually-tracked bitcoin wallet (e.g. "Cold storage"). Amount only — no keys or xpubs.
 * Mirrors web `lib/bitcoin.ts`; stored under the `fiatlife/btc/<id>` d-tag.
 */
@Serializable
data class BitcoinWallet(
    val id: String = "",
    val name: String = "",
    /** Whole satoshis, so amounts never pick up float rounding. */
    val sats: Long = 0L,
    val notes: String = "",
    /** Held in a retirement account (e.g. a bitcoin IRA). Still counted once, under Bitcoin. */
    val retirement: Boolean = false,
    val createdAt: Long = 0L,
    /** When the amount was last entered. */
    val updatedAt: Long = 0L
)

data class BtcPrice(
    val usd: Double,
    val fetchedAtMs: Long,
    val source: String
)

object Bitcoin {
    const val SATS_PER_BTC = 100_000_000L

    /**
     * Parse a user-entered BTC amount ("0.5", "1,000.12345678") into sats.
     * Returns null for anything that isn't a non-negative amount with ≤ 8 decimals.
     */
    fun parseBtcToSats(input: String): Long? {
        val text = input.trim().replace(",", "").replace(Regex("\\s*btc$", RegexOption.IGNORE_CASE), "")
        val match = Regex("^(\\d*)(?:\\.(\\d{0,8}))?$").matchEntire(text) ?: return null
        val whole = match.groupValues[1]
        val fraction = match.groupValues[2]
        if (whole.isEmpty() && fraction.isEmpty()) return null
        return runCatching {
            BigDecimal(whole.ifEmpty { "0" }).multiply(BigDecimal(SATS_PER_BTC))
                .add(BigDecimal(fraction.padEnd(8, '0')))
                .longValueExact()
        }.getOrNull()
    }

    /** "0.5", "1.23456789" — trailing zeros trimmed. */
    fun formatBtc(sats: Long): String {
        val abs = kotlin.math.abs(sats)
        val whole = NumberFormat.getIntegerInstance(Locale.US).format(abs / SATS_PER_BTC)
        val fraction = (abs % SATS_PER_BTC).toString().padStart(8, '0').trimEnd('0')
        val body = if (fraction.isEmpty()) whole else "$whole.$fraction"
        return if (sats < 0) "-$body" else body
    }

    fun satsToUsd(sats: Long, usdPerBtc: Double): Double = sats.toDouble() / SATS_PER_BTC * usdPerBtc

    fun walletDetail(wallet: BitcoinWallet): String =
        "${formatBtc(wallet.sats)} BTC" + if (wallet.retirement) " · Retirement" else ""
}

data class NetWorthSummary(
    val cash: Double,
    val invested: Double,
    val bitcoinSats: Long,
    /** Null when there are wallets but no BTC price yet. */
    val bitcoinUsd: Double?,
    /** Purchase price of homes with a mortgage on file (offsets the mortgage debt). */
    val homeValue: Double,
    /** Balance owed on mortgages that have a home price. */
    val mortgageDebt: Double,
    val homeEquity: Double,
    /** All debt except mortgages counted in [homeEquity]. */
    val otherDebt: Double,
    /** A mortgage with a balance but no home price, so it counts only as debt. */
    val missingHomePrice: Boolean,
    val debt: Double,
    val assets: Double,
    val total: Double,
    val hasData: Boolean
)

/** Mirrors web `computeNetWorth` in `lib/netWorth.ts`. */
fun computeNetWorth(
    bankAccounts: List<BankAccount>,
    wallets: List<BitcoinWallet>,
    usdPerBtc: Double?,
    creditAccounts: List<CreditAccount>
): NetWorthSummary {
    val withBalance = bankAccounts.filter { it.balance != null }
    val cash = withBalance.filter { it.isCash }.sumOf { it.balance!! }
    val invested = withBalance.filterNot { it.isCash }.sumOf { it.balance!! }
    val bitcoinSats = wallets.sumOf { it.sats }
    val bitcoinUsd = when {
        usdPerBtc != null -> Bitcoin.satsToUsd(bitcoinSats, usdPerBtc)
        bitcoinSats == 0L -> 0.0
        else -> null
    }
    val mortgages = creditAccounts.filter { it.type == CreditAccountType.MORTGAGE }
    val priced = mortgages.filter { it.homePrice > 0.0 }
    val homeValue = priced.sumOf { it.homePrice }
    val mortgageDebt = priced.sumOf { it.currentBalance.coerceAtLeast(0.0) }
    val debt = creditAccounts.sumOf { it.currentBalance }
    val assets = cash + invested + (bitcoinUsd ?: 0.0) + homeValue
    return NetWorthSummary(
        cash = cash,
        invested = invested,
        bitcoinSats = bitcoinSats,
        bitcoinUsd = bitcoinUsd,
        homeValue = homeValue,
        mortgageDebt = mortgageDebt,
        homeEquity = homeValue - mortgageDebt,
        otherDebt = debt - mortgageDebt,
        missingHomePrice = mortgages.any { it.homePrice <= 0.0 && it.currentBalance > 0.0 },
        debt = debt,
        assets = assets,
        total = assets - debt,
        hasData = withBalance.isNotEmpty() || wallets.isNotEmpty() || creditAccounts.isNotEmpty()
    )
}
