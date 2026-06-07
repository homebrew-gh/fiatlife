package com.fiatlife.app.data.local.dao

import androidx.room.*
import com.fiatlife.app.data.local.entity.CypherLogSubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CypherLogSubscriptionDao {
    @Query("SELECT * FROM cypherlog_subscriptions ORDER BY createdAt DESC")
    fun getAll(): Flow<List<CypherLogSubscriptionEntity>>

    @Query("SELECT COUNT(*) FROM cypherlog_subscriptions")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM cypherlog_subscriptions ORDER BY createdAt DESC")
    suspend fun getAllSnapshot(): List<CypherLogSubscriptionEntity>

    @Query("SELECT * FROM cypherlog_subscriptions WHERE dTag = :dTag LIMIT 1")
    suspend fun getByDTag(dTag: String): CypherLogSubscriptionEntity?

    @Query("SELECT * FROM cypherlog_subscriptions WHERE dTag = :dTag LIMIT 1")
    fun getByDTagAsFlow(dTag: String): Flow<CypherLogSubscriptionEntity?>

    @Query("SELECT * FROM cypherlog_subscriptions WHERE dTag = :dTagA OR dTag = :dTagB LIMIT 1")
    fun getByEitherDTagAsFlow(dTagA: String, dTagB: String): Flow<CypherLogSubscriptionEntity?>

    @Upsert
    suspend fun upsert(entity: CypherLogSubscriptionEntity)

    @Upsert
    suspend fun upsertAll(entities: List<CypherLogSubscriptionEntity>)

    @Transaction
    suspend fun applySyncBatch(upserts: List<CypherLogSubscriptionEntity>, deleteDTags: List<String>) {
        deleteDTags.forEach { deleteByDTag(it) }
        if (upserts.isNotEmpty()) upsertAll(upserts)
    }

    @Delete
    suspend fun delete(entity: CypherLogSubscriptionEntity)

    @Query("DELETE FROM cypherlog_subscriptions WHERE dTag = :dTag")
    suspend fun deleteByDTag(dTag: String)

    @Query("DELETE FROM cypherlog_subscriptions")
    suspend fun deleteAll()
}
