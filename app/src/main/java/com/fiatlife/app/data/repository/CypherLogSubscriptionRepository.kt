package com.fiatlife.app.data.repository

import android.util.Log
import com.fiatlife.app.data.local.dao.CypherLogSubscriptionDao
import com.fiatlife.app.data.local.entity.CypherLogSubscriptionEntity
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.data.nostr.NostrEvent
import com.fiatlife.app.domain.model.Bill
import com.fiatlife.app.domain.model.BillCategory
import com.fiatlife.app.domain.model.BillFrequency
import com.fiatlife.app.domain.model.BillRecurrenceUnit
import com.fiatlife.app.domain.model.BillSource
import com.fiatlife.app.domain.model.BillSubcategory
import com.fiatlife.app.domain.model.BillWithSource
import com.fiatlife.app.domain.model.BillPayment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CypherLogSubRepo"
private const val DELETE_TOMBSTONE_DTAG_PREFIX = "fiatlife/cypherlog_deleted/"
private const val SUBSCRIPTION_DTAG_PREFIX = "subscription:"

/** CypherLog 37004 tag keys we map to Bill; all others are preserved for round-trip */
private val MAPPED_TAG_KEYS = setOf(
    "d", "id", "name", "cost", "amount", "currency", "billing_frequency", "recurrence", "subscription_type",
    "company_name", "company_id", "notes", "alt", "due_day",
    "renewal_date", "next_due_date", "due_date",
    "initial_purchase_date", "purchase_date", "anchor_date", "start_date",
    "interval_unit", "interval_count", "timezone",
    "fiatlife_is_recurring", "fiatlife_rate_valid_until",
    "fiatlife_is_cancelled", "fiatlife_cancelled_at",
    "fiatlife_is_paid", "fiatlife_last_paid_date", "fiatlife_payment",
    "fiatlife_pay_from_bank_id", "fiatlife_pay_from_credit_id"
)

/** True if [content] looks like plaintext subscription JSON (CypherLog on private relay often sends unencrypted). */
private fun isPlaintextSubscriptionJson(content: String): Boolean {
    val trimmed = content.trim()
    if (trimmed.isEmpty() || (!trimmed.startsWith("{") && !trimmed.startsWith("["))) return false
    return try {
        Json.parseToJsonElement(trimmed)
        true
    } catch (_: Exception) {
        false
    }
}

/** Build a tag map with lowercase keys so lookup is case-insensitive (Nostr/CypherLog may use varying case). */
private fun tagsToMap(tags: List<List<String>>): Map<String, List<String>> {
    val map = mutableMapOf<String, MutableList<String>>()
    tags.forEach { pair ->
        if (pair.size >= 2) {
            val key = pair[0].lowercase()
            map.getOrPut(key) { mutableListOf() }.add(pair[1])
        }
    }
    return map
}

/** When name is empty, try to derive from CypherLog "alt" tag (e.g. "Subscription: Netflix"). */
private fun nameFromAltTag(tagMap: Map<String, List<String>>): String {
    val alt = tagMap["alt"]?.firstOrNull() ?: return ""
    val lower = alt.lowercase()
    if (lower.contains("encrypted") && lower.contains("subscription data")) return ""
    return alt.removePrefix("Subscription:").removePrefix("subscription:").trim()
}

/** Map CypherLog subscription_type to BillSubcategory (spec: Streaming, Software, Health/Wellness, etc.). */
private fun subscriptionTypeToSubcategory(value: String?): BillSubcategory {
    val v = value?.trim()?.lowercase() ?: return BillSubcategory.OTHER_SUBSCRIPTION
    return when (v) {
        "streaming" -> BillSubcategory.STREAMING
        "software" -> BillSubcategory.SOFTWARE
        "health/wellness", "health", "wellness" -> BillSubcategory.HEALTH_WELLNESS
        "shopping" -> BillSubcategory.SHOPPING
        "vehicle" -> BillSubcategory.VEHICLE
        "food" -> BillSubcategory.FOOD
        "gaming" -> BillSubcategory.GAMING
        "news/media", "news", "media" -> BillSubcategory.NEWS_MEDIA
        "music" -> BillSubcategory.MUSIC
        "home" -> BillSubcategory.SUB_HOME
        "finance" -> BillSubcategory.FINANCE
        "pet care", "petcare" -> BillSubcategory.PET_CARE
        "education" -> BillSubcategory.EDUCATION
        "travel" -> BillSubcategory.TRAVEL
        "firearm", "firearms", "2a", "gun", "guns" -> BillSubcategory.FIREARM
        else -> BillSubcategory.OTHER_SUBSCRIPTION
    }
}

private fun parseIsoDateToMillis(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    val input = value.trim()
    val isoParts = input.split("-")
    val (year, month, day) = if (isoParts.size == 3) {
        Triple(
            isoParts[0].toIntOrNull() ?: return null,
            isoParts[1].toIntOrNull() ?: return null,
            isoParts[2].toIntOrNull() ?: return null
        )
    } else {
        // CypherLog compatibility: start_date currently uses MM/dd/yyyy.
        val usParts = input.split("/")
        if (usParts.size != 3) return null
        Triple(
            usParts[2].toIntOrNull() ?: return null,
            usParts[0].toIntOrNull() ?: return null,
            usParts[1].toIntOrNull() ?: return null
        )
    }
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.YEAR, year)
    cal.set(java.util.Calendar.MONTH, (month - 1).coerceIn(0, 11))
    cal.set(java.util.Calendar.DAY_OF_MONTH, day.coerceAtLeast(1))
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun formatIsoDate(millis: Long): String {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = millis
    val y = cal.get(java.util.Calendar.YEAR)
    val m = cal.get(java.util.Calendar.MONTH) + 1
    val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
    return String.format(java.util.Locale.US, "%04d-%02d-%02d", y, m, d)
}

private fun formatUsDate(millis: Long): String {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = millis
    val y = cal.get(java.util.Calendar.YEAR)
    val m = cal.get(java.util.Calendar.MONTH) + 1
    val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
    return String.format(java.util.Locale.US, "%02d/%02d/%04d", m, d, y)
}

private fun dayOfMonthFromMillis(millis: Long?): Int? {
    if (millis == null) return null
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = millis
    return cal.get(java.util.Calendar.DAY_OF_MONTH).coerceIn(1, 31)
}

private fun intervalUnitFromCypherLog(value: String?): BillRecurrenceUnit? = when (value?.trim()?.lowercase()) {
    "day", "days" -> BillRecurrenceUnit.DAY
    "week", "weeks" -> BillRecurrenceUnit.WEEK
    "month", "months" -> BillRecurrenceUnit.MONTH
    "year", "years" -> BillRecurrenceUnit.YEAR
    else -> null
}

private fun intervalUnitToCypherLog(unit: BillRecurrenceUnit?): String? = when (unit) {
    BillRecurrenceUnit.DAY -> "day"
    BillRecurrenceUnit.WEEK -> "week"
    BillRecurrenceUnit.MONTH -> "month"
    BillRecurrenceUnit.YEAR -> "year"
    null -> null
}

private fun parseFiatLifePayment(value: String): BillPayment? {
    val parts = value.split("|")
    if (parts.size != 2) return null
    val date = parts[0].toLongOrNull() ?: return null
    val amount = parts[1].toDoubleOrNull() ?: return null
    return BillPayment(date = date, amount = amount)
}

@Singleton
class CypherLogSubscriptionRepository @Inject constructor(
    private val dao: CypherLogSubscriptionDao,
    private val nostrClient: NostrClient,
    private val json: Json
) {
    private fun toSubscriptionDTag(idOrDTag: String): String {
        val value = idOrDTag.trim()
        if (value.startsWith(SUBSCRIPTION_DTAG_PREFIX)) return value
        return "$SUBSCRIPTION_DTAG_PREFIX$value"
    }

    private fun rawIdFromDTag(dTag: String): String =
        dTag.removePrefix(SUBSCRIPTION_DTAG_PREFIX)

    data class SaveResult(
        val success: Boolean,
        val reason: String = ""
    )

    fun observeHasData(): Flow<Boolean> =
        dao.observeCount().map { it > 0 }

    fun getAllAsBills(): Flow<List<BillWithSource>> {
        return dao.getAll().map { entities ->
            entities.mapNotNull { entity -> entityToBillWithSource(entity) }
        }.decodeOnBackground()
    }

    fun getByDTag(dTag: String): Flow<BillWithSource?> {
        return dao.getByEitherDTagAsFlow(toSubscriptionDTag(dTag), dTag).map { entity ->
            entity?.let { entityToBillWithSource(it) }
        }.decodeOnBackground()
    }

    suspend fun upsertFromEvent(event: NostrEvent) {
        val entity = buildEntityFromEvent(event) ?: return
        dao.upsert(entity)
        Log.d(TAG, "Upserted 37004 d=${entity.dTag}")
    }

    private suspend fun buildEntityFromEvent(event: NostrEvent): CypherLogSubscriptionEntity? {
        val dTagRaw = event.tags.firstOrNull { it.size >= 2 && it[0] == "d" }?.getOrNull(1) ?: return null
        val dTag = toSubscriptionDTag(dTagRaw)
        val tagsJson = buildJsonArray {
            event.tags.forEach { tag ->
                add(buildJsonArray { tag.forEach { add(JsonPrimitive(it)) } })
            }
        }.toString()
        var contentDecryptedJson: String? = null
        if (event.content.isNotBlank()) {
            val rawContent = event.content.trim()
            if (isPlaintextSubscriptionJson(rawContent)) {
                contentDecryptedJson = rawContent
                Log.d(TAG, "37004 d=$dTag: using plaintext content (private-relay style)")
            } else {
                val signer = nostrClient.currentSigner
                if (signer != null) {
                    val pTagPubkeys = event.tags
                        .filter { it.size >= 2 && it[0] == "p" }
                        .map { it[1] }
                    val candidates = (listOf(event.pubkey, signer.pubkeyHex) + pTagPubkeys)
                        .filter { it.isNotBlank() }
                        .distinct()
                    for (candidate in candidates) {
                        val raw = signer.nip44Decrypt(event.content, candidate)?.trim()
                        if (raw.isNullOrBlank()) continue
                        val lower = raw.lowercase()
                        if (lower.contains("could not decrypt")) continue
                        if (isPlaintextSubscriptionJson(raw)) {
                            contentDecryptedJson = raw
                            break
                        }
                    }
                    if (contentDecryptedJson == null) {
                        Log.w(
                            TAG,
                            "Failed to decrypt 37004 content for d=$dTag (author=${event.pubkey.take(8)}…, pTags=${pTagPubkeys.size})"
                        )
                    }
                }
            }
        }
        return CypherLogSubscriptionEntity(
            dTag = dTag,
            eventId = event.id,
            tagsJson = tagsJson,
            createdAt = event.created_at,
            contentDecryptedJson = contentDecryptedJson
        )
    }

    /**
     * Publish a new or updated subscription (37004) and upsert locally.
     * [preservedTags] are re-emitted so CypherLog keeps company/vehicle links.
     */
    suspend fun saveSubscription(
        bill: Bill,
        preservedTags: Map<String, List<String>>? = null
    ): Boolean {
        return saveSubscriptionDetailed(bill, preservedTags).success
    }

    suspend fun saveSubscriptionDetailed(
        bill: Bill,
        preservedTags: Map<String, List<String>>? = null
    ): SaveResult {
        val rawId = bill.id.ifEmpty { UUID.randomUUID().toString() }
        val dTag = toSubscriptionDTag(rawId)
        val tags = billTo30078Tags(bill.copy(id = rawId), preservedTags, dTag, rawId)
        val status = nostrClient.publishReplaceable30078Detailed(dTag, tags, content = "")
        if (status.success) {
            val tagsJson = buildJsonArray {
                tags.forEach { tag ->
                    add(buildJsonArray { tag.forEach { add(JsonPrimitive(it)) } })
                }
            }.toString()
            dao.upsert(
                CypherLogSubscriptionEntity(
                    dTag = dTag,
                    eventId = "",
                    tagsJson = tagsJson,
                    createdAt = System.currentTimeMillis() / 1000
                )
            )
            return SaveResult(success = true)
        }
        val reason = when (status.stage) {
            "no_signer" -> "No signer configured."
            "sign_event" -> "Amber signing was rejected/cancelled or not supported for this event."
            "publish_event" -> "Signed event could not be sent to relay."
            else -> "Unknown publish error."
        } + if (status.detail.isNotBlank()) " ${status.detail}" else ""
        return SaveResult(success = false, reason = reason)
    }

    suspend fun deleteSubscription(dTag: String) {
        publishDeleteTombstone(dTag)
        if (nostrClient.hasSigner) {
            try {
                nostrClient.publishDeletion(NostrEvent.KIND_APP_SPECIFIC_DATA, toSubscriptionDTag(dTag))
                Log.d(TAG, "Published NIP-09 deletion for 30078 d=${toSubscriptionDTag(dTag)}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish 30078 deletion: ${e.message}")
            }
        }
        dao.deleteByDTag(toSubscriptionDTag(dTag))
        dao.deleteByDTag(dTag)
    }

    suspend fun syncFromRelay() {
        if (!nostrClient.hasSigner) return
        try {
            withTimeout(30_000) {
                val deletedDTags = loadDeletedDTagsFromRelay()
                val deleteDTags = mutableListOf<String>()
                if (deletedDTags.isNotEmpty()) {
                    deletedDTags.forEach {
                        deleteDTags.add(toSubscriptionDTag(it))
                        deleteDTags.add(it)
                    }
                }
                val upsertsByDTag = mutableMapOf<String, CypherLogSubscriptionEntity>()
                val seenDTags = mutableSetOf<String>()
                nostrClient.subscribeToKind30078ByDTagPrefix(SUBSCRIPTION_DTAG_PREFIX).collect { event ->
                    val dTag = event.tags.firstOrNull { it.size >= 2 && it[0] == "d" }?.getOrNull(1) ?: return@collect
                    val normalizedDTag = toSubscriptionDTag(dTag)
                    if (rawIdFromDTag(normalizedDTag) in deletedDTags || normalizedDTag in deletedDTags) {
                        deleteDTags.add(normalizedDTag)
                        upsertsByDTag.remove(normalizedDTag)
                        return@collect
                    }
                    val entity = buildEntityFromEvent(event) ?: return@collect
                    upsertsByDTag[normalizedDTag] = entity
                    seenDTags.add(normalizedDTag)
                }
                // Prune stale local rows that no longer exist on relay (prevents ghost/deleted reappearance).
                val local = dao.getAllSnapshot().map { toSubscriptionDTag(it.dTag) }
                val stale = local.filter { it !in seenDTags && rawIdFromDTag(it) !in deletedDTags }
                stale.forEach {
                    deleteDTags.add(it)
                    deleteDTags.add(rawIdFromDTag(it))
                }
                dao.applySyncBatch(upsertsByDTag.values.toList(), deleteDTags.distinct())
                Log.d(TAG, "Synced ${upsertsByDTag.size} 30078 subscription(s) from relay; pruned ${stale.size} stale row(s)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "30078 subscription sync failed: ${e.message}")
        }
    }

    private fun entityToBillWithSource(entity: CypherLogSubscriptionEntity): BillWithSource? {
        val tags = try {
            json.parseToJsonElement(entity.tagsJson).jsonArray.map { arr ->
                arr.jsonArray.map { it.jsonPrimitive.content }
            }
        } catch (_: Exception) {
            emptyList()
        }
        if (!isRenderableSubscription(tags, entity.contentDecryptedJson)) return null
        val (bill, preserved) = if (entity.contentDecryptedJson != null) {
            content37004ToBill(entity.dTag, entity.contentDecryptedJson!!, tags)
        } else {
            tags37004ToBill(entity.dTag, tags)
        }
        return BillWithSource(bill = bill, source = BillSource.CYPHERLOG, preservedTags = preserved)
    }

    /** Hide non-renderable encrypted placeholders (d/alt/client only, no parseable fields). */
    private fun isRenderableSubscription(tags: List<List<String>>, contentDecryptedJson: String?): Boolean {
        val tagMap = tagsToMap(tags)
        val name = tagMap["name"]?.firstOrNull()?.trim().orEmpty()
        val cost = tagMap["cost"]?.firstOrNull()?.toDoubleOrNull() ?: tagMap["amount"]?.firstOrNull()?.toDoubleOrNull()
        val frequency = tagMap["billing_frequency"]?.firstOrNull()?.trim().orEmpty()
        val type = tagMap["subscription_type"]?.firstOrNull()?.trim().orEmpty()
        val alt = tagMap["alt"]?.firstOrNull()?.trim().orEmpty().lowercase()
        val altIsPlaceholder = alt.contains("encrypted") && alt.contains("subscription data")

        if (name.isNotBlank() || cost != null || frequency.isNotBlank() || type.isNotBlank()) return true
        if (contentDecryptedJson != null) {
            val lower = contentDecryptedJson.lowercase()
            if (lower.isNotBlank() && !lower.contains("could not decrypt")) return true
        }
        if (alt.isNotBlank() && !altIsPlaceholder) return true
        return false
    }

    /** Parse CypherLog content JSON (same logical fields as tags) and build Bill; preserved from tags. */
    private fun content37004ToBill(dTag: String, contentJson: String, tags: List<List<String>>): Pair<Bill, Map<String, List<String>>?> {
        val tagMap = tagsToMap(tags)
        val preserved = tagMap.filter { (k, _) -> k !in MAPPED_TAG_KEYS }
            .mapValues { (_, v) -> v.toList() }
            .ifEmpty { null }

        val name: String
        val cost: Double
        val frequency: BillFrequency
        val notes: String
        val companyName: String
        val dueDay: Int
        val subcategory: BillSubcategory
        val renewalDateMillis: Long?
        val initialPurchaseDateMillis: Long?
        val recurrenceUnit: BillRecurrenceUnit?
        val recurrenceIntervalCount: Int
        val recurrenceTimezone: String?
        val isRecurring: Boolean
        val rateValidUntilMillis: Long?
        val isPaid: Boolean
        val lastPaidDate: Long?
        val paymentHistory: List<BillPayment>
        val isCancelled: Boolean
        val cancelledAt: Long?
        try {
            val root = json.parseToJsonElement(contentJson)
            val obj: JsonObject = when {
                root is JsonObject && (root.containsKey("name") || root.containsKey("cost") || root.containsKey("billing_frequency")) -> root
                root is JsonObject && root.containsKey("data") -> (root["data"]?.jsonObject ?: root)
                root is JsonObject -> root
                root is JsonArray && root.isNotEmpty() -> root.first().jsonObject
                else -> {
                    Log.w(TAG, "37004 content for d=$dTag: root is not object or array; snippet: ${contentJson.take(80)}…")
                    return tags37004ToBill(dTag, tags)
                }
            }
            fun str(vararg keys: String): String? = keys.mapNotNull { key ->
                obj[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            }.firstOrNull()
            fun numFromElement(e: kotlinx.serialization.json.JsonElement?): Double? = when {
                e == null -> null
                e is JsonPrimitive -> e.content.toDoubleOrNull()
                else -> null
            }
            fun doubleVal(vararg keys: String): Double? = keys.mapNotNull { key -> numFromElement(obj[key]) }.firstOrNull()
                ?: keys.mapNotNull { key -> str(key)?.toDoubleOrNull() }.firstOrNull()
            var nameFromContent = str("name", "subscriptionName", "subscription_name", "title", "description") ?: ""
            cost = doubleVal("cost", "amount", "price", "costAmount", "subscriptionCost") ?: 0.0
            frequency = billingFrequencyToBillFrequency(str("billing_frequency", "billingFrequency", "recurrence"))
            notes = str("notes") ?: ""
            companyName = str("company_name", "companyName") ?: ""
            val parsedDueDay = str("due_day")?.toIntOrNull()?.coerceIn(1, 31)
                ?: preserved?.get("due_day")?.firstOrNull()?.toIntOrNull()?.coerceIn(1, 31)
            renewalDateMillis = parseIsoDateToMillis(
                str("renewal_date", "next_due_date", "due_date")
                    ?: tagMap["renewal_date"]?.firstOrNull()
                    ?: tagMap["next_due_date"]?.firstOrNull()
                    ?: tagMap["due_date"]?.firstOrNull()
            )
            initialPurchaseDateMillis = parseIsoDateToMillis(
                str("initial_purchase_date", "purchase_date", "anchor_date", "start_date")
                    ?: tagMap["initial_purchase_date"]?.firstOrNull()
                    ?: tagMap["purchase_date"]?.firstOrNull()
                    ?: tagMap["anchor_date"]?.firstOrNull()
                    ?: tagMap["start_date"]?.firstOrNull()
            )
            recurrenceUnit = intervalUnitFromCypherLog(
                str("interval_unit") ?: tagMap["interval_unit"]?.firstOrNull()
            )
            recurrenceIntervalCount = (str("interval_count") ?: tagMap["interval_count"]?.firstOrNull())
                ?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            recurrenceTimezone = str("timezone") ?: tagMap["timezone"]?.firstOrNull()
            isRecurring = (str("fiatlife_is_recurring") ?: tagMap["fiatlife_is_recurring"]?.firstOrNull())
                ?.equals("false", ignoreCase = true) != true
            rateValidUntilMillis = parseIsoDateToMillis(
                str("fiatlife_rate_valid_until") ?: tagMap["fiatlife_rate_valid_until"]?.firstOrNull()
            )
            isCancelled = (str("fiatlife_is_cancelled") ?: tagMap["fiatlife_is_cancelled"]?.firstOrNull())
                ?.equals("true", ignoreCase = true) == true
            cancelledAt = (str("fiatlife_cancelled_at") ?: tagMap["fiatlife_cancelled_at"]?.firstOrNull())
                ?.toLongOrNull()
            isPaid = (str("fiatlife_is_paid") ?: tagMap["fiatlife_is_paid"]?.firstOrNull())
                ?.equals("true", ignoreCase = true) == true
            lastPaidDate = (str("fiatlife_last_paid_date") ?: tagMap["fiatlife_last_paid_date"]?.firstOrNull())
                ?.toLongOrNull()
            paymentHistory = (tagMap["fiatlife_payment"] ?: emptyList())
                .mapNotNull { parseFiatLifePayment(it) }
            dueDay = parsedDueDay
                ?: dayOfMonthFromMillis(renewalDateMillis)
                ?: dayOfMonthFromMillis(initialPurchaseDateMillis)
                ?: 1
            if (nameFromContent.isBlank()) {
                nameFromContent = nameFromAltTag(tagMap)
            }
            name = nameFromContent
            val subscriptionTypeFromContent = str("subscription_type", "subscriptionType")
            subcategory = subscriptionTypeToSubcategory(subscriptionTypeFromContent ?: tagMap["subscription_type"]?.firstOrNull())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse 37004 content for d=$dTag: ${e.message}; content snippet: ${contentJson.take(200)}…")
            return tags37004ToBill(dTag, tags)
        }

        val bill = Bill(
            id = tagMap["id"]?.firstOrNull()?.ifBlank { null } ?: rawIdFromDTag(dTag),
            name = name.ifBlank { "Subscription" },
            amount = cost,
            category = BillCategory.OTHER,
            subcategory = subcategory,
            frequency = frequency,
            dueDay = dueDay,
            renewalDateMillis = renewalDateMillis,
            initialPurchaseDateMillis = initialPurchaseDateMillis,
            recurrenceUnit = recurrenceUnit,
            recurrenceIntervalCount = recurrenceIntervalCount,
            recurrenceTimezone = recurrenceTimezone,
            isRecurring = isRecurring,
            rateValidUntilMillis = rateValidUntilMillis,
            isCancelled = isCancelled,
            cancelledAt = cancelledAt,
            paymentHistory = paymentHistory,
            isPaid = isPaid,
            lastPaidDate = lastPaidDate,
            accountName = companyName,
            billerName = companyName,
            notes = notes,
            updatedAt = 0L,
            payFromBankAccountId = tagMap["fiatlife_pay_from_bank_id"]?.firstOrNull()?.takeIf { it.isNotBlank() },
            payFromCreditAccountId = tagMap["fiatlife_pay_from_credit_id"]?.firstOrNull()?.takeIf { it.isNotBlank() }
        )
        return bill to preserved
    }

    private fun tags37004ToBill(dTag: String, tags: List<List<String>>): Pair<Bill, Map<String, List<String>>?> {
        val tagMap = tagsToMap(tags)
        fun first(key: String): String? = tagMap[key]?.firstOrNull()

        var name = first("name") ?: ""
        if (name.isBlank()) name = nameFromAltTag(tagMap)
        val cost = first("cost")?.toDoubleOrNull() ?: first("amount")?.toDoubleOrNull() ?: 0.0
        val frequency = billingFrequencyToBillFrequency(first("billing_frequency") ?: first("recurrence"))
        val notes = first("notes") ?: ""
        val companyName = first("company_name") ?: ""
        val parsedDueDay = first("due_day")?.toIntOrNull()?.coerceIn(1, 31)
        val subcategory = subscriptionTypeToSubcategory(first("subscription_type"))
        val renewalDateMillis = parseIsoDateToMillis(first("renewal_date") ?: first("next_due_date") ?: first("due_date"))
        val initialPurchaseDateMillis = parseIsoDateToMillis(
            first("initial_purchase_date") ?: first("purchase_date") ?: first("anchor_date") ?: first("start_date")
        )
        val dueDay = parsedDueDay
            ?: dayOfMonthFromMillis(renewalDateMillis)
            ?: dayOfMonthFromMillis(initialPurchaseDateMillis)
            ?: 1
        val recurrenceUnit = intervalUnitFromCypherLog(first("interval_unit"))
        val recurrenceIntervalCount = first("interval_count")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val recurrenceTimezone = first("timezone")
        val isRecurring = first("fiatlife_is_recurring")?.equals("false", ignoreCase = true) != true
        val rateValidUntilMillis = parseIsoDateToMillis(first("fiatlife_rate_valid_until"))
        val isCancelled = first("fiatlife_is_cancelled")?.equals("true", ignoreCase = true) == true
        val cancelledAt = first("fiatlife_cancelled_at")?.toLongOrNull()
        val isPaid = first("fiatlife_is_paid")?.equals("true", ignoreCase = true) == true
        val lastPaidDate = first("fiatlife_last_paid_date")?.toLongOrNull()
        val paymentHistory = (tagMap["fiatlife_payment"] ?: emptyList())
            .mapNotNull { parseFiatLifePayment(it) }

        val preserved = tagMap.filter { (k, _) -> k !in MAPPED_TAG_KEYS }
            .mapValues { (_, v) -> v.toList() }
            .ifEmpty { null }

        val bill = Bill(
            id = first("id")?.ifBlank { null } ?: rawIdFromDTag(dTag),
            name = name.ifBlank { "Subscription" },
            amount = cost,
            category = BillCategory.OTHER,
            subcategory = subcategory,
            frequency = frequency,
            dueDay = dueDay,
            renewalDateMillis = renewalDateMillis,
            initialPurchaseDateMillis = initialPurchaseDateMillis,
            recurrenceUnit = recurrenceUnit,
            recurrenceIntervalCount = recurrenceIntervalCount,
            recurrenceTimezone = recurrenceTimezone,
            isRecurring = isRecurring,
            rateValidUntilMillis = rateValidUntilMillis,
            isCancelled = isCancelled,
            cancelledAt = cancelledAt,
            paymentHistory = paymentHistory,
            isPaid = isPaid,
            lastPaidDate = lastPaidDate,
            accountName = companyName,
            billerName = companyName,
            notes = notes,
            updatedAt = 0L,
            payFromBankAccountId = first("fiatlife_pay_from_bank_id")?.takeIf { it.isNotBlank() },
            payFromCreditAccountId = first("fiatlife_pay_from_credit_id")?.takeIf { it.isNotBlank() }
        )
        return bill to preserved
    }

    private fun billSubcategoryToSubscriptionType(sub: BillSubcategory): String = when (sub) {
        BillSubcategory.STREAMING -> "Streaming"
        BillSubcategory.SOFTWARE -> "Software"
        BillSubcategory.HEALTH_WELLNESS -> "Health/Wellness"
        BillSubcategory.SHOPPING -> "Shopping"
        BillSubcategory.VEHICLE -> "Vehicle"
        BillSubcategory.FOOD -> "Food"
        BillSubcategory.GAMING -> "Gaming"
        BillSubcategory.NEWS_MEDIA -> "News/Media"
        BillSubcategory.MUSIC -> "Music"
        BillSubcategory.SUB_HOME -> "Home"
        BillSubcategory.FINANCE -> "Finance"
        BillSubcategory.PET_CARE -> "Pet Care"
        BillSubcategory.EDUCATION -> "Education"
        BillSubcategory.TRAVEL -> "Travel"
        BillSubcategory.FIREARM -> "Firearm"
        else -> "Other"
    }

    private fun billTo30078Tags(
        bill: Bill,
        preservedTags: Map<String, List<String>>?,
        dTag: String,
        rawId: String
    ): List<List<String>> {
        val list = mutableListOf<List<String>>()
        list.add(listOf("d", dTag))
        list.add(listOf("id", rawId))
        list.add(listOf("alt", "Subscription: ${bill.name}"))
        list.add(listOf("name", bill.name))
        list.add(listOf("subscription_type", billSubcategoryToSubscriptionType(bill.effectiveSubcategory)))
        list.add(listOf("cost", bill.amount.toString()))
        list.add(listOf("amount", bill.amount.toString()))
        list.add(listOf("billing_frequency", billFrequencyToCypherLog(bill.frequency)))
        list.add(listOf("recurrence", billFrequencyToCypherLog(bill.frequency)))
        list.add(listOf("due_day", bill.dueDay.toString()))
        list.add(listOf("schema_version", "2"))
        list.add(listOf("updated_at", (System.currentTimeMillis() / 1000).toString()))
        bill.renewalDateMillis?.let { list.add(listOf("renewal_date", formatIsoDate(it))) }
        bill.initialPurchaseDateMillis?.let { list.add(listOf("initial_purchase_date", formatIsoDate(it))) }
        bill.initialPurchaseDateMillis?.let { list.add(listOf("start_date", formatUsDate(it))) }
        intervalUnitToCypherLog(bill.recurrenceUnit)?.let { list.add(listOf("interval_unit", it)) }
        if (bill.recurrenceIntervalCount > 1) list.add(listOf("interval_count", bill.recurrenceIntervalCount.toString()))
        if (!bill.recurrenceTimezone.isNullOrBlank()) list.add(listOf("timezone", bill.recurrenceTimezone))
        if (!bill.isRecurring) list.add(listOf("fiatlife_is_recurring", "false"))
        bill.rateValidUntilMillis?.let { list.add(listOf("fiatlife_rate_valid_until", formatIsoDate(it))) }
        if (bill.isCancelled) list.add(listOf("fiatlife_is_cancelled", "true"))
        bill.cancelledAt?.let { list.add(listOf("fiatlife_cancelled_at", it.toString())) }
        if (bill.isPaid) list.add(listOf("fiatlife_is_paid", "true"))
        bill.lastPaidDate?.let { list.add(listOf("fiatlife_last_paid_date", it.toString())) }
        bill.paymentHistory.forEach { p ->
            list.add(listOf("fiatlife_payment", "${p.date}|${p.amount}"))
        }
        if (bill.notes.isNotBlank()) list.add(listOf("notes", bill.notes))
        val companyName = bill.billerName.ifBlank { bill.accountName }
        if (companyName.isNotBlank()) list.add(listOf("company_name", companyName))
        bill.payFromBankAccountId?.takeIf { it.isNotBlank() }?.let { list.add(listOf("fiatlife_pay_from_bank_id", it)) }
        bill.payFromCreditAccountId?.takeIf { it.isNotBlank() }?.let { list.add(listOf("fiatlife_pay_from_credit_id", it)) }
        preservedTags?.forEach { (key, values) ->
            if (key != "d" && key != "id") values.forEach { list.add(listOf(key, it)) }
        }
        return list
    }

    private fun billingFrequencyToBillFrequency(value: String?): BillFrequency = when (value?.lowercase()) {
        "weekly" -> BillFrequency.WEEKLY
        "monthly" -> BillFrequency.MONTHLY
        "quarterly" -> BillFrequency.QUARTERLY
        "semi-annually" -> BillFrequency.SEMIANNUALLY
        "annually" -> BillFrequency.ANNUALLY
        "one-time" -> BillFrequency.ANNUALLY
        else -> BillFrequency.MONTHLY
    }

    private fun billFrequencyToCypherLog(f: BillFrequency): String = when (f) {
        BillFrequency.WEEKLY -> "weekly"
        BillFrequency.MONTHLY -> "monthly"
        BillFrequency.QUARTERLY -> "quarterly"
        BillFrequency.SEMIANNUALLY -> "semi-annually"
        BillFrequency.ANNUALLY -> "annually"
        BillFrequency.BIWEEKLY -> "monthly"
        BillFrequency.BIMONTHLY -> "quarterly"
    }

    /** Local/app-level tombstone so deleted 37004 subscriptions stay hidden across resync and devices. */
    private suspend fun publishDeleteTombstone(dTag: String) {
        if (!nostrClient.hasSigner) return
        val tombstoneDTag = "$DELETE_TOMBSTONE_DTAG_PREFIX$dTag"
        val payload = """{"deleted":true,"dTag":"$dTag","updatedAt":${System.currentTimeMillis()}}"""
        try {
            nostrClient.publishEncryptedAppData(tombstoneDTag, payload)
            Log.d(TAG, "Published CypherLog tombstone for d=$dTag")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to publish CypherLog tombstone for d=$dTag: ${e.message}")
        }
    }

    private suspend fun loadDeletedDTagsFromRelay(): Set<String> {
        val deleted = mutableSetOf<String>()
        try {
            withTimeout(12_000) {
                nostrClient.subscribeToAppData(dTagPrefix = DELETE_TOMBSTONE_DTAG_PREFIX).collect { (eventDTag, decrypted) ->
                    val dTag = eventDTag.removePrefix(DELETE_TOMBSTONE_DTAG_PREFIX).trim()
                    if (dTag.isBlank()) return@collect
                    val isDeleted = runCatching {
                        val obj = json.parseToJsonElement(decrypted).jsonObject
                        obj["deleted"]?.jsonPrimitive?.booleanOrNull != false
                    }.getOrDefault(true)
                    if (isDeleted) deleted.add(dTag)
                }
            }
        } catch (_: Exception) {
            // If tombstone sync fails, we still continue with normal 37004 sync.
        }
        return deleted
    }
}
