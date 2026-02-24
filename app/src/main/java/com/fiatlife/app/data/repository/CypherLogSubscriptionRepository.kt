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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CypherLogSubRepo"

/** CypherLog 37004 tag keys we map to Bill; all others are preserved for round-trip */
private val MAPPED_TAG_KEYS = setOf(
    "d", "name", "cost", "currency", "billing_frequency", "subscription_type",
    "company_name", "company_id", "notes", "alt", "due_day",
    "renewal_date", "next_due_date", "due_date",
    "initial_purchase_date", "purchase_date", "anchor_date",
    "interval_unit", "interval_count", "timezone"
)

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
        else -> BillSubcategory.OTHER_SUBSCRIPTION
    }
}

private fun parseIsoDateToMillis(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    val parts = value.trim().split("-")
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
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

@Singleton
class CypherLogSubscriptionRepository @Inject constructor(
    private val dao: CypherLogSubscriptionDao,
    private val nostrClient: NostrClient,
    private val json: Json
) {
    data class SaveResult(
        val success: Boolean,
        val reason: String = ""
    )

    /** Temporary debug export for local 37004 cache inspection in Settings screen. */
    suspend fun exportDebugRows(limit: Int = 20): String {
        val rows = dao.getRecent(limit)
        if (rows.isEmpty()) return "No cached 37004 subscription rows found."
        fun truncate(s: String, max: Int = 2500): String =
            if (s.length <= max) s else s.take(max) + "… [truncated]"

        return buildString {
            appendLine("CypherLog 37004 cache debug export")
            appendLine("rows=${rows.size}")
            rows.forEachIndexed { idx, row ->
                appendLine()
                appendLine("[$idx] dTag=${row.dTag}")
                appendLine("eventId=${row.eventId}")
                appendLine("createdAt=${row.createdAt}")
                appendLine("tagsJson=${truncate(row.tagsJson)}")
                appendLine("contentDecryptedJson=${row.contentDecryptedJson?.let { truncate(it) } ?: "<null>"}")
            }
        }
    }

    fun getAllAsBills(): Flow<List<BillWithSource>> {
        return dao.getAll().map { entities ->
            entities.mapNotNull { entity -> entityToBillWithSource(entity) }
        }
    }

    fun getByDTag(dTag: String): Flow<BillWithSource?> {
        return dao.getByDTagAsFlow(dTag).map { entity ->
            entity?.let { entityToBillWithSource(it) }
        }
    }

    suspend fun upsertFromEvent(event: NostrEvent) {
        val dTag = event.tags.firstOrNull { it.size >= 2 && it[0] == "d" }?.getOrNull(1) ?: return
        val tagsJson = buildJsonArray {
            event.tags.forEach { tag ->
                add(buildJsonArray { tag.forEach { add(JsonPrimitive(it)) } })
            }
        }.toString()
        var contentDecryptedJson: String? = null
        if (event.content.isNotBlank()) {
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
                    val isJson = try {
                        json.parseToJsonElement(raw)
                        true
                    } catch (_: Exception) {
                        false
                    }
                    if (isJson) {
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
        dao.upsert(
            CypherLogSubscriptionEntity(
                dTag = dTag,
                eventId = event.id,
                tagsJson = tagsJson,
                createdAt = event.created_at,
                contentDecryptedJson = contentDecryptedJson
            )
        )
        Log.d(TAG, "Upserted 37004 d=$dTag")
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
        val dTag = bill.id.ifEmpty { UUID.randomUUID().toString() }
        val tags = billTo37004Tags(bill, preservedTags, dTag)
        val status = nostrClient.publishReplaceable37004Detailed(dTag, tags)
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
        if (nostrClient.hasSigner) {
            try {
                nostrClient.publishDeletion(NostrEvent.KIND_CYPHERLOG_SUBSCRIPTION, dTag)
                Log.d(TAG, "Published NIP-09 deletion for 37004 d=$dTag")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish 37004 deletion: ${e.message}")
            }
        }
        dao.deleteByDTag(dTag)
    }

    suspend fun syncFromRelay() {
        if (!nostrClient.hasSigner) return
        try {
            withTimeout(30_000) {
                var count = 0
                nostrClient.subscribeToKind37004().collect { event ->
                    upsertFromEvent(event)
                    count++
                }
                Log.d(TAG, "Synced $count 37004 subscription(s) from relay")
            }
        } catch (e: Exception) {
            Log.e(TAG, "37004 sync failed: ${e.message}")
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

    /** Parse CypherLog encrypted content JSON (same logical fields as tags) and build Bill; preserved from tags. */
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
            frequency = billingFrequencyToBillFrequency(str("billing_frequency", "billingFrequency"))
            notes = str("notes") ?: ""
            companyName = str("company_name", "companyName") ?: ""
            dueDay = str("due_day")?.toIntOrNull()?.coerceIn(1, 31)
                ?: preserved?.get("due_day")?.firstOrNull()?.toIntOrNull()?.coerceIn(1, 31) ?: 1
            renewalDateMillis = parseIsoDateToMillis(
                str("renewal_date", "next_due_date", "due_date")
                    ?: tagMap["renewal_date"]?.firstOrNull()
                    ?: tagMap["next_due_date"]?.firstOrNull()
                    ?: tagMap["due_date"]?.firstOrNull()
            )
            initialPurchaseDateMillis = parseIsoDateToMillis(
                str("initial_purchase_date", "purchase_date", "anchor_date")
                    ?: tagMap["initial_purchase_date"]?.firstOrNull()
                    ?: tagMap["purchase_date"]?.firstOrNull()
                    ?: tagMap["anchor_date"]?.firstOrNull()
            )
            recurrenceUnit = intervalUnitFromCypherLog(
                str("interval_unit") ?: tagMap["interval_unit"]?.firstOrNull()
            )
            recurrenceIntervalCount = (str("interval_count") ?: tagMap["interval_count"]?.firstOrNull())
                ?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            recurrenceTimezone = str("timezone") ?: tagMap["timezone"]?.firstOrNull()
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
            id = dTag,
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
            accountName = companyName,
            notes = notes,
            updatedAt = 0L
        )
        return bill to preserved
    }

    private fun tags37004ToBill(dTag: String, tags: List<List<String>>): Pair<Bill, Map<String, List<String>>?> {
        val tagMap = tagsToMap(tags)
        fun first(key: String): String? = tagMap[key]?.firstOrNull()

        var name = first("name") ?: ""
        if (name.isBlank()) name = nameFromAltTag(tagMap)
        val cost = first("cost")?.toDoubleOrNull() ?: first("amount")?.toDoubleOrNull() ?: 0.0
        val frequency = billingFrequencyToBillFrequency(first("billing_frequency"))
        val notes = first("notes") ?: ""
        val companyName = first("company_name") ?: ""
        val dueDay = first("due_day")?.toIntOrNull()?.coerceIn(1, 31) ?: 1
        val subcategory = subscriptionTypeToSubcategory(first("subscription_type"))
        val renewalDateMillis = parseIsoDateToMillis(first("renewal_date") ?: first("next_due_date") ?: first("due_date"))
        val initialPurchaseDateMillis = parseIsoDateToMillis(first("initial_purchase_date") ?: first("purchase_date") ?: first("anchor_date"))
        val recurrenceUnit = intervalUnitFromCypherLog(first("interval_unit"))
        val recurrenceIntervalCount = first("interval_count")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val recurrenceTimezone = first("timezone")

        val preserved = tagMap.filter { (k, _) -> k !in MAPPED_TAG_KEYS }
            .mapValues { (_, v) -> v.toList() }
            .ifEmpty { null }

        val bill = Bill(
            id = dTag,
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
            accountName = companyName,
            notes = notes,
            updatedAt = 0L
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
        else -> "Other"
    }

    private fun billTo37004Tags(
        bill: Bill,
        preservedTags: Map<String, List<String>>?,
        dTag: String
    ): List<List<String>> {
        val list = mutableListOf<List<String>>()
        list.add(listOf("d", dTag))
        list.add(listOf("alt", "Subscription: ${bill.name}"))
        list.add(listOf("name", bill.name))
        list.add(listOf("subscription_type", billSubcategoryToSubscriptionType(bill.effectiveSubcategory)))
        list.add(listOf("cost", bill.amount.toString()))
        list.add(listOf("billing_frequency", billFrequencyToCypherLog(bill.frequency)))
        list.add(listOf("due_day", bill.dueDay.toString()))
        bill.renewalDateMillis?.let { list.add(listOf("renewal_date", formatIsoDate(it))) }
        bill.initialPurchaseDateMillis?.let { list.add(listOf("initial_purchase_date", formatIsoDate(it))) }
        intervalUnitToCypherLog(bill.recurrenceUnit)?.let { list.add(listOf("interval_unit", it)) }
        if (bill.recurrenceIntervalCount > 1) list.add(listOf("interval_count", bill.recurrenceIntervalCount.toString()))
        if (!bill.recurrenceTimezone.isNullOrBlank()) list.add(listOf("timezone", bill.recurrenceTimezone))
        if (bill.notes.isNotBlank()) list.add(listOf("notes", bill.notes))
        if (bill.accountName.isNotBlank()) list.add(listOf("company_name", bill.accountName))
        preservedTags?.forEach { (key, values) ->
            if (key != "d") values.forEach { list.add(listOf(key, it)) }
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
}
