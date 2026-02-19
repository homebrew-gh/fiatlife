package com.fiatlife.app.data.local.dao

import androidx.room.*
import com.fiatlife.app.data.local.entity.SalaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SalaryDao {
    @Query("SELECT * FROM salary_configs ORDER BY updatedAt DESC LIMIT 1")
    fun getLatestConfig(): Flow<SalaryEntity?>

    @Query("SELECT * FROM salary_configs WHERE id = :id")
    suspend fun getById(id: String): SalaryEntity?

    @Upsert
    suspend fun upsert(entity: SalaryEntity)

    @Delete
    suspend fun delete(entity: SalaryEntity)

    @Query("DELETE FROM salary_configs")
    suspend fun deleteAll()
}
