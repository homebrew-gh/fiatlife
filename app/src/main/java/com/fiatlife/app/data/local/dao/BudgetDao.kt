package com.fiatlife.app.data.local.dao

import androidx.room.*
import com.fiatlife.app.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budget_configs ORDER BY updatedAt DESC LIMIT 1")
    fun getLatestConfig(): Flow<BudgetEntity?>

    @Query("SELECT * FROM budget_configs ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestConfigOnce(): BudgetEntity?

    @Query("DELETE FROM budget_configs WHERE id != :keepId")
    suspend fun deleteExcept(keepId: String)

    @Query("SELECT COUNT(*) FROM budget_configs")
    fun observeCount(): Flow<Int>

    @Upsert
    suspend fun upsert(entity: BudgetEntity)

    @Delete
    suspend fun delete(entity: BudgetEntity)

    @Query("DELETE FROM budget_configs")
    suspend fun deleteAll()
}
