package com.fiatlife.app.data.repository

import android.util.Log
import com.fiatlife.app.data.blossom.BlossomClient
import com.fiatlife.app.data.local.dao.BillDao
import com.fiatlife.app.data.local.entity.BillEntity
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.data.nostr.NostrEvent
import com.fiatlife.app.domain.model.Bill
import com.fiatlife.app.domain.model.BillCategory
import com.fiatlife.app.domain.model.BillPayment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BillRepo"

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
        }.decodeOnBackground()
    }

    fun getBillsByCategory(category: BillCategory): Flow<List<Bill>> {
        return billDao.getByCategory(category.name).map { entities ->
            entities.map { json.decodeFromString<Bill>(it.jsonData) }
        }.decodeOnBackground()
    }

    fun getBillById(id: String): Flow<Bill?> {
        return billDao.getByIdAsFlow(id).map { entity ->
            entity?.let { json.decodeFromString<Bill>(it.jsonData) }
        }.decodeOnBackground()
    }

    suspend fun getBillByLinkedBillerId(billerId: String): Bill? {
        return billDao.getByLinkedBillerId(billerId)?.let { json.decodeFromString<Bill>(it.jsonData) }
    }

    suspend fun saveBill(bill: Bill): Bill {
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
                category = billWithId.effectiveSubcategory.name,
                linkedBillerId = billWithId.linkedBillerId,
                updatedAt = billWithId.updatedAt
            )
        )

        if (nostrClient.hasSigner) {
            try {
                val published = nostrClient.publishEncryptedAppData(
                    "$NOSTR_D_TAG_PREFIX${billWithId.id}",
                    jsonStr
                )
                Log.d(TAG, "Published bill ${billWithId.id.take(8)}… to relay: $published")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish bill: ${e.message}")
            }
        }
        return billWithId
    }

    suspend fun deleteBill(bill: Bill) {
        billDao.delete(
            BillEntity(
                id = bill.id,
                jsonData = "",
                category = bill.effectiveSubcategory.name,
                linkedBillerId = bill.linkedBillerId
            )
        )

        if (nostrClient.hasSigner) {
            val dTag = "$NOSTR_D_TAG_PREFIX${bill.id}"
            try {
                nostrClient.publishEncryptedAppData(dTag, """{"deleted":true}""")
                Log.d(TAG, "Published delete tombstone for bill ${bill.id.take(8)}…")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish bill tombstone: ${e.message}")
            }
            try {
                nostrClient.publishDeletion(NostrEvent.KIND_APP_SPECIFIC_DATA, dTag)
                Log.d(TAG, "Published NIP-09 deletion for bill ${bill.id.take(8)}…")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish bill NIP-09 deletion: ${e.message}")
            }
        }
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
            withTimeout(30_000) {
                val deleteIds = mutableListOf<String>()
                val upsertsById = mutableMapOf<String, BillEntity>()
                nostrClient.subscribeToAppData(dTagPrefix = NOSTR_D_TAG_PREFIX).collect { (dTag, decrypted) ->
                    try {
                        val obj = json.parseToJsonElement(decrypted).jsonObject
                        if (obj["deleted"]?.jsonPrimitive?.booleanOrNull == true) {
                            val billId = dTag.removePrefix(NOSTR_D_TAG_PREFIX)
                            deleteIds.add(billId)
                            upsertsById.remove(billId)
                            return@collect
                        }
                        val bill = json.decodeFromString<Bill>(decrypted)
                        if (bill.id.isNotEmpty()) {
                            upsertsById[bill.id] = BillEntity(
                                id = bill.id,
                                jsonData = decrypted,
                                category = bill.effectiveSubcategory.name,
                                linkedBillerId = bill.linkedBillerId,
                                updatedAt = bill.updatedAt
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse bill event: ${e.message}")
                    }
                }
                billDao.applySyncBatch(upsertsById.values.toList(), deleteIds)
                Log.d(TAG, "Synced ${upsertsById.size} bill(s) from relay; deleted ${deleteIds.size}")
            }
            backfillLegacyCreditLoanPayments()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}")
        }
    }

    /**
     * One-time compatibility backfill for legacy credit/loan bills that have
     * `isPaid + lastPaidDate` but no matching paymentHistory row.
     */
    suspend fun backfillLegacyCreditLoanPayments(): Int {
        val rows = billDao.getAll().first()
        if (rows.isEmpty()) return 0
        val toUpsert = mutableListOf<BillEntity>()
        val now = System.currentTimeMillis()
        rows.forEach { entity ->
            val bill = runCatching { json.decodeFromString<Bill>(entity.jsonData) }.getOrNull() ?: return@forEach
            if (!bill.isCreditOrLoan() || !bill.isPaid || bill.lastPaidDate == null) return@forEach
            val paidAt = bill.lastPaidDate
            val hasMatchingPayment = bill.paymentHistory.any { payment ->
                kotlin.math.abs(payment.date - paidAt) <= 60_000L
            }
            if (hasMatchingPayment) return@forEach

            val inferredAmount = bill.amount.takeIf { it > 0.0 } ?: bill.effectiveAmountDue()
            val migrated = bill.copy(
                paymentHistory = bill.paymentHistory + BillPayment(date = paidAt, amount = inferredAmount),
                updatedAt = now
            )
            val migratedJson = json.encodeToString(Bill.serializer(), migrated)
            toUpsert.add(
                BillEntity(
                    id = migrated.id,
                    jsonData = migratedJson,
                    category = migrated.effectiveSubcategory.name,
                    linkedBillerId = migrated.linkedBillerId,
                    updatedAt = migrated.updatedAt
                )
            )
        }
        if (toUpsert.isNotEmpty()) {
            billDao.upsertAll(toUpsert)
            Log.d(TAG, "Backfilled ${toUpsert.size} legacy credit/loan payment row(s)")
        }
        return toUpsert.size
    }
}
