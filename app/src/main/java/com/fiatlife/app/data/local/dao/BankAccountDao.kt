package com.fiatlife.app.data.local.dao

import androidx.room.*
import com.fiatlife.app.data.local.entity.BankAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BankAccountDao {
    @Query("SELECT * FROM bank_accounts ORDER BY name ASC")
    fun getAll(): Flow<List<BankAccountEntity>>

    @Query("SELECT * FROM bank_accounts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): BankAccountEntity?

    @Upsert
    suspend fun upsert(entity: BankAccountEntity)

    @Upsert
    suspend fun upsertAll(entities: List<BankAccountEntity>)

    @Transaction
    suspend fun applySyncBatch(upserts: List<BankAccountEntity>, deleteIds: List<String>) {
        deleteIds.forEach { deleteById(it) }
        if (upserts.isNotEmpty()) upsertAll(upserts)
    }

    @Delete
    suspend fun delete(entity: BankAccountEntity)

    @Query("DELETE FROM bank_accounts WHERE id = :id")
    suspend fun deleteById(id: String)
}
