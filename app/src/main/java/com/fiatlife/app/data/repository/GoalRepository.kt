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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
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

    fun observeHasData(): Flow<Boolean> =
        goalDao.observeCount().map { it > 0 }

    fun getAllGoals(): Flow<List<FinancialGoal>> {
        return goalDao.getAll().map { entities ->
            entities.mapNotNull { entity ->
                decodeGoalSafely(entity.jsonData, source = "db:${entity.id}")
            }
        }.decodeOnBackground()
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
        val goal = decodeGoalSafely(entity.jsonData, source = "db:$goalId") ?: return
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
                val deleteIds = mutableListOf<String>()
                val upsertsById = mutableMapOf<String, GoalEntity>()
                nostrClient.subscribeToAppData(dTagPrefix = NOSTR_D_TAG_PREFIX).collect { (dTag, decrypted) ->
                    try {
                        val obj = json.parseToJsonElement(decrypted).jsonObject
                        if (obj["deleted"]?.jsonPrimitive?.booleanOrNull == true) {
                            val goalId = dTag.removePrefix(NOSTR_D_TAG_PREFIX)
                            deleteIds.add(goalId)
                            upsertsById.remove(goalId)
                            return@collect
                        }
                        val goal = decodeGoalSafely(decrypted, source = "relay:$dTag") ?: return@collect
                        if (goal.id.isNotEmpty()) {
                            val canonical = json.encodeToString(FinancialGoal.serializer(), goal)
                            upsertsById[goal.id] = GoalEntity(
                                id = goal.id,
                                jsonData = canonical,
                                category = goal.category.name,
                                updatedAt = goal.updatedAt
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse goal event: ${e.message}")
                    }
                }
                goalDao.applySyncBatch(upsertsById.values.toList(), deleteIds)
                Log.d(TAG, "Synced ${upsertsById.size} goal(s) from relay; deleted ${deleteIds.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}")
        }
    }

    private fun decodeGoalSafely(raw: String, source: String): FinancialGoal? {
        val direct = runCatching { json.decodeFromString<FinancialGoal>(raw) }.getOrNull()
        if (direct != null) return direct

        // Compatibility path for legacy/unknown enum category values.
        val repaired = runCatching {
            val obj = json.parseToJsonElement(raw).jsonObject
            val categoryRaw = obj["category"]?.jsonPrimitive?.contentOrNull
            val mappedCategory = mapLegacyGoalCategory(categoryRaw) ?: "OTHER"
            val mutable = obj.toMutableMap()
            mutable["category"] = JsonPrimitive(mappedCategory)
            json.decodeFromJsonElement(FinancialGoal.serializer(), JsonObject(mutable))
        }.getOrNull()

        if (repaired == null) {
            Log.w(TAG, "Dropping malformed goal payload from $source")
        }
        return repaired
    }

    /** Maps legacy or unknown category strings to a valid GoalCategory enum name, or null to use OTHER. */
    private fun mapLegacyGoalCategory(raw: String?): String? {
        val value = raw?.trim()?.uppercase() ?: return null
        return when (value) {
            "HOME_RENOVATION" -> "HOME_IMPROVEMENT"
            "CAR", "AUTO", "CAR_GOAL", "VEHICLE" -> "CAR_PURCHASE"
            "EMERGENCY_FUND", "RETIREMENT", "HOUSE_DOWN_PAYMENT", "CAR_PURCHASE",
            "VACATION", "WEDDING", "EDUCATION", "DEBT_PAYOFF", "GENERAL_SAVINGS",
            "INVESTMENT", "HOME_IMPROVEMENT", "MEDICAL", "OTHER" -> value
            else -> null // unknown → caller will use OTHER
        }
    }
}
