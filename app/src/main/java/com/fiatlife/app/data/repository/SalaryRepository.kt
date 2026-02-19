package com.fiatlife.app.data.repository

import com.fiatlife.app.data.local.dao.SalaryDao
import com.fiatlife.app.data.local.entity.SalaryEntity
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.domain.model.SalaryConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

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

    suspend fun saveSalaryConfig(config: SalaryConfig, privateKey: ByteArray?) {
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

        if (privateKey != null) {
            try {
                nostrClient.publishEncryptedAppData(NOSTR_D_TAG, jsonStr, privateKey)
            } catch (_: Exception) {
                // Sync will retry later
            }
        }
    }

    suspend fun syncFromNostr(pubkey: String, privateKey: ByteArray) {
        try {
            nostrClient.subscribeToAppData(pubkey, NOSTR_D_TAG).collect { decrypted ->
                val config = json.decodeFromString<SalaryConfig>(decrypted)
                salaryDao.upsert(
                    SalaryEntity(
                        id = config.id,
                        jsonData = decrypted,
                        updatedAt = config.updatedAt
                    )
                )
            }
        } catch (_: Exception) {
            // Will retry on next sync
        }
    }
}
