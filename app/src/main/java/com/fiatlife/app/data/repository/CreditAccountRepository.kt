package com.fiatlife.app.data.repository

import android.util.Log
import com.fiatlife.app.data.blossom.BlossomClient
import com.fiatlife.app.data.local.dao.CreditAccountDao
import com.fiatlife.app.data.local.entity.CreditAccountEntity
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.data.nostr.NostrEvent
import com.fiatlife.app.domain.model.Bill
import com.fiatlife.app.domain.model.BillFrequency
import com.fiatlife.app.domain.model.BillSubcategory
import com.fiatlife.app.domain.model.CreditAccount
import com.fiatlife.app.domain.model.CreditAccountType
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

private const val TAG = "CreditAccountRepo"

@Singleton
class CreditAccountRepository @Inject constructor(
    private val creditAccountDao: CreditAccountDao,
    private val billRepository: BillRepository,
    private val nostrClient: NostrClient,
    private val blossomClient: BlossomClient,
    private val json: Json
) {
    companion object {
        private const val NOSTR_D_TAG_PREFIX = "fiatlife/credit/"
    }

    fun getAllCreditAccounts(): Flow<List<CreditAccount>> {
        return creditAccountDao.getAll().map { entities ->
            entities.map { json.decodeFromString<CreditAccount>(it.jsonData) }
        }
    }

    fun getCreditAccountById(id: String): Flow<CreditAccount?> {
        return creditAccountDao.getByIdAsFlow(id).map { entity ->
            entity?.let { json.decodeFromString<CreditAccount>(it.jsonData) }
        }
    }

    suspend fun saveCreditAccount(account: CreditAccount): CreditAccount {
        val withId = if (account.id.isEmpty()) {
            account.copy(
                id = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        } else {
            account.copy(updatedAt = System.currentTimeMillis())
        }
        val jsonStr = json.encodeToString(CreditAccount.serializer(), withId)
        creditAccountDao.upsert(
            CreditAccountEntity(
                id = withId.id,
                jsonData = jsonStr,
                type = withId.type.name,
                updatedAt = withId.updatedAt
            )
        )
        if (nostrClient.hasSigner) {
            try {
                nostrClient.publishEncryptedAppData("$NOSTR_D_TAG_PREFIX${withId.id}", jsonStr)
                Log.d(TAG, "Published credit account ${withId.id.take(8)}…")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish credit account: ${e.message}")
            }
        }
        return ensureBillForAccount(withId)
    }

    /** When balance > 0, ensure a linked bill exists (create or update). When balance == 0, set bill amount to 0. */
    private suspend fun ensureBillForAccount(account: CreditAccount): CreditAccount {
        val subcategory = when (account.type) {
            CreditAccountType.CREDIT_CARD -> BillSubcategory.CREDIT_CARD
            CreditAccountType.STUDENT_LOAN -> BillSubcategory.STUDENT_LOAN
            else -> BillSubcategory.OTHER_LOAN
        }
        return when {
            account.currentBalance > 0 -> {
                if (account.linkedBillId != null) {
                    val existing = billRepository.getBillById(account.linkedBillId).first()
                    if (existing != null) {
                        billRepository.saveBill(
                            existing.copy(
                                name = account.name,
                                amount = account.effectiveMonthlyPayment(),
                                dueDay = account.dueDay,
                                subcategory = subcategory,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        account
                    } else {
                        createAndLinkBill(account, subcategory)
                    }
                } else {
                    createAndLinkBill(account, subcategory)
                }
            }
            account.linkedBillId != null -> {
                billRepository.getBillById(account.linkedBillId).first()?.let { existing ->
                    billRepository.saveBill(
                        existing.copy(
                            amount = 0.0,
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                }
                account
            }
            else -> account
        }
    }

    private suspend fun createAndLinkBill(account: CreditAccount, billSubcategory: BillSubcategory): CreditAccount {
        val billId = UUID.randomUUID().toString()
        val bill = Bill(
            id = billId,
            name = account.name,
            amount = account.effectiveMonthlyPayment(),
            subcategory = billSubcategory,
            frequency = BillFrequency.MONTHLY,
            dueDay = account.dueDay,
            linkedCreditAccountId = account.id,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        billRepository.saveBill(bill)
        val updated = account.copy(linkedBillId = billId)
        val jsonStr = json.encodeToString(CreditAccount.serializer(), updated)
        creditAccountDao.upsert(
            CreditAccountEntity(
                id = updated.id,
                jsonData = jsonStr,
                type = updated.type.name,
                updatedAt = updated.updatedAt
            )
        )
        if (nostrClient.hasSigner) {
            try {
                nostrClient.publishEncryptedAppData("$NOSTR_D_TAG_PREFIX${updated.id}", jsonStr)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish credit account after linking bill: ${e.message}")
            }
        }
        return updated
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

    suspend fun deleteCreditAccount(account: CreditAccount) {
        creditAccountDao.delete(
            CreditAccountEntity(id = account.id, jsonData = "", type = account.type.name)
        )
        if (nostrClient.hasSigner) {
            val dTag = "$NOSTR_D_TAG_PREFIX${account.id}"
            try {
                nostrClient.publishEncryptedAppData(dTag, """{"deleted":true}""")
                nostrClient.publishDeletion(NostrEvent.KIND_APP_SPECIFIC_DATA, dTag)
                Log.d(TAG, "Published delete for credit account ${account.id.take(8)}…")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish credit account deletion: ${e.message}")
            }
        }
    }

    suspend fun syncFromNostr() {
        if (!nostrClient.hasSigner) return
        try {
            withTimeout(30_000) {
                var count = 0
                nostrClient.subscribeToAppData(dTagPrefix = NOSTR_D_TAG_PREFIX).collect { (dTag, decrypted) ->
                    try {
                        val obj = json.parseToJsonElement(decrypted).jsonObject
                        if (obj["deleted"]?.jsonPrimitive?.booleanOrNull == true) {
                            val id = dTag.removePrefix(NOSTR_D_TAG_PREFIX)
                            creditAccountDao.deleteById(id)
                            Log.d(TAG, "Deleted tombstoned credit account $id")
                            return@collect
                        }
                        val account = json.decodeFromString<CreditAccount>(decrypted)
                        if (account.id.isNotEmpty()) {
                            creditAccountDao.upsert(
                                CreditAccountEntity(
                                    id = account.id,
                                    jsonData = decrypted,
                                    type = account.type.name,
                                    updatedAt = account.updatedAt
                                )
                            )
                            count++
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse credit account event: ${e.message}")
                    }
                }
                Log.d(TAG, "Synced $count credit account(s) from relay")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Credit account sync failed: ${e.message}")
        }
    }
}
