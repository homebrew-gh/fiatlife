package com.fiatlife.app.data.repository

import android.util.Log
import com.fiatlife.app.data.local.dao.BudgetDao
import com.fiatlife.app.data.local.entity.BudgetEntity
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.domain.model.BudgetConfig
import com.fiatlife.app.domain.model.mergeBudgetConfigPreserveId
import com.fiatlife.app.domain.model.rollBudgetPeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BudgetRepo"

@Singleton
class BudgetRepository @Inject constructor(
    private val budgetDao: BudgetDao,
    private val nostrClient: NostrClient,
    private val json: Json
) {
    companion object {
        private const val NOSTR_D_TAG = "fiatlife/budget"
    }

    /** False until the first budget pull from the relay finishes this app session. */
    private val _relayPublishReady = MutableStateFlow(false)
    val relayPublishReady: StateFlow<Boolean> = _relayPublishReady.asStateFlow()

    private var initialBudgetSyncCompleted = false
    private var relayPublishPending = false

    /** Call before the first budget sync when a signer is available. */
    fun prepareForInitialRelaySync() {
        if (nostrClient.hasSigner && !initialBudgetSyncCompleted) {
            _relayPublishReady.value = false
        }
    }

    fun observeHasData(): Flow<Boolean> =
        budgetDao.observeCount().map { it > 0 }

    fun getBudgetConfig(): Flow<BudgetConfig?> {
        return budgetDao.getLatestConfig().map { entity ->
            entity?.let { rollBudgetPeriod(json.decodeFromString<BudgetConfig>(it.jsonData)) }
        }.decodeOnBackground()
    }

    suspend fun saveBudgetConfig(config: BudgetConfig) {
        val merged = mergeBeforeSave(config)
        val configWithId = if (merged.id.isEmpty()) {
            merged.copy(id = UUID.randomUUID().toString(), updatedAt = System.currentTimeMillis())
        } else {
            merged.copy(updatedAt = System.currentTimeMillis())
        }

        val jsonStr = json.encodeToString(BudgetConfig.serializer(), configWithId)

        budgetDao.upsert(
            BudgetEntity(
                id = configWithId.id,
                jsonData = jsonStr,
                updatedAt = configWithId.updatedAt
            )
        )

        if (!nostrClient.hasSigner) {
            Log.d(TAG, "No signer, budget saved locally only")
            return
        }
        if (!_relayPublishReady.value) {
            relayPublishPending = true
            Log.d(TAG, "Relay publish deferred until initial budget sync completes")
            return
        }
        publishToRelay(jsonStr)
    }

    suspend fun syncFromNostr() {
        if (!nostrClient.hasSigner) return
        val isInitial = !initialBudgetSyncCompleted
        if (isInitial) _relayPublishReady.value = false
        try {
            withTimeout(30_000) {
                var latest: BudgetConfig? = null
                var latestJson: String? = null
                var count = 0
                nostrClient.subscribeToAppData(dTag = NOSTR_D_TAG).collect { (_, decrypted) ->
                    val config = runCatching {
                        json.decodeFromString<BudgetConfig>(decrypted)
                    }.getOrNull() ?: return@collect
                    count++
                    if (latest == null || config.updatedAt >= latest!!.updatedAt) {
                        latest = config
                        latestJson = decrypted
                    }
                }
                val resolved = latest
                val resolvedJson = latestJson
                if (resolved != null && resolvedJson != null) {
                    val localStored = decodeConfig(budgetDao.getLatestConfigOnce())
                    // Only let the relay copy overwrite local when it is at least as
                    // recent; otherwise newer unpublished local edits would be reverted.
                    if (localStored == null || resolved.updatedAt >= localStored.updatedAt) {
                        val mergedRemote = mergeBudgetConfigPreserveId(resolved, localStored)
                        val jsonToStore = if (mergedRemote == resolved) {
                            resolvedJson
                        } else {
                            json.encodeToString(BudgetConfig.serializer(), mergedRemote)
                        }
                        budgetDao.deleteExcept(mergedRemote.id)
                        budgetDao.upsert(
                            BudgetEntity(
                                id = mergedRemote.id,
                                jsonData = jsonToStore,
                                updatedAt = mergedRemote.updatedAt
                            )
                        )
                        Log.d(TAG, "Synced $count budget event(s); applied relay copy ${mergedRemote.id.take(8)}")
                    } else {
                        Log.d(
                            TAG,
                            "Synced $count budget event(s); kept newer local copy " +
                                "(local=${localStored.updatedAt} > relay=${resolved.updatedAt})"
                        )
                    }
                } else {
                    Log.d(TAG, "Synced $count budget event(s); no usable relay copy")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}")
        } finally {
            if (isInitial) {
                initialBudgetSyncCompleted = true
                _relayPublishReady.value = true
                flushPendingRelayPublish()
            }
        }
    }

    private suspend fun mergeBeforeSave(incoming: BudgetConfig): BudgetConfig {
        val stored = decodeConfig(budgetDao.getLatestConfigOnce())
        return mergeBudgetConfigPreserveId(incoming, stored)
    }

    private suspend fun flushPendingRelayPublish() {
        if (!relayPublishPending) return
        relayPublishPending = false
        if (!nostrClient.hasSigner) return
        val current = budgetDao.getLatestConfigOnce() ?: return
        publishToRelay(current.jsonData)
    }

    private suspend fun publishToRelay(jsonStr: String) {
        try {
            val published = nostrClient.publishEncryptedAppData(NOSTR_D_TAG, jsonStr)
            Log.d(TAG, "Published budget to relay: $published")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish budget: ${e.message}")
        }
    }

    private fun decodeConfig(entity: BudgetEntity?): BudgetConfig? {
        if (entity == null) return null
        return runCatching {
            json.decodeFromString<BudgetConfig>(entity.jsonData)
        }.getOrNull()
    }
}
