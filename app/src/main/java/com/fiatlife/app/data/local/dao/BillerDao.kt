package com.fiatlife.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.fiatlife.app.data.local.entity.BillerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BillerDao {
    @Query("SELECT * FROM billers ORDER BY name ASC")
    fun getAll(): Flow<List<BillerEntity>>

    @Query("SELECT * FROM billers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): BillerEntity?

    @Query("SELECT * FROM billers WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun getByNormalizedName(normalizedName: String): BillerEntity?

    @Upsert
    suspend fun upsert(entity: BillerEntity)

    @Delete
    suspend fun delete(entity: BillerEntity)

    @Query("DELETE FROM billers WHERE id = :id")
    suspend fun deleteById(id: String)
}
