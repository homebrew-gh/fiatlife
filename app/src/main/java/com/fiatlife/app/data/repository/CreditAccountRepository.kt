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

    /** Ensure account-linked bills are in sync (monthly payment + optional annual fee bill). */
    private suspend fun ensureBillForAccount(account: CreditAccount): CreditAccount {
        val primaryUpdated = ensurePrimaryBillForAccount(account)
        return ensureAnnualFeeBill(primaryUpdated)
    }

    /** When balance > 0, ensure a linked monthly payment bill exists. When balance == 0, keep linked bill with amount 0. */
    private suspend fun ensurePrimaryBillForAccount(account: CreditAccount): CreditAccount {
        val subcategory = when (account.type) {
            CreditAccountType.CREDIT_CARD -> BillSubcategory.CREDIT_CARD
            CreditAccountType.STUDENT_LOAN -> BillSubcategory.STUDENT_LOAN
            else -> BillSubcategory.OTHER_LOAN
        }
        if (account.currentBalance > 0) {
            val existingByLinkedAccount = billRepository.getAllBills().first()
                .firstOrNull { it.linkedCreditAccountId == account.id }
            val billId = account.linkedBillId
            if (billId != null) {
                val existing = billRepository.getBillById(billId).first()
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
                    return account
                }
            }
            if (existingByLinkedAccount != null) {
                billRepository.saveBill(
                    existingByLinkedAccount.copy(
                        name = account.name,
                        amount = account.effectiveMonthlyPayment(),
                        dueDay = account.dueDay,
                        subcategory = subcategory,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                if (account.linkedBillId != existingByLinkedAccount.id) {
                    return saveCreditAccount(account.copy(linkedBillId = existingByLinkedAccount.id))
                }
                return account
            }
            return createAndLinkBill(account, subcategory)
        }
        val billId = account.linkedBillId
        if (billId != null) {
            billRepository.getBillById(billId).first()?.let { existing ->
                billRepository.saveBill(
                    existing.copy(
                        amount = 0.0,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        return account
    }

    /** For credit cards with annual fee configured, ensure a separate recurring fee bill exists and is linked. */
    private suspend fun ensureAnnualFeeBill(account: CreditAccount): CreditAccount {
        val hasAnnualFee = account.type == CreditAccountType.CREDIT_CARD &&
            account.annualFeeAmount > 0.0 &&
            account.annualFeeRenewalDateMillis != null

        if (!hasAnnualFee) {
            val linkedFeeId = account.annualFeeLinkedBillId
            if (linkedFeeId != null) {
                billRepository.getBillById(linkedFeeId).first()?.let { existing ->
                    billRepository.deleteBill(existing)
                }
                val cleared = account.copy(annualFeeLinkedBillId = null)
                persistAccountWithoutEnsure(cleared)
                return cleared
            }
            return account
        }

        val renewal = account.annualFeeRenewalDateMillis ?: return account
        val dueDay = dayOfMonthFromMillis(renewal)
        val billName = "${account.name} Annual Fee"

        val updateBill: (Bill) -> Bill = { existing ->
            existing.copy(
                name = billName,
                amount = account.annualFeeAmount,
                frequency = account.annualFeeFrequency,
                dueDay = dueDay,
                subcategory = BillSubcategory.FINANCE,
                isRecurring = true,
                renewalDateMillis = renewal,
                initialPurchaseDateMillis = renewal,
                linkedCreditAccountId = null,
                accountName = account.name,
                billerName = account.name,
                updatedAt = System.currentTimeMillis()
            )
        }

        val linkedFeeId = account.annualFeeLinkedBillId
        if (linkedFeeId != null) {
            val existingLinked = billRepository.getBillById(linkedFeeId).first()
            if (existingLinked != null) {
                billRepository.saveBill(updateBill(existingLinked))
                return account
            }
        }

        val existingByName = billRepository.getAllBills().first().firstOrNull { b ->
            b.linkedCreditAccountId == null &&
                b.name.equals(billName, ignoreCase = true) &&
                b.billerName.equals(account.name, ignoreCase = true)
        }
        if (existingByName != null) {
            billRepository.saveBill(updateBill(existingByName))
            val relinked = account.copy(annualFeeLinkedBillId = existingByName.id)
            persistAccountWithoutEnsure(relinked)
            return relinked
        }

        val feeBillId = UUID.randomUUID().toString()
        val feeBill = Bill(
            id = feeBillId,
            name = billName,
            amount = account.annualFeeAmount,
            subcategory = BillSubcategory.FINANCE,
            frequency = account.annualFeeFrequency,
            dueDay = dueDay,
            renewalDateMillis = renewal,
            initialPurchaseDateMillis = renewal,
            isRecurring = true,
            accountName = account.name,
            billerName = account.name,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        billRepository.saveBill(feeBill)
        val linked = account.copy(annualFeeLinkedBillId = feeBillId)
        persistAccountWithoutEnsure(linked)
        return linked
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
        persistAccountWithoutEnsure(updated)
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
        account.annualFeeLinkedBillId?.let { feeBillId ->
            billRepository.getBillById(feeBillId).first()?.let { feeBill ->
                billRepository.deleteBill(feeBill)
            }
        }
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
                val backfilled = backfillMissingLinkedBills()
                if (backfilled > 0) {
                    Log.d(TAG, "Backfilled $backfilled missing linked bill(s) for credit accounts")
                }
                val deduped = dedupeLinkedBillsForAccounts()
                if (deduped > 0) {
                    Log.d(TAG, "Removed $deduped duplicate linked credit/loan bill(s)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Credit account sync failed: ${e.message}")
        }
    }

    /**
     * One-time-safe backfill: create/link a bill for any positive-balance account that has no valid linked bill.
     * Idempotent for already-linked accounts.
     */
    suspend fun backfillMissingLinkedBills(): Int {
        val accounts = creditAccountDao.getAllSnapshot().mapNotNull { entity ->
            runCatching { json.decodeFromString<CreditAccount>(entity.jsonData) }
                .onFailure { Log.w(TAG, "Skipping malformed credit account ${entity.id}: ${it.message}") }
                .getOrNull()
        }
        val allBills = billRepository.getAllBills().first()
        var created = 0
        accounts.forEach { account ->
            if (account.currentBalance <= 0.0) return@forEach
            val hasValidLinkedBill = account.linkedBillId?.let { billId ->
                billRepository.getBillById(billId).first() != null
            } ?: false
            val existingByLinkedAccount = allBills.firstOrNull { it.linkedCreditAccountId == account.id }
            if (hasValidLinkedBill || existingByLinkedAccount != null) {
                if (!hasValidLinkedBill && existingByLinkedAccount != null && account.linkedBillId != existingByLinkedAccount.id) {
                    saveCreditAccount(account.copy(linkedBillId = existingByLinkedAccount.id))
                }
                return@forEach
            }

            val subcategory = when (account.type) {
                CreditAccountType.CREDIT_CARD -> BillSubcategory.CREDIT_CARD
                CreditAccountType.STUDENT_LOAN -> BillSubcategory.STUDENT_LOAN
                else -> BillSubcategory.OTHER_LOAN
            }
            createAndLinkBill(account, subcategory)
            created++
        }
        // Ensure annual fee bills exist even when balance is zero.
        accounts.forEach { account ->
            ensureAnnualFeeBill(account)
        }
        return created
    }

    /**
     * Ensure there is at most one bill linked to each credit account.
     * Keeps the most recently updated bill and deletes older duplicates.
     */
    private suspend fun dedupeLinkedBillsForAccounts(): Int {
        val accounts = creditAccountDao.getAllSnapshot().mapNotNull { entity ->
            runCatching { json.decodeFromString<CreditAccount>(entity.jsonData) }.getOrNull()
        }
        val allBills = billRepository.getAllBills().first()
        var removed = 0
        accounts.forEach { account ->
            val linked = allBills.filter { it.linkedCreditAccountId == account.id }
            if (linked.size <= 1) return@forEach
            val keep = linked.maxByOrNull { it.updatedAt } ?: return@forEach
            linked.filter { it.id != keep.id }.forEach { dup ->
                billRepository.deleteBill(dup)
                removed++
            }
            if (account.linkedBillId != keep.id) {
                saveCreditAccount(account.copy(linkedBillId = keep.id))
            }
        }
        return removed
    }

    private suspend fun persistAccountWithoutEnsure(account: CreditAccount) {
        val jsonStr = json.encodeToString(CreditAccount.serializer(), account)
        creditAccountDao.upsert(
            CreditAccountEntity(
                id = account.id,
                jsonData = jsonStr,
                type = account.type.name,
                updatedAt = account.updatedAt
            )
        )
        if (nostrClient.hasSigner) {
            try {
                nostrClient.publishEncryptedAppData("$NOSTR_D_TAG_PREFIX${account.id}", jsonStr)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish credit account update: ${e.message}")
            }
        }
    }

    private fun dayOfMonthFromMillis(millis: Long): Int {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = millis
        return cal.get(java.util.Calendar.DAY_OF_MONTH).coerceIn(1, 31)
    }
}
