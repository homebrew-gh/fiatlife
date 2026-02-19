package com.fiatlife.app.data.repository

import com.fiatlife.app.data.local.dao.GoalDao
import com.fiatlife.app.data.local.entity.GoalEntity
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.domain.model.FinancialGoal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalRepository @Inject constructor(
    private val goalDao: GoalDao,
    private val nostrClient: NostrClient,
    private val json: Json
) {
    companion object {
        private const val NOSTR_D_TAG_PREFIX = "fiatlife/goal/"
    }

    fun getAllGoals(): Flow<List<FinancialGoal>> {
        return goalDao.getAll().map { entities ->
            entities.map { json.decodeFromString<FinancialGoal>(it.jsonData) }
        }
    }

    suspend fun saveGoal(goal: FinancialGoal, privateKey: ByteArray?) {
        val goalWithId = if (goal.id.isEmpty()) {
            goal.copy(
                id = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        } else {
            goal.copy(updatedAt = System.currentTimeMillis())
        }

        val jsonStr = json.encodeToString(FinancialGoal.serializer(), goalWithId)

        goalDao.upsert(
            GoalEntity(
                id = goalWithId.id,
                jsonData = jsonStr,
                category = goalWithId.category.name,
                updatedAt = goalWithId.updatedAt
            )
        )

        if (privateKey != null) {
            try {
                nostrClient.publishEncryptedAppData(
                    "$NOSTR_D_TAG_PREFIX${goalWithId.id}",
                    jsonStr,
                    privateKey
                )
            } catch (_: Exception) { }
        }
    }

    suspend fun updateGoalProgress(
        goalId: String,
        newAmount: Double,
        privateKey: ByteArray?
    ) {
        val entity = goalDao.getById(goalId) ?: return
        val goal = json.decodeFromString<FinancialGoal>(entity.jsonData)
        val updated = goal.copy(currentAmount = newAmount)
        saveGoal(updated, privateKey)
    }

    suspend fun deleteGoal(goal: FinancialGoal) {
        goalDao.delete(
            GoalEntity(
                id = goal.id,
                jsonData = "",
                category = goal.category.name
            )
        )
    }

    suspend fun syncFromNostr(pubkey: String, privateKey: ByteArray) {
        try {
            nostrClient.subscribeToAppData(pubkey, null).collect { decrypted ->
                try {
                    val goal = json.decodeFromString<FinancialGoal>(decrypted)
                    if (goal.id.isNotEmpty()) {
                        goalDao.upsert(
                            GoalEntity(
                                id = goal.id,
                                jsonData = decrypted,
                                category = goal.category.name,
                                updatedAt = goal.updatedAt
                            )
                        )
                    }
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }
}
