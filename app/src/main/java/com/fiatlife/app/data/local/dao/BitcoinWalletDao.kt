package com.fiatlife.app.data.local.dao

import androidx.room.*
import com.fiatlife.app.data.local.entity.BitcoinWalletEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BitcoinWalletDao {
    @Query("SELECT * FROM bitcoin_wallets ORDER BY name COLLATE NOCASE ASC")
    fun getAll(): Flow<List<BitcoinWalletEntity>>

    @Upsert
    suspend fun upsert(entity: BitcoinWalletEntity)

    @Upsert
    suspend fun upsertAll(entities: List<BitcoinWalletEntity>)

    @Transaction
    suspend fun applySyncBatch(upserts: List<BitcoinWalletEntity>, deleteIds: List<String>) {
        deleteIds.forEach { deleteById(it) }
        if (upserts.isNotEmpty()) upsertAll(upserts)
    }

    @Query("DELETE FROM bitcoin_wallets WHERE id = :id")
    suspend fun deleteById(id: String)
}
