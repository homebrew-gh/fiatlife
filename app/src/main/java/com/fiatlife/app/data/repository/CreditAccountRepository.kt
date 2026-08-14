package com.fiatlife.app.data.repository

import android.util.Log
import com.fiatlife.app.data.blossom.BlossomClient
import com.fiatlife.app.data.local.dao.CreditAccountDao
import com.fiatlife.app.data.local.entity.CreditAccountEntity
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.data.nostr.NostrEvent
import com.fiatlife.app.domain.model.Bill
import com.fiatlife.app.domain.model.BillPayment
import com.fiatlife.app.domain.model.BillFrequency
import com.fiatlife.app.domain.model.BillGeneralCategory
import com.fiatlife.app.domain.model.BillSubcategory
import com.fiatlife.app.domain.model.CreditAccount
import com.fiatlife.app.domain.model.CreditAccountType
import com.fiatlife.app.domain.model.CreditStatementUpdate
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
        }.decodeOnBackground()
    }

    fun getCreditAccountById(id: String): Flow<CreditAccount?> {
        return creditAccountDao.getByIdAsFlow(id).map { entity ->
            entity?.let { json.decodeFromString<CreditAccount>(it.jsonData) }
        }.decodeOnBackground()
    }

    suspend fun saveCreditAccount(
        account: CreditAccount,
        inferPayment: Boolean = true
    ): CreditAccount {
        val previous = account.id.takeIf { it.isNotBlank() }?.let { id ->
            creditAccountDao.getById(id)?.let { entity ->
                runCatching { json.decodeFromString<CreditAccount>(entity.jsonData) }.getOrNull()
            }
        }
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
        val ensured = ensureBillForAccount(withId)
        if (inferPayment) inferCreditPaymentFromBalanceDrop(previous, ensured)
        return ensured
    }

    suspend fun updateStatement(
        account: CreditAccount,
        update: CreditStatementUpdate
    ): CreditAccount {
        val updated = account.copy(
            currentBalance = update.balanceAfterPayment.coerceAtLeast(0.0),
            statementBalanceAsOfMillis = update.statementBalanceAsOfMillis,
            statementAmountDue = update.statementAmountDue?.coerceAtLeast(0.0),
            dueDay = update.dueDay.coerceIn(1, 31)
        )
        val saved = saveCreditAccount(updated, inferPayment = false)
        if (update.paymentAmount > 0.0) {
            val billId = saved.linkedBillId
            val bill = billId?.let { billRepository.getBillById(it).first() }
            if (bill != null) {
                val now = System.currentTimeMillis()
                billRepository.saveBill(
                    bill.copy(
                        paymentHistory = bill.paymentHistory +
                            BillPayment(now, update.paymentAmount),
                        isPaid = true,
                        lastPaidDate = now,
                        updatedAt = now
                    )
                )
            }
        }
        return saved
    }

    /** Ensure account-linked bills are in sync (monthly payment + optional annual fee bill). */
    private suspend fun ensureBillForAccount(account: CreditAccount): CreditAccount {
        val primaryUpdated = ensurePrimaryBillForAccount(account)
        return ensureAnnualFeeBill(primaryUpdated)
    }

    /** Keep the account-linked payment reminder synchronized without deleting its history at zero balance. */
    private suspend fun ensurePrimaryBillForAccount(account: CreditAccount): CreditAccount {
        val subcategory = when (account.type) {
            CreditAccountType.CREDIT_CARD -> BillSubcategory.CREDIT_CARD
            CreditAccountType.STUDENT_LOAN -> BillSubcategory.STUDENT_LOAN
            else -> BillSubcategory.OTHER_LOAN
        }
        val allBills = billRepository.getAllBills().first()
        suspend fun updateLinkedBill(existing: Bill) {
            billRepository.saveBill(
                existing.copy(
                    name = account.name,
                    amount = if (account.currentBalance > 0.0) {
                        account.effectiveMonthlyPayment()
                    } else {
                        0.0
                    },
                    dueDay = account.dueDay,
                    subcategory = subcategory,
                    isRecurring = true,
                    isCancelled = false,
                    linkedCreditAccountId = account.id,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        if (account.currentBalance > 0) {
            val existingByLinkedAccount = allBills.firstOrNull { it.linkedCreditAccountId == account.id }
            val existingLegacyByName = allBills.firstOrNull { bill ->
                bill.linkedCreditAccountId == null &&
                    bill.effectiveGeneralCategory == BillGeneralCategory.CREDIT_LOANS &&
                    bill.name.equals(account.name, ignoreCase = true) &&
                    bill.effectiveSubcategory == subcategory
            }
            val billId = account.linkedBillId
            if (billId != null) {
                val existing = billRepository.getBillById(billId).first()
                if (existing != null) {
                    updateLinkedBill(existing)
                    return account
                }
            }
            val existingCandidate = existingByLinkedAccount ?: existingLegacyByName
            if (existingCandidate != null) {
                updateLinkedBill(existingCandidate)
                if (account.linkedBillId != existingCandidate.id) {
                    val relinked = account.copy(linkedBillId = existingCandidate.id)
                    persistAccountWithoutEnsure(relinked)
                    return relinked
                }
                return account
            }
            return createAndLinkBill(account, subcategory)
        }

        val existingPaidOff = account.linkedBillId
            ?.let { billRepository.getBillById(it).first() }
            ?: allBills.firstOrNull { it.linkedCreditAccountId == account.id }
        if (existingPaidOff != null) {
            updateLinkedBill(existingPaidOff)
            if (account.linkedBillId != existingPaidOff.id) {
                val relinked = account.copy(linkedBillId = existingPaidOff.id)
                persistAccountWithoutEnsure(relinked)
                return relinked
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
            isRecurring = true,
            dueDay = account.dueDay,
            renewalDateMillis = null,
            initialPurchaseDateMillis = null,
            recurrenceUnit = null,
            recurrenceIntervalCount = 1,
            recurrenceTimezone = null,
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
                val localBefore = creditAccountDao.getAllSnapshot().associateBy { it.id }
                val deleteIds = mutableListOf<String>()
                val upsertsById = mutableMapOf<String, CreditAccountEntity>()
                nostrClient.subscribeToAppData(dTagPrefix = NOSTR_D_TAG_PREFIX).collect { (dTag, decrypted) ->
                    try {
                        val obj = json.parseToJsonElement(decrypted).jsonObject
                        if (obj["deleted"]?.jsonPrimitive?.booleanOrNull == true) {
                            val id = dTag.removePrefix(NOSTR_D_TAG_PREFIX)
                            deleteIds.add(id)
                            upsertsById.remove(id)
                            return@collect
                        }
                        val account = json.decodeFromString<CreditAccount>(decrypted)
                        if (account.id.isNotEmpty()) {
                            upsertsById[account.id] = CreditAccountEntity(
                                id = account.id,
                                jsonData = decrypted,
                                type = account.type.name,
                                updatedAt = account.updatedAt
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse credit account event: ${e.message}")
                    }
                }
                creditAccountDao.applySyncBatch(upsertsById.values.toList(), deleteIds)
                Log.d(TAG, "Synced ${upsertsById.size} credit account(s) from relay; deleted ${deleteIds.size}")
                republishStrandedAccounts(localBefore, upsertsById, deleteIds)
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
     * Self-heal: push local credit accounts the relay doesn't reflect back up so
     * other clients (e.g. the web app) can see them. Covers accounts whose
     * original publish was never delivered (the outbox is in-memory only) and
     * accounts whose local copy is newer than the relay's.
     */
    private suspend fun republishStrandedAccounts(
        localBefore: Map<String, CreditAccountEntity>,
        relayById: Map<String, CreditAccountEntity>,
        deleteIds: List<String>
    ) {
        val deleted = deleteIds.toSet()
        var pushed = 0
        for ((id, local) in localBefore) {
            if (id in deleted) continue
            val relay = relayById[id]
            if (relay != null && local.updatedAt <= relay.updatedAt) continue
            if (relay != null) creditAccountDao.upsert(local)
            runCatching {
                nostrClient.publishEncryptedAppData("$NOSTR_D_TAG_PREFIX$id", local.jsonData)
            }.onSuccess { pushed++ }
                .onFailure { Log.w(TAG, "Self-heal republish failed for credit account ${id.take(8)}…: ${it.message}") }
        }
        if (pushed > 0) Log.d(TAG, "Self-heal re-published $pushed credit account(s) the relay was missing")
    }

    /**
     * One-time-safe backfill: create/link a bill for any positive-balance account that has no valid linked bill.
     * Idempotent for already-linked accounts.
     */
    suspend fun backfillMissingLinkedBills(): Int {
        migrateLegacyCreditCardBills()
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
     * Convert the old Bill-owned credit-card payload into a Debt account.
     * The existing Bill id and all payment/statement history are retained.
     */
    private suspend fun migrateLegacyCreditCardBills(): Int {
        val existingAccounts = creditAccountDao.getAllSnapshot().mapNotNull { entity ->
            runCatching {
                json.decodeFromString<CreditAccount>(entity.jsonData)
            }.getOrNull()
        }
        val bills = billRepository.getAllBills().first()
        var migrated = 0
        for (bill in bills) {
            val legacy = bill.creditCardDetails ?: continue
            if (bill.linkedCreditAccountId != null) continue
            if (existingAccounts.any {
                    it.linkedBillId == bill.id ||
                        it.name.equals(bill.name, ignoreCase = true)
                }
            ) continue

            val now = System.currentTimeMillis()
            val account = CreditAccount(
                id = UUID.randomUUID().toString(),
                name = bill.name,
                type = CreditAccountType.CREDIT_CARD,
                institution = bill.accountName,
                apr = legacy.apr,
                standardApr = legacy.apr,
                currentBalance = legacy.currentBalance.coerceAtLeast(0.0),
                statementAmountDue = bill.amount.coerceAtLeast(0.0),
                dueDay = bill.dueDay,
                linkedBillId = bill.id,
                notes = bill.notes,
                createdAt = bill.createdAt.takeIf { it > 0 } ?: now,
                updatedAt = now,
                statementEntries = bill.statementEntries,
                attachmentHashes = bill.attachmentHashes,
                minimumPaymentType = legacy.minimumPaymentType,
                minimumPaymentValue = legacy.minimumPaymentValue
            )
            val saved = saveCreditAccount(account, inferPayment = false)
            billRepository.saveBill(
                bill.copy(
                    amount = saved.effectiveAmountDue(),
                    linkedCreditAccountId = saved.id,
                    creditCardDetails = null,
                    updatedAt = now
                )
            )
            migrated++
        }
        return migrated
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

    private suspend fun inferCreditPaymentFromBalanceDrop(previous: CreditAccount?, current: CreditAccount) {
        if (previous == null) return
        if (!current.type.isRevolving && !current.type.isAmortizing) return
        val delta = previous.currentBalance - current.currentBalance
        if (delta <= 0.0) return
        val linkedBillId = current.linkedBillId ?: return
        val bill = billRepository.getBillById(linkedBillId).first() ?: return
        val now = System.currentTimeMillis()
        val payment = BillPayment(date = now, amount = delta)
        val hasNearby = bill.paymentHistory.any { existing ->
            kotlin.math.abs(existing.date - now) <= 90_000L && kotlin.math.abs(existing.amount - delta) <= 0.01
        }
        if (hasNearby) return
        billRepository.saveBill(
            bill.copy(
                paymentHistory = bill.paymentHistory + payment,
                isPaid = true,
                lastPaidDate = now
            )
        )
    }
}
