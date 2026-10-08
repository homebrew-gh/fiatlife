package com.fiatlife.app.data.repository

import android.util.Log
import com.fiatlife.app.data.local.dao.BitcoinWalletDao
import com.fiatlife.app.data.local.entity.BitcoinWalletEntity
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.data.nostr.NostrEvent
import com.fiatlife.app.domain.model.BitcoinWallet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BitcoinWalletRepo"

@Singleton
class BitcoinWalletRepository @Inject constructor(
    private val dao: BitcoinWalletDao,
    private val nostrClient: NostrClient,
    private val json: Json
) {
    companion object {
        /** Same prefix as web `BITCOIN_WALLET_D_TAG_PREFIX`. */
        private const val NOSTR_D_TAG_PREFIX = "fiatlife/btc/"
    }

    private fun BitcoinWalletEntity.toModel(): BitcoinWallet? =
        runCatching { json.decodeFromString(BitcoinWallet.serializer(), jsonData) }.getOrNull()
            ?.copy(id = id)

    private fun BitcoinWallet.toEntity(): BitcoinWalletEntity = BitcoinWalletEntity(
        id = id,
        name = name,
        jsonData = json.encodeToString(BitcoinWallet.serializer(), this)
    )

    fun getAllWallets(): Flow<List<BitcoinWallet>> =
        dao.getAll().map { entities -> entities.mapNotNull { it.toModel() } }.decodeOnBackground()

    suspend fun saveWallet(wallet: BitcoinWallet): BitcoinWallet {
        val now = System.currentTimeMillis()
        val normalized = wallet.copy(
            id = wallet.id.ifEmpty { UUID.randomUUID().toString() },
            name = wallet.name.trim(),
            notes = wallet.notes.trim(),
            createdAt = wallet.createdAt.takeIf { it > 0 } ?: now,
            updatedAt = now
        )
        dao.upsert(normalized.toEntity())
        if (nostrClient.hasSigner) {
            try {
                nostrClient.publishEncryptedAppData(
                    "$NOSTR_D_TAG_PREFIX${normalized.id}",
                    json.encodeToString(BitcoinWallet.serializer(), normalized)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish bitcoin wallet: ${e.message}")
            }
        }
        return normalized
    }

    suspend fun deleteWallet(wallet: BitcoinWallet) {
        dao.deleteById(wallet.id)
        if (nostrClient.hasSigner) {
            val dTag = "$NOSTR_D_TAG_PREFIX${wallet.id}"
            try {
                nostrClient.publishEncryptedAppData(dTag, """{"deleted":true}""")
                nostrClient.publishDeletion(NostrEvent.KIND_APP_SPECIFIC_DATA, dTag)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish bitcoin wallet deletion: ${e.message}")
            }
        }
    }

    suspend fun syncFromNostr() {
        if (!nostrClient.hasSigner) return
        try {
            withTimeout(90_000) {
                val localBefore = dao.getAll().first().associateBy { it.id }
                val deleteIds = mutableListOf<String>()
                val upsertsById = mutableMapOf<String, BitcoinWalletEntity>()
                nostrClient.subscribeToAppData(dTagPrefix = NOSTR_D_TAG_PREFIX).collect { (dTag, decrypted) ->
                    val id = dTag.removePrefix(NOSTR_D_TAG_PREFIX)
                    try {
                        val obj = json.parseToJsonElement(decrypted).jsonObject
                        if (obj["deleted"]?.jsonPrimitive?.booleanOrNull == true) {
                            deleteIds.add(id)
                            upsertsById.remove(id)
                            return@collect
                        }
                        val wallet = json.decodeFromString(BitcoinWallet.serializer(), decrypted)
                            .let { if (it.id.isBlank()) it.copy(id = id) else it }
                        if (wallet.id.isNotBlank() && wallet.name.isNotBlank()) {
                            upsertsById[wallet.id] = wallet.toEntity()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse bitcoin wallet event: ${e.message}")
                    }
                }
                dao.applySyncBatch(upsertsById.values.toList(), deleteIds)
                Log.d(TAG, "Synced ${upsertsById.size} bitcoin wallet(s); deleted ${deleteIds.size}")
                republishStranded(localBefore, upsertsById, deleteIds)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bitcoin wallet sync failed: ${e.message}")
        }
    }

    /** Push local wallets the relay doesn't have (original publish never delivered). */
    private suspend fun republishStranded(
        localBefore: Map<String, BitcoinWalletEntity>,
        relayById: Map<String, BitcoinWalletEntity>,
        deleteIds: List<String>
    ) {
        val deleted = deleteIds.toSet()
        for ((id, local) in localBefore) {
            if (id in deleted || relayById.containsKey(id)) continue
            runCatching {
                nostrClient.publishEncryptedAppData("$NOSTR_D_TAG_PREFIX$id", local.jsonData)
            }.onFailure { Log.w(TAG, "Self-heal republish failed for wallet ${id.take(8)}…: ${it.message}") }
        }
    }
}
