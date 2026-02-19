package com.fiatlife.app.data.local.dao

import androidx.room.*
import com.fiatlife.app.data.local.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getById(id: String): GoalEntity?

    @Upsert
    suspend fun upsert(entity: GoalEntity)

    @Delete
    suspend fun delete(entity: GoalEntity)

    @Query("DELETE FROM goals")
    suspend fun deleteAll()
}
