package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.repository.BillRepository
import com.fiatlife.app.data.repository.BillerRepository
import com.fiatlife.app.data.repository.CypherLogSubscriptionRepository
import com.fiatlife.app.domain.model.Bill
import com.fiatlife.app.domain.model.BillFrequency
import com.fiatlife.app.domain.model.BillStatusEvent
import com.fiatlife.app.domain.model.BillSubcategory
import com.fiatlife.app.domain.model.StatementEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class CompanyHistoryRow(
    val key: String,
    val name: String,
    val isArchived: Boolean,
    val billCount: Int,
    val totalPaid: Double,
    val paymentCount: Int,
    val lastPaidDate: Long?,
    val lastActivityAt: Long
)

data class CompanyPaymentRow(
    val id: String,
    val companyKey: String,
    val companyName: String,
    val billId: String,
    val billName: String,
    val amount: Double,
    val paidDate: Long,
    val hasInvoiceOrStatement: Boolean
)

data class CompanyStatementRow(
    val id: String,
    val companyKey: String,
    val billId: String,
    val billName: String,
    val label: String,
    val hash: String,
    val addedAt: Long
)

data class CompanyHistoryState(
    val companies: List<CompanyHistoryRow> = emptyList(),
    val paymentsByCompanyKey: Map<String, List<CompanyPaymentRow>> = emptyMap(),
    val statementsByCompanyKey: Map<String, List<CompanyStatementRow>> = emptyMap(),
    val billsByCompanyKey: Map<String, List<Bill>> = emptyMap(),
    val cypherBillIds: Set<String> = emptySet(),
    val showArchived: Boolean = false,
    val message: String = ""
)

@HiltViewModel
class CompanyHistoryViewModel @Inject constructor(
    private val billRepository: BillRepository,
    private val cypherLogSubscriptionRepository: CypherLogSubscriptionRepository,
    private val billerRepository: BillerRepository
) : ViewModel() {
    private val _state = MutableStateFlow(CompanyHistoryState())
    val state: StateFlow<CompanyHistoryState> = _state.asStateFlow()
    private var allCompaniesCache: List<CompanyHistoryRow> = emptyList()

    init {
        viewModelScope.launch {
            combine(
                billRepository.getAllBills(),
                cypherLogSubscriptionRepository.getAllAsBills(),
                billerRepository.getAllBillers()
            ) { nativeBills, cypherLogBills, billers ->
                val cypherIds = cypherLogBills.map { it.bill.id }.toSet()
                val allBills = nativeBills + cypherLogBills.map { it.bill }
                buildState(allBills, cypherIds, billers)
            }.collect { next ->
                allCompaniesCache = next.companies
                _state.update { prev ->
                    prev.copy(
                        companies = allCompaniesCache.filter { if (prev.showArchived) it.isArchived else !it.isArchived },
                        paymentsByCompanyKey = next.paymentsByCompanyKey,
                        statementsByCompanyKey = next.statementsByCompanyKey,
                        billsByCompanyKey = next.billsByCompanyKey,
                        cypherBillIds = next.cypherBillIds
                    )
                }
            }
        }
    }

    private data class CompanyRef(val key: String, val name: String)

    private fun companyForBill(
        bill: Bill,
        billersById: Map<String, com.fiatlife.app.domain.model.Biller>
    ): CompanyRef? {
        val label = bill.billerName.ifBlank { bill.accountName }.trim()
        val key = when {
            !bill.linkedBillerId.isNullOrBlank() -> "id:${bill.linkedBillerId}"
            label.isNotBlank() -> "name:${normalizeCompany(label)}"
            else -> return null
        }
        // Prefer biller entity name; fall back to bill's billerName/accountName when biller is missing or blank.
        val name = when {
            !bill.linkedBillerId.isNullOrBlank() -> {
                billersById[bill.linkedBillerId]?.name?.takeIf { it.isNotBlank() } ?: label
            }
            else -> label
        }.ifBlank { "Unknown company" }
        return CompanyRef(key = key, name = name)
    }

    private fun buildState(
        bills: List<Bill>,
        cypherBillIds: Set<String>,
        billers: List<com.fiatlife.app.domain.model.Biller>
    ): CompanyHistoryState {
        val billersById = billers.associateBy { it.id }
        val archivedByNormalized = billers
            .associateBy({ it.normalizedName }, { it.isArchived })
        val annotatedBills = bills.mapNotNull { bill ->
            companyForBill(bill, billersById)?.let { ref -> ref to bill }
        }
        val billsByCompany = annotatedBills
            .groupBy({ it.first.key }, { it.second })
            .mapValues { (_, rows) -> rows.sortedByDescending { it.updatedAt } }

        val paymentsByCompany = annotatedBills
            .flatMap { (company, bill) ->
                bill.paymentHistory.mapIndexed { index, p ->
                    CompanyPaymentRow(
                        id = "${bill.id}|${p.date}|$index",
                        companyKey = company.key,
                        companyName = company.name,
                        billId = bill.id,
                        billName = bill.name,
                        amount = p.amount,
                        paidDate = p.date,
                        hasInvoiceOrStatement = bill.statementEntries.isNotEmpty() || bill.attachmentHashes.isNotEmpty()
                    )
                }
            }
            .groupBy { it.companyKey }
            .mapValues { (_, rows) -> rows.sortedByDescending { it.paidDate } }

        val statementsByCompany = annotatedBills
            .flatMap { (company, bill) ->
                bill.statementsOrderedByDate().mapIndexed { index, entry ->
                    CompanyStatementRow(
                        id = "${bill.id}|${entry.hash}|${entry.addedAt}|$index",
                        companyKey = company.key,
                        billId = bill.id,
                        billName = bill.name,
                        label = entry.label.ifBlank { "Statement" },
                        hash = entry.hash,
                        addedAt = entry.addedAt.takeIf { it > 0 } ?: bill.updatedAt
                    )
                }
            }
            .groupBy { it.companyKey }
            .mapValues { (_, rows) -> rows.sortedByDescending { it.addedAt } }

        val companyRows = billsByCompany.mapNotNull { (companyKey, companyBills) ->
            if (companyBills.isEmpty()) return@mapNotNull null
            val companyName = annotatedBills.firstOrNull { it.first.key == companyKey }?.first?.name ?: "Unknown company"
            val payments = paymentsByCompany[companyKey].orEmpty()
            val totalPaid = payments.sumOf { it.amount }
            val paymentCount = payments.size
            val lastPaid = payments.maxOfOrNull { it.paidDate }
            val lastBillUpdate = companyBills.maxOfOrNull { it.updatedAt } ?: 0L
            val lastActivity = maxOf(lastBillUpdate, lastPaid ?: 0L)
            val isArchived = when {
                companyKey.startsWith("id:") -> billersById[companyKey.removePrefix("id:")]?.isArchived == true
                companyKey.startsWith("name:") -> archivedByNormalized[companyKey.removePrefix("name:")] == true
                else -> false
            }
            CompanyHistoryRow(
                key = companyKey,
                name = companyName,
                isArchived = isArchived,
                billCount = companyBills.size,
                totalPaid = totalPaid,
                paymentCount = paymentCount,
                lastPaidDate = lastPaid,
                lastActivityAt = lastActivity
            )
        }.sortedByDescending { it.lastActivityAt }

        return CompanyHistoryState(
            companies = companyRows,
            paymentsByCompanyKey = paymentsByCompany,
            statementsByCompanyKey = statementsByCompany,
            billsByCompanyKey = billsByCompany,
            cypherBillIds = cypherBillIds
        )
    }

    fun setShowArchived(show: Boolean) {
        _state.update { state ->
            state.copy(
                showArchived = show,
                companies = allCompaniesCache.filter { if (show) it.isArchived else !it.isArchived }
            )
        }
    }

    fun uploadStatementForCompany(
        companyKey: String,
        bytes: ByteArray,
        contentType: String,
        filename: String,
        targetBillId: String?
    ) {
        viewModelScope.launch {
            val target = selectTargetBill(companyKey, targetBillId)
            if (target == null) {
                _state.update { it.copy(message = "No bill found for this company.") }
                return@launch
            }
            billRepository.uploadAttachment(bytes, contentType, filename)
                .onSuccess { hash ->
                    val now = System.currentTimeMillis()
                    val nextStatements = target.statementEntries + StatementEntry(
                        hash = hash,
                        addedAt = now,
                        label = filename
                    )
                    billRepository.saveBill(target.copy(statementEntries = nextStatements))
                    _state.update { it.copy(message = "Statement uploaded to ${target.name}.") }
                }
                .onFailure { e ->
                    _state.update { it.copy(message = "Upload failed: ${e.message}") }
                }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = "") }
    }

    fun skipSubscriptionInterval(companyKey: String, billId: String) {
        val bill = selectTargetBill(companyKey, billId) ?: return
        if (!bill.canSkipInterval()) {
            _state.update { it.copy(message = "Skip is only available for Food and Health/Wellness subscriptions.") }
            return
        }
        val skippedDue = bill.skippedNextDueDateMillis()
        if (skippedDue == null) {
            _state.update { it.copy(message = "Could not calculate next interval.") }
            return
        }
        val now = System.currentTimeMillis()
        val updated = bill.copy(
            renewalDateMillis = skippedDue,
            isPaid = false,
            lastPaidDate = null,
            statusHistory = bill.statusHistory + BillStatusEvent(
                date = now,
                type = "skipped_interval",
                note = "Skipped one billing interval"
            )
        )
        saveBillBySource(updated)
    }

    fun cancelSubscription(companyKey: String, billId: String) {
        val bill = selectTargetBill(companyKey, billId) ?: return
        val now = System.currentTimeMillis()
        val updated = bill.copy(
            isCancelled = true,
            cancelledAt = now,
            statusHistory = bill.statusHistory + BillStatusEvent(
                date = now,
                type = "cancelled",
                note = "Subscription cancelled by user"
            )
        )
        saveBillBySource(updated)
    }

    fun reactivateSubscription(
        companyKey: String,
        billId: String,
        frequency: BillFrequency,
        newBillingDateMillis: Long
    ) {
        val bill = selectTargetBill(companyKey, billId) ?: return
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = newBillingDateMillis }
        val dueDay = cal.get(java.util.Calendar.DAY_OF_MONTH).coerceIn(1, 31)
        val now = System.currentTimeMillis()
        val updated = bill.copy(
            isCancelled = false,
            cancelledAt = null,
            isRecurring = true,
            frequency = frequency,
            dueDay = dueDay,
            initialPurchaseDateMillis = newBillingDateMillis,
            renewalDateMillis = newBillingDateMillis,
            isPaid = false,
            lastPaidDate = null,
            statusHistory = bill.statusHistory + BillStatusEvent(
                date = now,
                type = "activated",
                note = "Subscription reactivated"
            )
        )
        saveBillBySource(updated)
    }

    fun setCompanyArchived(companyKey: String, companyName: String, archived: Boolean) {
        viewModelScope.launch {
            when {
                companyKey.startsWith("id:") -> {
                    val id = companyKey.removePrefix("id:")
                    val biller = billerRepository.getById(id) ?: return@launch
                    billerRepository.saveBiller(biller.copy(isArchived = archived))
                }
                companyKey.startsWith("name:") -> {
                    val normalized = companyKey.removePrefix("name:")
                    val existing = billerRepository.getByNormalizedName(normalized)
                    val base = existing ?: billerRepository.getOrCreateByName(companyName)
                    billerRepository.saveBiller(base.copy(isArchived = archived))
                }
            }
        }
    }

    fun deleteCompany(companyKey: String, companyName: String) {
        viewModelScope.launch {
            val bills = _state.value.billsByCompanyKey[companyKey].orEmpty()
            val cypherIds = _state.value.cypherBillIds
            bills.forEach { bill ->
                if (bill.id in cypherIds) cypherLogSubscriptionRepository.deleteSubscription(bill.id)
                else billRepository.deleteBill(bill)
            }
            when {
                companyKey.startsWith("id:") -> {
                    billerRepository.deleteById(companyKey.removePrefix("id:"))
                }
                companyKey.startsWith("name:") -> {
                    val normalized = companyKey.removePrefix("name:")
                    billerRepository.getByNormalizedName(normalized)?.let { billerRepository.deleteById(it.id) }
                }
            }
            _state.update {
                it.copy(
                    message = "Deleted company \"$companyName\" and associated bills. Note: relay policy may keep historical Nostr events."
                )
            }
        }
    }

    private fun selectTargetBill(companyKey: String, targetBillId: String?): Bill? {
        val bills = _state.value.billsByCompanyKey[companyKey].orEmpty()
        if (bills.isEmpty()) return null
        targetBillId?.let { id -> bills.firstOrNull { it.id == id }?.let { return it } }
        return bills.maxByOrNull { it.updatedAt }
    }

    private fun saveBillBySource(updated: Bill) {
        viewModelScope.launch {
            if (updated.id in _state.value.cypherBillIds) {
                val result = cypherLogSubscriptionRepository.saveSubscriptionDetailed(updated, null)
                _state.update {
                    it.copy(
                        message = if (result.success) "Subscription updated." else "Update failed: ${result.reason}"
                    )
                }
            } else {
                billRepository.saveBill(updated)
                _state.update { it.copy(message = "Subscription updated.") }
            }
        }
    }

    private fun normalizeCompany(value: String): String =
        value.trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
}
