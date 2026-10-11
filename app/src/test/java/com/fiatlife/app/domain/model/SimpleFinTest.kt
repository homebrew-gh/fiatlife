package com.fiatlife.app.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleFinTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun stripsCredentialsFromAccessUrl() {
        val access = SimpleFin.parseAccessUrl("https://user%40x:p%3Ass@bridge.example.com/simplefin")!!
        assertEquals("user@x", access.username)
        assertEquals("p:ss", access.password)
        assertEquals("https://bridge.example.com/simplefin", access.base)
        assertEquals(
            "https://bridge.example.com/simplefin/accounts?version=2&balances-only=1",
            access.balancesUrl
        )
        assertFalse(access.toString().contains("p:ss"))
    }

    @Test
    fun rejectsInvalidAccessUrls() {
        assertNull(SimpleFin.parseAccessUrl("http://user:pass@bridge.example.com/simplefin"))
        assertNull(SimpleFin.parseAccessUrl("https://bridge.example.com/simplefin"))
        assertNull(SimpleFin.parseAccessUrl("not a url"))
    }

    @Test
    fun parsesV2AccountSet() {
        val body = """
            {
              "errlist": [{"code": "con.auth", "msg": "Authentication failed\u0007 for My Bank", "conn_id": "CON-1"}],
              "connections": [{"conn_id": "CON-1", "name": "My Bank", "org_id": "ORG", "sfin_url": "https://x"}],
              "accounts": [
                {"id": "A1", "name": "Checking", "conn_id": "CON-1", "currency": "USD",
                 "balance": "100.23", "available-balance": "75.23", "balance-date": 978366153},
                {"id": "A2", "name": "Card", "currency": "USD", "balance": -50.5, "balance-date": null},
                {"id": " ", "name": "No id", "balance": "1"}
              ]
            }
        """.trimIndent()
        val snap = SimpleFin.parseAccountSet(body, json)
        assertEquals(2, snap.accounts.size)
        val a = snap.accounts[0]
        assertEquals("CON-1:A1", a.key)
        assertEquals(100.23, a.balance!!, 0.0)
        assertEquals(75.23, a.availableBalance!!, 0.0)
        assertEquals(978_366_153_000L, a.balanceDateMs)
        assertEquals("A2", snap.accounts[1].key)
        assertEquals(-50.5, snap.accounts[1].balance!!, 0.0)
        assertNull(snap.accounts[1].balanceDateMs)
        assertEquals(listOf("Authentication failed for My Bank"), snap.messages)
    }

    @Test
    fun fallsBackToDeprecatedErrors() {
        val snap = SimpleFin.parseAccountSet("""{"errors": ["Reauthenticate Fidelity", 3], "accounts": []}""", json)
        assertEquals(listOf("Reauthenticate Fidelity"), snap.messages)
    }

    @Test
    fun updatesOnlyLinkedBankAccountsThatChanged() {
        val synced = listOf(
            SimpleFinAccount("C:1", balance = 200.0, availableBalance = 150.0, balanceDateMs = 2_000),
            SimpleFinAccount("C:2", balance = 50.0, availableBalance = null, balanceDateMs = 2_000),
            SimpleFinAccount("C:3", balance = null, availableBalance = null, balanceDateMs = 2_000)
        )
        val accounts = listOf(
            BankAccount(id = "changed", simplefinAccountKey = "C:1", balance = 100.0, balanceAsOf = 1_000),
            BankAccount(id = "same", simplefinAccountKey = "C:2", balance = 50.0, balanceAsOf = 2_000),
            BankAccount(id = "noBalance", simplefinAccountKey = "C:3", balance = 10.0),
            BankAccount(id = "unlinked", balance = 1.0),
            BankAccount(id = "newer", simplefinAccountKey = "C:1", balance = 300.0, balanceAsOf = 3_000)
        )
        val updates = SimpleFin.bankUpdates(accounts, synced)
        assertEquals(listOf("changed"), updates.map { it.id })
        assertEquals(200.0, updates[0].balance!!, 0.0)
        assertEquals(150.0, updates[0].availableBalance!!, 0.0)
        assertEquals(2_000L, updates[0].balanceAsOf)
    }

    @Test
    fun flagsStaleAndUnmatchedLinks() {
        val now = 10 * 86_400_000L
        val synced = listOf(
            SimpleFinAccount("C:fresh", balance = 1.0, availableBalance = null, balanceDateMs = now - 86_400_000L),
            SimpleFinAccount("C:old", balance = 1.0, availableBalance = null, balanceDateMs = now - 4 * 86_400_000L),
            SimpleFinAccount("C:unlinked", balance = 1.0, availableBalance = null, balanceDateMs = 0)
        )
        assertEquals(now - 4 * 86_400_000L, SimpleFin.staleSince(setOf("C:fresh", "C:old"), synced, now))
        assertNull(SimpleFin.staleSince(setOf("C:fresh"), synced, now))
        assertEquals(setOf("C:gone"), SimpleFin.unmatchedKeys(setOf("C:fresh", "C:gone"), synced))
    }

    @Test
    fun debtUpdatesKeepManualBalanceUntilInstitutionChanges() {
        val synced = listOf(
            SimpleFinAccount("C:card", balance = -1234.567, availableBalance = null, balanceDateMs = 2_000),
            SimpleFinAccount("C:loan", balance = -500.0, availableBalance = null, balanceDateMs = 2_000)
        )
        val accounts = listOf(
            CreditAccount(id = "card", simplefinAccountKey = "C:card", currentBalance = 1000.0, simplefinBalance = 1000.0),
            CreditAccount(id = "loan", simplefinAccountKey = "C:loan", currentBalance = 400.0, simplefinBalance = 500.0)
        )
        val updates = SimpleFin.debtUpdates(accounts, synced)
        assertEquals(listOf("card"), updates.map { it.id })
        assertEquals(1234.57, updates[0].currentBalance, 0.0)
        assertEquals(1234.57, updates[0].simplefinBalance!!, 0.0)
        assertEquals(2_000L, updates[0].simplefinBalanceAsOf)
        assertTrue(SimpleFin.debtUpdates(listOf(updates[0].copy(simplefinBalanceAsOf = 3_000, simplefinBalance = 1.0)), synced).isEmpty())
    }
}
