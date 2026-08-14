package com.fiatlife.app.data.repository

import android.util.Log
import com.fiatlife.app.data.local.dao.BillerDao
import com.fiatlife.app.data.local.entity.BillerEntity
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.domain.model.Biller
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BillerRepo"

@Singleton
class BillerRepository @Inject constructor(
    private val dao: BillerDao,
    private val nostrClient: NostrClient,
    private val json: Json
) {
    companion object {
        private const val NOSTR_D_TAG_PREFIX = "fiatlife/biller/"
    }

    fun getAllBillers(): Flow<List<Biller>> =
        dao.getAll().map { rows ->
            rows.map {
                Biller(
                    id = it.id,
                    name = it.name,
                    normalizedName = it.normalizedName,
                    linkedBillId = it.linkedBillId,
                    isArchived = it.isArchived,
                    updatedAt = it.updatedAt
                )
            }
        }.decodeOnBackground()

    suspend fun getById(id: String): Biller? =
        dao.getById(id)?.toDomain()

    suspend fun getByNormalizedName(normalizedName: String): Biller? =
        dao.getByNormalizedName(normalizedName)?.toDomain()

    suspend fun getOrCreateByName(name: String): Biller {
        val normalized = normalize(name)
        val existing = dao.getByNormalizedName(normalized)
        if (existing != null) return existing.toDomain()
        val now = System.currentTimeMillis()
        val created = Biller(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            normalizedName = normalized,
            linkedBillId = null,
            updatedAt = now
        )
        saveBiller(created)
        return created
    }

    suspend fun saveBiller(biller: Biller): Biller {
        val withId = if (biller.id.isBlank()) biller.copy(id = UUID.randomUUID().toString()) else biller
        val normalizedName = withId.normalizedName.ifBlank { normalize(withId.name) }
        val normalized = withId.copy(
            name = withId.name.trim(),
            normalizedName = normalizedName,
                isArchived = withId.isArchived,
            updatedAt = System.currentTimeMillis()
        )
        dao.upsert(normalized.toEntity())
        publish(normalized)
        return normalized
    }

    suspend fun linkToBill(billerId: String, billId: String) {
        val current = dao.getById(billerId) ?: return
        if (current.linkedBillId == billId) return
        saveBiller(current.toDomain().copy(linkedBillId = billId))
    }

    suspend fun unlinkIfLinkedToBill(billerId: String, billId: String) {
        val current = dao.getById(billerId) ?: return
        if (current.linkedBillId != billId) return
        saveBiller(current.toDomain().copy(linkedBillId = null))
    }

    suspend fun deleteById(id: String) {
        dao.deleteById(id)
        if (!nostrClient.hasSigner) return
        val dTag = "$NOSTR_D_TAG_PREFIX$id"
        runCatching {
            nostrClient.publishEncryptedAppData(dTag, """{"deleted":true}""")
        }.onFailure {
            Log.w(TAG, "Failed to publish biller tombstone $id: ${it.message}")
        }
    }

    suspend fun syncFromNostr() {
        if (!nostrClient.hasSigner) return
        try {
            withTimeout(30_000) {
                val localBefore = dao.getAll().first().associateBy { it.id }
                val deleteIds = mutableListOf<String>()
                val upsertsById = mutableMapOf<String, BillerEntity>()
                nostrClient.subscribeToAppData(dTagPrefix = NOSTR_D_TAG_PREFIX).collect { (dTag, decrypted) ->
                    try {
                        val obj = json.parseToJsonElement(decrypted).jsonObject
                        if (obj["deleted"]?.jsonPrimitive?.booleanOrNull == true) {
                            val id = dTag.removePrefix(NOSTR_D_TAG_PREFIX)
                            deleteIds.add(id)
                            upsertsById.remove(id)
                            return@collect
                        }
                        val parsed = json.decodeFromString(Biller.serializer(), decrypted)
                        if (parsed.id.isBlank()) return@collect
                        upsertsById[parsed.id] = parsed.toEntity()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse biller event: ${e.message}")
                    }
                }
                dao.applySyncBatch(upsertsById.values.toList(), deleteIds)
                Log.d(TAG, "Synced ${upsertsById.size} biller(s) from relay; deleted ${deleteIds.size}")
                republishStrandedBillers(localBefore, upsertsById, deleteIds)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Biller sync failed: ${e.message}")
        }
    }

    /**
     * Self-heal: push local billers the relay doesn't reflect back up so other
     * clients (e.g. the web app) can see them. Covers billers whose original
     * publish was never delivered (the outbox is in-memory only) and billers
     * whose local copy is newer than the relay's.
     */
    private suspend fun republishStrandedBillers(
        localBefore: Map<String, BillerEntity>,
        relayById: Map<String, BillerEntity>,
        deleteIds: List<String>
    ) {
        val deleted = deleteIds.toSet()
        var pushed = 0
        for ((id, local) in localBefore) {
            if (id in deleted) continue
            val relay = relayById[id]
            if (relay != null && local.updatedAt <= relay.updatedAt) continue
            if (relay != null) dao.upsert(local)
            val payload = json.encodeToString(Biller.serializer(), local.toDomain())
            runCatching {
                nostrClient.publishEncryptedAppData("$NOSTR_D_TAG_PREFIX$id", payload)
            }.onSuccess { pushed++ }
                .onFailure { Log.w(TAG, "Self-heal republish failed for biller ${id.take(8)}…: ${it.message}") }
        }
        if (pushed > 0) Log.d(TAG, "Self-heal re-published $pushed biller(s) the relay was missing")
    }

    private suspend fun publish(biller: Biller) {
        if (!nostrClient.hasSigner) return
        val dTag = "$NOSTR_D_TAG_PREFIX${biller.id}"
        runCatching {
            nostrClient.publishEncryptedAppData(
                dTag,
                json.encodeToString(Biller.serializer(), biller)
            )
        }.onFailure {
            Log.w(TAG, "Failed to publish biller ${biller.id.take(8)}…: ${it.message}")
        }
    }

    fun normalize(name: String): String =
        name.trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    private fun BillerEntity.toDomain(): Biller =
        Biller(
            id = id,
            name = name,
            normalizedName = normalizedName,
            linkedBillId = linkedBillId,
            isArchived = isArchived,
            updatedAt = updatedAt
        )

    private fun Biller.toEntity(): BillerEntity =
        BillerEntity(
            id = id,
            name = name,
            normalizedName = normalizedName,
            linkedBillId = linkedBillId,
            isArchived = isArchived,
            updatedAt = updatedAt
        )
}
