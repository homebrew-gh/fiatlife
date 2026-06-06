package com.fiatlife.app.data.local.dao

import androidx.room.*
import com.fiatlife.app.data.local.entity.CreditAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CreditAccountDao {
    @Query("SELECT * FROM credit_accounts ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<CreditAccountEntity>>

    @Query("SELECT * FROM credit_accounts ORDER BY updatedAt DESC")
    suspend fun getAllSnapshot(): List<CreditAccountEntity>

    @Query("SELECT * FROM credit_accounts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CreditAccountEntity?

    @Query("SELECT * FROM credit_accounts WHERE id = :id LIMIT 1")
    fun getByIdAsFlow(id: String): Flow<CreditAccountEntity?>

    @Upsert
    suspend fun upsert(entity: CreditAccountEntity)

    @Upsert
    suspend fun upsertAll(entities: List<CreditAccountEntity>)

    @Transaction
    suspend fun applySyncBatch(upserts: List<CreditAccountEntity>, deleteIds: List<String>) {
        deleteIds.forEach { deleteById(it) }
        if (upserts.isNotEmpty()) upsertAll(upserts)
    }

    @Delete
    suspend fun delete(entity: CreditAccountEntity)

    @Query("DELETE FROM credit_accounts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM credit_accounts")
    suspend fun deleteAll()
}
