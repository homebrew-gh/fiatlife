package com.fiatlife.app.data.repository

import android.util.Log
import com.fiatlife.app.data.local.dao.BillerDao
import com.fiatlife.app.data.local.entity.BillerEntity
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.domain.model.Biller
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
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
                    updatedAt = it.updatedAt
                )
            }
        }

    suspend fun getById(id: String): Biller? =
        dao.getById(id)?.toDomain()

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

    suspend fun syncFromNostr() {
        if (!nostrClient.hasSigner) return
        try {
            withTimeout(30_000) {
                var count = 0
                nostrClient.subscribeToAppData(dTagPrefix = NOSTR_D_TAG_PREFIX).collect { (dTag, decrypted) ->
                    try {
                        val obj = json.parseToJsonElement(decrypted).jsonObject
                        if (obj["deleted"]?.jsonPrimitive?.booleanOrNull == true) {
                            val id = dTag.removePrefix(NOSTR_D_TAG_PREFIX)
                            dao.deleteById(id)
                            return@collect
                        }
                        val parsed = json.decodeFromString(Biller.serializer(), decrypted)
                        if (parsed.id.isBlank()) return@collect
                        dao.upsert(parsed.toEntity())
                        count++
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse biller event: ${e.message}")
                    }
                }
                Log.d(TAG, "Synced $count biller(s) from relay")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Biller sync failed: ${e.message}")
        }
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
            updatedAt = updatedAt
        )

    private fun Biller.toEntity(): BillerEntity =
        BillerEntity(
            id = id,
            name = name,
            normalizedName = normalizedName,
            linkedBillId = linkedBillId,
            updatedAt = updatedAt
        )
}
