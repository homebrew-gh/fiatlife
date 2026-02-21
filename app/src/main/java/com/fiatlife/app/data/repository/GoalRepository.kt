package com.fiatlife.app.data.repository

import android.util.Log
import com.fiatlife.app.data.local.dao.GoalDao
import com.fiatlife.app.data.local.entity.GoalEntity
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.data.nostr.NostrEvent
import com.fiatlife.app.domain.model.FinancialGoal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GoalRepo"

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

    suspend fun saveGoal(goal: FinancialGoal) {
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

        if (nostrClient.hasSigner) {
            try {
                val published = nostrClient.publishEncryptedAppData(
                    "$NOSTR_D_TAG_PREFIX${goalWithId.id}",
                    jsonStr
                )
                Log.d(TAG, "Published goal ${goalWithId.id.take(8)}… to relay: $published")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish goal: ${e.message}")
            }
        }
    }

    suspend fun updateGoalProgress(
        goalId: String,
        newAmount: Double
    ) {
        val entity = goalDao.getById(goalId) ?: return
        val goal = json.decodeFromString<FinancialGoal>(entity.jsonData)
        val updated = goal.copy(currentAmount = newAmount)
        saveGoal(updated)
    }

    suspend fun deleteGoal(goal: FinancialGoal) {
        goalDao.delete(
            GoalEntity(
                id = goal.id,
                jsonData = "",
                category = goal.category.name
            )
        )

        if (nostrClient.hasSigner) {
            val dTag = "$NOSTR_D_TAG_PREFIX${goal.id}"
            try {
                nostrClient.publishEncryptedAppData(dTag, """{"deleted":true}""")
                Log.d(TAG, "Published delete tombstone for goal ${goal.id.take(8)}…")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish goal tombstone: ${e.message}")
            }
            try {
                nostrClient.publishDeletion(NostrEvent.KIND_APP_SPECIFIC_DATA, dTag)
                Log.d(TAG, "Published NIP-09 deletion for goal ${goal.id.take(8)}…")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish goal NIP-09 deletion: ${e.message}")
            }
        }
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
                            val goalId = dTag.removePrefix(NOSTR_D_TAG_PREFIX)
                            goalDao.deleteById(goalId)
                            Log.d(TAG, "Deleted tombstoned goal $goalId")
                            return@collect
                        }
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
                            count++
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse goal event: ${e.message}")
                    }
                }
                Log.d(TAG, "Synced $count goal(s) from relay")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}")
        }
    }
}
