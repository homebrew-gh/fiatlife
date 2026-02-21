package com.fiatlife.app.data.repository

import android.util.Log
import com.fiatlife.app.data.local.dao.SalaryDao
import com.fiatlife.app.data.local.entity.SalaryEntity
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.domain.model.SalaryConfig
import kotlinx.coroutines.flow.Flow
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

    fun getSalaryConfig(): Flow<SalaryConfig?> {
        return salaryDao.getLatestConfig().map { entity ->
            entity?.let { json.decodeFromString<SalaryConfig>(it.jsonData) }
        }
    }

    suspend fun saveSalaryConfig(config: SalaryConfig) {
        val configWithId = if (config.id.isEmpty()) {
            config.copy(id = UUID.randomUUID().toString(), updatedAt = System.currentTimeMillis())
        } else {
            config.copy(updatedAt = System.currentTimeMillis())
        }

        val jsonStr = json.encodeToString(SalaryConfig.serializer(), configWithId)

        salaryDao.upsert(
            SalaryEntity(
                id = configWithId.id,
                jsonData = jsonStr,
                updatedAt = configWithId.updatedAt
            )
        )

        if (nostrClient.hasSigner) {
            try {
                val published = nostrClient.publishEncryptedAppData(NOSTR_D_TAG, jsonStr)
                Log.d(TAG, "Published salary to relay: $published")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish salary: ${e.message}")
            }
        } else {
            Log.d(TAG, "No signer, salary saved locally only")
        }
    }

    suspend fun syncFromNostr() {
        if (!nostrClient.hasSigner) return
        try {
            withTimeout(30_000) {
                var count = 0
                nostrClient.subscribeToAppData(dTag = NOSTR_D_TAG).collect { (_, decrypted) ->
                    val config = json.decodeFromString<SalaryConfig>(decrypted)
                    salaryDao.upsert(
                        SalaryEntity(
                            id = config.id,
                            jsonData = decrypted,
                            updatedAt = config.updatedAt
                        )
                    )
                    count++
                }
                Log.d(TAG, "Synced $count salary config(s) from relay")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}")
        }
    }
}
