package com.fiatlife.app.data.local.dao

import androidx.room.*
import com.fiatlife.app.data.local.entity.BillEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE category = :category ORDER BY updatedAt DESC")
    fun getByCategory(category: String): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun getById(id: String): BillEntity?

    @Query("SELECT * FROM bills WHERE id = :id")
    fun getByIdAsFlow(id: String): Flow<BillEntity?>

    @Query("SELECT * FROM bills WHERE linkedBillerId = :billerId LIMIT 1")
    suspend fun getByLinkedBillerId(billerId: String): BillEntity?

    @Upsert
    suspend fun upsert(entity: BillEntity)

    @Upsert
    suspend fun upsertAll(entities: List<BillEntity>)

    @Transaction
    suspend fun applySyncBatch(upserts: List<BillEntity>, deleteIds: List<String>) {
        deleteIds.forEach { deleteById(it) }
        if (upserts.isNotEmpty()) upsertAll(upserts)
    }

    @Delete
    suspend fun delete(entity: BillEntity)

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM bills")
    suspend fun deleteAll()
}
