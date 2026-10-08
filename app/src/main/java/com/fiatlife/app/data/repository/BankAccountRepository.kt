package com.fiatlife.app.data.repository

import android.util.Log
import com.fiatlife.app.data.local.dao.BankAccountDao
import com.fiatlife.app.data.local.entity.BankAccountEntity
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.data.nostr.NostrEvent
import com.fiatlife.app.domain.model.BankAccount
import kotlinx.coroutines.flow.collect
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

private const val TAG = "BankAccountRepo"

@Singleton
class BankAccountRepository @Inject constructor(
    private val dao: BankAccountDao,
    private val nostrClient: NostrClient,
    private val json: Json
) {
    companion object {
        private const val NOSTR_D_TAG_PREFIX = "fiatlife/settings/bank/"
    }

    private fun BankAccountEntity.toModel(): BankAccount =
        jsonData.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString(BankAccount.serializer(), it) }.getOrNull() }
            ?.copy(id = id, name = name)
            ?: BankAccount(id = id, name = name)

    private fun BankAccount.toEntity(): BankAccountEntity = BankAccountEntity(
        id = id,
        name = name,
        jsonData = json.encodeToString(BankAccount.serializer(), this)
    )

    fun getAllBankAccounts(): Flow<List<BankAccount>> {
        return dao.getAll().map { entities ->
            entities.map { it.toModel() }
        }.decodeOnBackground()
    }

    suspend fun saveBankAccount(account: BankAccount): BankAccount {
        val withId = if (account.id.isEmpty()) {
            account.copy(id = UUID.randomUUID().toString())
        } else account
        val normalized = withId.copy(
            name = withId.name.trim(),
            updatedAt = System.currentTimeMillis()
        )
        dao.upsert(normalized.toEntity())
        if (nostrClient.hasSigner) {
            val dTag = "$NOSTR_D_TAG_PREFIX${normalized.id}"
            val payload = json.encodeToString(BankAccount.serializer(), normalized)
            try {
                nostrClient.publishEncryptedAppData(dTag, payload)
                Log.d(TAG, "Published bank account ${normalized.id.take(8)}…")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish bank account: ${e.message}")
            }
        }
        return normalized
    }

    suspend fun deleteBankAccount(account: BankAccount) {
        dao.delete(account.toEntity())
        if (nostrClient.hasSigner) {
            val dTag = "$NOSTR_D_TAG_PREFIX${account.id}"
            try {
                nostrClient.publishEncryptedAppData(dTag, """{"deleted":true}""")
                nostrClient.publishDeletion(NostrEvent.KIND_APP_SPECIFIC_DATA, dTag)
                Log.d(TAG, "Published delete for bank account ${account.id.take(8)}…")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish bank account deletion: ${e.message}")
            }
        }
    }

    suspend fun syncFromNostr() {
        if (!nostrClient.hasSigner) return
        try {
            withTimeout(90_000) {
                val localBefore = dao.getAll().first().associateBy { it.id }
                val deleteIds = mutableListOf<String>()
                val upsertsById = mutableMapOf<String, BankAccountEntity>()
                nostrClient.subscribeToAppData(dTagPrefix = NOSTR_D_TAG_PREFIX).collect { (dTag, decrypted) ->
                    try {
                        val obj = json.parseToJsonElement(decrypted).jsonObject
                        if (obj["deleted"]?.jsonPrimitive?.booleanOrNull == true) {
                            val id = dTag.removePrefix(NOSTR_D_TAG_PREFIX)
                            deleteIds.add(id)
                            upsertsById.remove(id)
                            return@collect
                        }
                        val account = json.decodeFromString<BankAccount>(decrypted)
                        if (account.id.isNotBlank()) {
                            upsertsById[account.id] = account.toEntity()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse bank account event: ${e.message}")
                    }
                }
                dao.applySyncBatch(upsertsById.values.toList(), deleteIds)
                Log.d(TAG, "Synced ${upsertsById.size} bank account(s) from relay; deleted ${deleteIds.size}")
                republishStrandedAccounts(localBefore, upsertsById, deleteIds)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bank account sync failed: ${e.message}")
        }
    }

    /**
     * Self-heal: push local bank accounts the relay doesn't have back up so other
     * clients (e.g. the web app) can see them — covers accounts whose original
     * publish was never delivered (the outbox is in-memory only).
     */
    private suspend fun republishStrandedAccounts(
        localBefore: Map<String, BankAccountEntity>,
        relayById: Map<String, BankAccountEntity>,
        deleteIds: List<String>
    ) {
        val deleted = deleteIds.toSet()
        var pushed = 0
        for ((id, local) in localBefore) {
            if (id in deleted || relayById.containsKey(id)) continue
            val payload = json.encodeToString(BankAccount.serializer(), local.toModel())
            runCatching {
                nostrClient.publishEncryptedAppData("$NOSTR_D_TAG_PREFIX$id", payload)
            }.onSuccess { pushed++ }
                .onFailure { Log.w(TAG, "Self-heal republish failed for bank account ${id.take(8)}…: ${it.message}") }
        }
        if (pushed > 0) Log.d(TAG, "Self-heal re-published $pushed bank account(s) the relay was missing")
    }
}
