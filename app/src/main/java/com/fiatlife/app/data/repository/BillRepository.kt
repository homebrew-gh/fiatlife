package com.fiatlife.app.data.repository

import com.fiatlife.app.data.blossom.BlossomClient
import com.fiatlife.app.data.local.dao.BillDao
import com.fiatlife.app.data.local.entity.BillEntity
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.domain.model.Bill
import com.fiatlife.app.domain.model.BillCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillRepository @Inject constructor(
    private val billDao: BillDao,
    private val nostrClient: NostrClient,
    private val blossomClient: BlossomClient,
    private val json: Json
) {
    companion object {
        private const val NOSTR_D_TAG_PREFIX = "fiatlife/bill/"
    }

    fun getAllBills(): Flow<List<Bill>> {
        return billDao.getAll().map { entities ->
            entities.map { json.decodeFromString<Bill>(it.jsonData) }
        }
    }

    fun getBillsByCategory(category: BillCategory): Flow<List<Bill>> {
        return billDao.getByCategory(category.name).map { entities ->
            entities.map { json.decodeFromString<Bill>(it.jsonData) }
        }
    }

    suspend fun saveBill(bill: Bill) {
        val billWithId = if (bill.id.isEmpty()) {
            bill.copy(
                id = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        } else {
            bill.copy(updatedAt = System.currentTimeMillis())
        }

        val jsonStr = json.encodeToString(Bill.serializer(), billWithId)

        billDao.upsert(
            BillEntity(
                id = billWithId.id,
                jsonData = jsonStr,
                category = billWithId.category.name,
                updatedAt = billWithId.updatedAt
            )
        )

        if (nostrClient.hasSigner) {
            try {
                nostrClient.publishEncryptedAppData(
                    "$NOSTR_D_TAG_PREFIX${billWithId.id}",
                    jsonStr
                )
            } catch (_: Exception) { }
        }
    }

    suspend fun deleteBill(bill: Bill) {
        billDao.delete(
            BillEntity(
                id = bill.id,
                jsonData = "",
                category = bill.category.name
            )
        )
    }

    suspend fun uploadAttachment(
        data: ByteArray,
        contentType: String,
        filename: String
    ): Result<String> {
        return blossomClient.uploadBlob(data, contentType, filename).map { it.sha256 }
    }

    suspend fun downloadAttachment(sha256: String): Result<ByteArray> {
        return blossomClient.getBlob(sha256)
    }

    suspend fun syncFromNostr() {
        if (!nostrClient.hasSigner) return
        try {
            nostrClient.subscribeToAppData(dTagPrefix = NOSTR_D_TAG_PREFIX).collect { (_, decrypted) ->
                try {
                    val bill = json.decodeFromString<Bill>(decrypted)
                    if (bill.id.isNotEmpty()) {
                        billDao.upsert(
                            BillEntity(
                                id = bill.id,
                                jsonData = decrypted,
                                category = bill.category.name,
                                updatedAt = bill.updatedAt
                            )
                        )
                    }
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }
}
