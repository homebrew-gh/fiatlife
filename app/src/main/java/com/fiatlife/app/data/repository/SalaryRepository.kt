package com.fiatlife.app.data.repository

import android.util.Log
import com.fiatlife.app.data.local.dao.SalaryDao
import com.fiatlife.app.data.local.entity.SalaryEntity
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.domain.model.SalaryConfig
import com.fiatlife.app.domain.model.mergeSalaryConfigPreserveLogs
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

private const val TAG = "SalaryRepo"

@Singleton
class SalaryRepository @Inject constructor(
    private val salaryDao: SalaryDao,
    private val nostrClient: NostrClient,
    private val json: Json
) {
    companion object {
        private const val NOSTR_D_TAG = "fiatlife/salary"
    }

    /** False until the first salary pull from the relay finishes this app session. */
    private val _relayPublishReady = MutableStateFlow(false)
    val relayPublishReady: StateFlow<Boolean> = _relayPublishReady.asStateFlow()

    private var initialSalarySyncCompleted = false
    private var relayPublishPending = false
    /** Relay copy was missing paycheck logs that exist locally — republish after sync. */
    private var relaySalaryRepairPending = false

    /** Call before the first salary sync when a signer is available. */
    fun prepareForInitialRelaySync() {
        if (nostrClient.hasSigner && !initialSalarySyncCompleted) {
            _relayPublishReady.value = false
        }
    }

    fun observeHasData(): Flow<Boolean> =
        salaryDao.observeCount().map { it > 0 }

    fun getSalaryConfig(): Flow<SalaryConfig?> {
        return salaryDao.getLatestConfig().map { entity ->
            entity?.let { json.decodeFromString<SalaryConfig>(it.jsonData) }
        }.decodeOnBackground()
    }

    suspend fun saveSalaryConfig(config: SalaryConfig) {
        val merged = mergeBeforeSave(config)
        val configWithId = if (merged.id.isEmpty()) {
            merged.copy(id = UUID.randomUUID().toString(), updatedAt = System.currentTimeMillis())
        } else {
            merged.copy(updatedAt = System.currentTimeMillis())
        }

        val jsonStr = json.encodeToString(SalaryConfig.serializer(), configWithId)

        salaryDao.upsert(
            SalaryEntity(
                id = configWithId.id,
                jsonData = jsonStr,
                updatedAt = configWithId.updatedAt
            )
        )

        if (!nostrClient.hasSigner) {
            Log.d(TAG, "No signer, salary saved locally only")
            return
        }
        if (!_relayPublishReady.value) {
            relayPublishPending = true
            Log.d(TAG, "Relay publish deferred until initial salary sync completes")
            return
        }
        publishToRelay(jsonStr)
    }

    suspend fun syncFromNostr() {
        if (!nostrClient.hasSigner) return
        val isInitial = !initialSalarySyncCompleted
        if (isInitial) _relayPublishReady.value = false
        relaySalaryRepairPending = false
        try {
            withTimeout(30_000) {
                var latest: SalaryConfig? = null
                var latestJson: String? = null
                var count = 0
                nostrClient.subscribeToAppData(dTag = NOSTR_D_TAG).collect { (_, decrypted) ->
                    val config = runCatching {
                        json.decodeFromString<SalaryConfig>(decrypted)
                    }.getOrNull() ?: return@collect
                    count++
                    if (latest == null || config.updatedAt >= latest!!.updatedAt) {
                        latest = config
                        latestJson = decrypted
                    }
                }
                val resolved = latest
                val resolvedJson = latestJson
                val localStored = decodeConfig(salaryDao.getLatestConfigOnce())
                val localHasLogs = localStored?.paycheckLog?.isNotEmpty() == true
                val relayHasLogs = resolved?.paycheckLog?.isNotEmpty() == true

                if (resolved != null && resolvedJson != null) {
                    // Only let the relay copy overwrite local when it is at least as
                    // recent; otherwise newer unpublished local edits would be reverted.
                    if (localStored == null || resolved.updatedAt >= localStored.updatedAt) {
                        // Preserve logs/history if a stale-but-newer relay copy is missing them.
                        val mergedRemote = mergeSalaryConfigPreserveLogs(resolved, localStored)
                        val jsonToStore = if (mergedRemote == resolved) {
                            resolvedJson
                        } else {
                            json.encodeToString(SalaryConfig.serializer(), mergedRemote)
                        }
                        salaryDao.deleteExcept(mergedRemote.id)
                        salaryDao.upsert(
                            SalaryEntity(
                                id = mergedRemote.id,
                                jsonData = jsonToStore,
                                updatedAt = mergedRemote.updatedAt
                            )
                        )
                        Log.d(TAG, "Synced $count salary event(s); applied relay copy ${mergedRemote.id.take(8)}")
                    } else {
                        Log.d(
                            TAG,
                            "Synced $count salary event(s); kept newer local copy " +
                                "(local=${localStored.updatedAt} > relay=${resolved.updatedAt})"
                        )
                    }
                } else {
                    Log.d(TAG, "Synced $count salary event(s); no usable relay copy")
                }

                // Self-heal: if this device has paycheck logs the relay doesn't
                // reflect — because an earlier publish was never delivered (the
                // outbox is in-memory only), or the relay copy was overwritten
                // without logs — push the local copy back up so other clients
                // (e.g. the web app) can see it.
                if (localHasLogs && !relayHasLogs) {
                    markRelaySalaryRepairPending()
                }
            }
            if (_relayPublishReady.value) {
                flushPendingRelaySalaryRepair()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}")
        } finally {
            if (isInitial) {
                initialSalarySyncCompleted = true
                _relayPublishReady.value = true
                flushPendingRelayPublish()
                flushPendingRelaySalaryRepair()
            }
        }
    }

    private fun markRelaySalaryRepairPending() {
        relaySalaryRepairPending = true
    }

    /** Push local paycheck logs back to the relay when a newer relay copy omitted them. */
    private suspend fun flushPendingRelaySalaryRepair() {
        if (!relaySalaryRepairPending) return
        relaySalaryRepairPending = false
        if (!nostrClient.hasSigner) return
        val stored = salaryDao.getLatestConfigOnce() ?: return
        val config = decodeConfig(stored) ?: return
        if (config.paycheckLog.isEmpty()) return
        val repaired = config.copy(updatedAt = System.currentTimeMillis())
        val jsonStr = json.encodeToString(SalaryConfig.serializer(), repaired)
        salaryDao.upsert(
            SalaryEntity(
                id = repaired.id,
                jsonData = jsonStr,
                updatedAt = repaired.updatedAt
            )
        )
        publishToRelay(jsonStr)
    }

    private suspend fun mergeBeforeSave(incoming: SalaryConfig): SalaryConfig {
        val stored = decodeConfig(salaryDao.getLatestConfigOnce())
        return mergeSalaryConfigPreserveLogs(incoming, stored)
    }

    private suspend fun flushPendingRelayPublish() {
        if (!relayPublishPending) return
        relayPublishPending = false
        if (!nostrClient.hasSigner) return
        // Publish the reconciled local state (post-sync winner), not a stale snapshot,
        // so a deferred edit can't clobber a newer relay copy that just arrived.
        val current = salaryDao.getLatestConfigOnce() ?: return
        publishToRelay(current.jsonData)
    }

    private suspend fun publishToRelay(jsonStr: String) {
        try {
            val published = nostrClient.publishEncryptedAppData(NOSTR_D_TAG, jsonStr)
            Log.d(TAG, "Published salary to relay: $published")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish salary: ${e.message}")
        }
    }

    private fun decodeConfig(entity: SalaryEntity?): SalaryConfig? {
        if (entity == null) return null
        return runCatching {
            json.decodeFromString<SalaryConfig>(entity.jsonData)
        }.getOrNull()
    }
}
