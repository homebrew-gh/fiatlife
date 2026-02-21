package com.fiatlife.app.data.local.dao

import androidx.room.*
import com.fiatlife.app.data.local.entity.CypherLogSubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CypherLogSubscriptionDao {
    @Query("SELECT * FROM cypherlog_subscriptions ORDER BY createdAt DESC")
    fun getAll(): Flow<List<CypherLogSubscriptionEntity>>

    @Query("SELECT * FROM cypherlog_subscriptions WHERE dTag = :dTag LIMIT 1")
    suspend fun getByDTag(dTag: String): CypherLogSubscriptionEntity?

    @Query("SELECT * FROM cypherlog_subscriptions WHERE dTag = :dTag LIMIT 1")
    fun getByDTagAsFlow(dTag: String): Flow<CypherLogSubscriptionEntity?>

    @Upsert
    suspend fun upsert(entity: CypherLogSubscriptionEntity)

    @Delete
    suspend fun delete(entity: CypherLogSubscriptionEntity)

    @Query("DELETE FROM cypherlog_subscriptions WHERE dTag = :dTag")
    suspend fun deleteByDTag(dTag: String)

    @Query("DELETE FROM cypherlog_subscriptions")
    suspend fun deleteAll()
}
