package com.fiatlife.app.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetWorthTest {

    @Test
    fun parsesBtcAmountsToSats() {
        assertEquals(50_000_000L, Bitcoin.parseBtcToSats("0.5"))
        assertEquals(50_000_000L, Bitcoin.parseBtcToSats(".5"))
        assertEquals(100_000_000L, Bitcoin.parseBtcToSats("1"))
        assertEquals(123_456_789L, Bitcoin.parseBtcToSats("1.23456789"))
        assertEquals(100_000_000_000L, Bitcoin.parseBtcToSats("1,000"))
        assertEquals(1L, Bitcoin.parseBtcToSats(" 0.00000001 BTC "))
        assertEquals(0L, Bitcoin.parseBtcToSats("0"))
        assertNull(Bitcoin.parseBtcToSats(""))
        assertNull(Bitcoin.parseBtcToSats("."))
        assertNull(Bitcoin.parseBtcToSats("0.000000001"))
        assertNull(Bitcoin.parseBtcToSats("-1"))
        assertNull(Bitcoin.parseBtcToSats("abc"))
    }

    @Test
    fun formatsSatsAsBtc() {
        assertEquals("0.5", Bitcoin.formatBtc(50_000_000L))
        assertEquals("1", Bitcoin.formatBtc(100_000_000L))
        assertEquals("0.00000001", Bitcoin.formatBtc(1L))
        assertEquals("1,000.12", Bitcoin.formatBtc(100_012_000_000L))
        assertEquals("0", Bitcoin.formatBtc(0L))
    }

    @Test
    fun decodesWebWalletRecord() {
        val json = Json { ignoreUnknownKeys = true }
        val wallet = json.decodeFromString(
            BitcoinWallet.serializer(),
            """{"id":"w1","name":"Cold","sats":25000000,"notes":"","retirement":true,"createdAt":1,"updatedAt":2}"""
        )
        assertEquals(BitcoinWallet("w1", "Cold", 25_000_000L, "", true, 1L, 2L), wallet)
    }

    @Test
    fun netWorthMatchesWebMath() {
        val bank = listOf(
            BankAccount(id = "c", name = "Checking", type = "CHECKING", balance = 5_000.0),
            BankAccount(id = "s", name = "Savings", type = "SAVINGS", balance = 10_000.0),
            BankAccount(id = "r", name = "401k", type = "RETIREMENT", balance = 50_000.0),
            BankAccount(id = "n", name = "No balance", type = "CHECKING")
        )
        val wallets = listOf(
            BitcoinWallet(id = "a", name = "Cold", sats = 50_000_000L),
            BitcoinWallet(id = "b", name = "IRA", sats = 25_000_000L, retirement = true)
        )
        val credit = listOf(
            CreditAccount(id = "m", name = "Mortgage", type = CreditAccountType.MORTGAGE, currentBalance = 200_000.0, homePrice = 300_000.0),
            CreditAccount(id = "v", name = "Visa", type = CreditAccountType.CREDIT_CARD, currentBalance = 2_000.0)
        )

        val s = computeNetWorth(bank, wallets, usdPerBtc = 100_000.0, creditAccounts = credit)
        assertEquals(15_000.0, s.cash, 0.001)
        assertEquals(50_000.0, s.invested, 0.001)
        assertEquals(75_000_000L, s.bitcoinSats)
        assertEquals(75_000.0, s.bitcoinUsd!!, 0.001)
        assertEquals(300_000.0, s.homeValue, 0.001)
        assertEquals(100_000.0, s.homeEquity, 0.001)
        assertEquals(2_000.0, s.otherDebt, 0.001)
        assertEquals(202_000.0, s.debt, 0.001)
        assertEquals(440_000.0, s.assets, 0.001)
        assertEquals(238_000.0, s.total, 0.001)
        assertFalse(s.missingHomePrice)
    }

    @Test
    fun unpricedMortgageAndMissingBtcPrice() {
        val wallets = listOf(BitcoinWallet(id = "a", name = "Cold", sats = 10_000_000L))
        val credit = listOf(
            CreditAccount(id = "m", name = "Mortgage", type = CreditAccountType.MORTGAGE, currentBalance = 150_000.0)
        )
        val s = computeNetWorth(emptyList(), wallets, usdPerBtc = null, creditAccounts = credit)
        assertNull(s.bitcoinUsd)
        assertTrue(s.missingHomePrice)
        assertEquals(0.0, s.homeValue, 0.001)
        assertEquals(150_000.0, s.otherDebt, 0.001)
        assertEquals(-150_000.0, s.total, 0.001)

        val noWallets = computeNetWorth(emptyList(), emptyList(), usdPerBtc = null, creditAccounts = emptyList())
        assertEquals(0.0, noWallets.bitcoinUsd!!, 0.001)
        assertFalse(noWallets.hasData)
    }
}
