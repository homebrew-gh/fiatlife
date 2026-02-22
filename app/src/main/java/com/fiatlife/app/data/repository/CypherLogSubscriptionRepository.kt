package com.fiatlife.app.data.repository

import android.util.Log
import com.fiatlife.app.data.local.dao.CypherLogSubscriptionDao
import com.fiatlife.app.data.local.entity.CypherLogSubscriptionEntity
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.data.nostr.NostrEvent
import com.fiatlife.app.domain.model.Bill
import com.fiatlife.app.domain.model.BillCategory
import com.fiatlife.app.domain.model.BillFrequency
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
    "company_name", "company_id", "notes", "alt", "due_day"
)

/** When name is empty, try to derive from CypherLog "alt" tag (e.g. "Subscription: Netflix"). */
private fun nameFromAltTag(tagMap: Map<String, List<String>>): String {
    val alt = tagMap["alt"]?.firstOrNull() ?: return ""
    val lower = alt.lowercase()
    if (lower.contains("encrypted") && lower.contains("subscription data")) return ""
    return alt.removePrefix("Subscription:").removePrefix("subscription:").trim()
}

@Singleton
class CypherLogSubscriptionRepository @Inject constructor(
    private val dao: CypherLogSubscriptionDao,
    private val nostrClient: NostrClient,
    private val json: Json
) {
    fun getAllAsBills(): Flow<List<BillWithSource>> {
        return dao.getAll().map { entities ->
            entities.map { entity -> entityToBillWithSource(entity) }
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
                contentDecryptedJson = signer.nip44Decrypt(event.content, event.pubkey)
                    ?: signer.nip44Decrypt(event.content, signer.pubkeyHex)
                if (contentDecryptedJson == null) {
                    Log.w(TAG, "Failed to decrypt 37004 content for d=$dTag (author=${event.pubkey.take(8)}…)")
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
        val dTag = bill.id.ifEmpty { UUID.randomUUID().toString() }
        val tags = billTo37004Tags(bill, preservedTags, dTag)
        val sent = nostrClient.publishReplaceable37004(dTag, tags)
        if (sent) {
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
        }
        return sent
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

    private fun entityToBillWithSource(entity: CypherLogSubscriptionEntity): BillWithSource {
        val tags = try {
            json.parseToJsonElement(entity.tagsJson).jsonArray.map { arr ->
                arr.jsonArray.map { it.jsonPrimitive.content }
            }
        } catch (_: Exception) {
            emptyList()
        }
        val (bill, preserved) = if (entity.contentDecryptedJson != null) {
            content37004ToBill(entity.dTag, entity.contentDecryptedJson!!, tags)
        } else {
            tags37004ToBill(entity.dTag, tags)
        }
        return BillWithSource(bill = bill, source = BillSource.CYPHERLOG, preservedTags = preserved)
    }

    /** Parse CypherLog encrypted content JSON (same logical fields as tags) and build Bill; preserved from tags. */
    private fun content37004ToBill(dTag: String, contentJson: String, tags: List<List<String>>): Pair<Bill, Map<String, List<String>>?> {
        val tagMap = mutableMapOf<String, MutableList<String>>()
        tags.forEach { pair ->
            if (pair.size >= 2) {
                tagMap.getOrPut(pair[0]) { mutableListOf() }.add(pair[1])
            }
        }
        val preserved = tagMap.filter { (k, _) -> k !in MAPPED_TAG_KEYS }
            .mapValues { (_, v) -> v.toList() }
            .ifEmpty { null }

        val name: String
        val cost: Double
        val frequency: BillFrequency
        val notes: String
        val companyName: String
        val dueDay: Int
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
            if (nameFromContent.isBlank()) {
                nameFromContent = nameFromAltTag(tagMap)
            }
            name = nameFromContent
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse 37004 content for d=$dTag: ${e.message}; content snippet: ${contentJson.take(200)}…")
            return tags37004ToBill(dTag, tags)
        }

        val bill = Bill(
            id = dTag,
            name = name.ifBlank { "Subscription" },
            amount = cost,
            category = BillCategory.OTHER,
            subcategory = BillSubcategory.OTHER_SUBSCRIPTION,
            frequency = frequency,
            dueDay = dueDay,
            accountName = companyName,
            notes = notes,
            updatedAt = 0L
        )
        return bill to preserved
    }

    private fun tags37004ToBill(dTag: String, tags: List<List<String>>): Pair<Bill, Map<String, List<String>>?> {
        val tagMap = mutableMapOf<String, MutableList<String>>()
        tags.forEach { pair ->
            if (pair.size >= 2) {
                tagMap.getOrPut(pair[0]) { mutableListOf() }.add(pair[1])
            }
        }
        fun first(key: String): String? = tagMap[key]?.firstOrNull()

        var name = first("name") ?: ""
        if (name.isBlank()) name = nameFromAltTag(tagMap)
        val cost = first("cost")?.toDoubleOrNull() ?: first("amount")?.toDoubleOrNull() ?: 0.0
        val frequency = billingFrequencyToBillFrequency(first("billing_frequency"))
        val notes = first("notes") ?: ""
        val companyName = first("company_name") ?: ""
        val dueDay = first("due_day")?.toIntOrNull()?.coerceIn(1, 31) ?: 1

        val preserved = tagMap.filter { (k, _) -> k !in MAPPED_TAG_KEYS }
            .mapValues { (_, v) -> v.toList() }
            .ifEmpty { null }

        val bill = Bill(
            id = dTag,
            name = name.ifBlank { "Subscription" },
            amount = cost,
            category = BillCategory.OTHER,
            subcategory = BillSubcategory.OTHER_SUBSCRIPTION,
            frequency = frequency,
            dueDay = dueDay,
            accountName = companyName,
            notes = notes,
            updatedAt = 0L
        )
        return bill to preserved
    }

    private fun billTo37004Tags(
        bill: Bill,
        preservedTags: Map<String, List<String>>?,
        dTag: String
    ): List<List<String>> {
        val list = mutableListOf<List<String>>()
        list.add(listOf("d", dTag))
        list.add(listOf("name", bill.name))
        list.add(listOf("cost", bill.amount.toString()))
        list.add(listOf("billing_frequency", billFrequencyToCypherLog(bill.frequency)))
        list.add(listOf("due_day", bill.dueDay.toString()))
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
