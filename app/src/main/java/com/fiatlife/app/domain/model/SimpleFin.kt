package com.fiatlife.app.domain.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.math.abs
import kotlin.math.roundToLong

/** Balances of one account from a SimpleFIN `/accounts` response. Mirrors the web server's `simplefin.rs`. */
data class SimpleFinAccount(
    /** Stable link key: `conn_id:id` (account ids are only unique per connection). */
    val key: String,
    val balance: Double?,
    val availableBalance: Double?,
    val balanceDateMs: Long?
)

data class SimpleFinSnapshot(
    val accounts: List<SimpleFinAccount>,
    val messages: List<String>
)

/** Access URL split into a credential-free base and its HTTP Basic credentials. */
class SimpleFinAccess(val base: String, val username: String, val password: String) {
    val balancesUrl: String get() = "${base.trimEnd('/')}/accounts?version=2&balances-only=1"

    override fun toString(): String = "SimpleFinAccess($base)"
}

object SimpleFin {
    private const val MAX_TEXT_LEN = 300

    /** Bank data older than this means SimpleFIN Bridge isn't getting fresh balances from the bank. */
    const val STALE_MS = 2 * 24 * 60 * 60 * 1000L

    /** Oldest balance date among linked accounts whose bank data is stale, or null if none are. */
    fun staleSince(linkedKeys: Set<String>, synced: List<SimpleFinAccount>, now: Long): Long? =
        synced.filter { it.key in linkedKeys }
            .mapNotNull { it.balanceDateMs }
            .filter { now - it > STALE_MS }
            .minOrNull()

    /** Linked keys the latest fetch didn't return (e.g. after reconnecting a bank in Bridge). */
    fun unmatchedKeys(linkedKeys: Set<String>, synced: List<SimpleFinAccount>): Set<String> =
        linkedKeys - synced.map { it.key }.toSet()

    /** Null unless [raw] is an https URL carrying a username. */
    fun parseAccessUrl(raw: String): SimpleFinAccess? {
        val uri = runCatching { java.net.URI(raw.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        val userInfo = uri.rawUserInfo ?: return null
        val authority = uri.rawAuthority?.substringAfterLast('@') ?: return null
        val username = percentDecode(userInfo.substringBefore(':'))
        val password = if (':' in userInfo) percentDecode(userInfo.substringAfter(':')) else ""
        if (username.isEmpty() || authority.isEmpty()) return null
        return SimpleFinAccess("https://$authority${uri.rawPath.orEmpty()}", username, password)
    }

    private fun percentDecode(input: String): String {
        val bytes = input.toByteArray(Charsets.UTF_8)
        val out = java.io.ByteArrayOutputStream(bytes.size)
        var i = 0
        while (i < bytes.size) {
            if (bytes[i] == '%'.code.toByte() && i + 2 < bytes.size) {
                val hi = Character.digit(bytes[i + 1].toInt(), 16)
                val lo = Character.digit(bytes[i + 2].toInt(), 16)
                if (hi >= 0 && lo >= 0) {
                    out.write(hi shl 4 or lo)
                    i += 3
                    continue
                }
            }
            out.write(bytes[i].toInt())
            i++
        }
        return out.toString(Charsets.UTF_8.name())
    }

    fun parseAccountSet(body: String, json: Json): SimpleFinSnapshot {
        val root = json.parseToJsonElement(body).jsonObject
        val accounts = root.array("accounts").mapNotNull { element ->
            val a = element as? JsonObject ?: return@mapNotNull null
            val id = a.text("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val connId = a.text("conn_id")
            SimpleFinAccount(
                key = connId?.takeIf { it.isNotEmpty() }?.let { "$it:$id" } ?: id,
                balance = a.number("balance"),
                availableBalance = a.number("available-balance"),
                balanceDateMs = a.number("balance-date")?.let { (it * 1000).toLong() }
            )
        }

        var messages = root.array("errlist")
            .mapNotNull { (it as? JsonObject)?.text("msg") }
            .map(::sanitize)
        if (messages.isEmpty()) {
            messages = root.array("errors")
                .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                .map(::sanitize)
        }
        return SimpleFinSnapshot(accounts, messages.filter { it.isNotEmpty() })
    }

    private fun JsonObject.array(name: String): List<JsonElement> = (this[name] as? JsonArray).orEmpty()

    private fun JsonObject.text(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.number(name: String): Double? =
        (this[name] as? JsonPrimitive)?.content?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }

    /** Server-provided text is untrusted: drop control characters and cap length. */
    private fun sanitize(text: String): String =
        text.filterNot { it.isISOControl() }.take(MAX_TEXT_LEN).trim()

    /** A balance dated before the one stored is stale (e.g. a cached snapshot); keep the newer one. */
    private fun isOlder(sourceMs: Long?, storedMs: Long?): Boolean =
        sourceMs != null && storedMs != null && sourceMs < storedMs

    /** Linked bank accounts whose stored balance differs from the latest fetch. Mirrors web `simplefin.ts`. */
    fun bankUpdates(accounts: List<BankAccount>, synced: List<SimpleFinAccount>): List<BankAccount> {
        val byKey = synced.associateBy { it.key }
        return accounts.mapNotNull { account ->
            val source = account.simplefinAccountKey?.let(byKey::get) ?: return@mapNotNull null
            if (source.balance == null || isOlder(source.balanceDateMs, account.balanceAsOf)) return@mapNotNull null
            if (account.balance == source.balance &&
                account.availableBalance == source.availableBalance &&
                account.balanceAsOf == source.balanceDateMs
            ) return@mapNotNull null
            account.copy(
                balance = source.balance,
                availableBalance = source.availableBalance,
                balanceAsOf = source.balanceDateMs
            )
        }
    }

    fun owed(balance: Double): Double = (abs(balance) * 100).roundToLong() / 100.0

    /**
     * Linked debt accounts whose SimpleFIN balance changed since the last sync. A manual
     * balance (e.g. a logged payment that hasn't posted) stands until the institution
     * reports a different amount.
     */
    fun debtUpdates(accounts: List<CreditAccount>, synced: List<SimpleFinAccount>): List<CreditAccount> {
        val byKey = synced.associateBy { it.key }
        return accounts.mapNotNull { account ->
            val source = account.simplefinAccountKey?.let(byKey::get) ?: return@mapNotNull null
            val balance = source.balance ?: return@mapNotNull null
            if (isOlder(source.balanceDateMs, account.simplefinBalanceAsOf)) return@mapNotNull null
            val owed = owed(balance)
            if (account.simplefinBalance == owed) return@mapNotNull null
            account.copy(
                currentBalance = owed,
                simplefinBalance = owed,
                simplefinBalanceAsOf = source.balanceDateMs
            )
        }
    }
}
