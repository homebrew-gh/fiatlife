package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.nostr.NostrClient
import com.fiatlife.app.data.repository.BankAccountRepository
import com.fiatlife.app.data.repository.BillRepository
import com.fiatlife.app.data.repository.BillerRepository
import com.fiatlife.app.data.repository.CreditAccountRepository
import com.fiatlife.app.data.repository.CypherLogSubscriptionRepository
import com.fiatlife.app.domain.model.BankAccount
import com.fiatlife.app.domain.model.Bill
import com.fiatlife.app.domain.model.CreditAccount
import com.fiatlife.app.domain.model.Biller
import com.fiatlife.app.domain.model.BillGeneralCategory
import com.fiatlife.app.domain.model.BillPayment
import com.fiatlife.app.domain.model.BillWithSource
import com.fiatlife.app.domain.model.StatementEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Per-account monthly total for summary breakdown. */
data class PaymentBreakdownRow(
    val id: String,
    val name: String,
    val isCredit: Boolean,
    val total: Double
)

data class BillsState(
    val bills: List<BillWithSource> = emptyList(),
    val filteredBills: List<BillWithSource> = emptyList(),
    val creditAccounts: List<CreditAccount> = emptyList(),
    val bankAccounts: List<BankAccount> = emptyList(),
    val billers: List<Biller> = emptyList(),
    /** Breakdown by payment account (banks then credit cards) for summary. */
    val paymentBreakdown: List<PaymentBreakdownRow> = emptyList(),
    val paymentSubtotalBanks: Double = 0.0,
    val paymentSubtotalCredit: Double = 0.0,
    val annualPaymentBreakdown: List<PaymentBreakdownRow> = emptyList(),
    val annualPaymentSubtotalBanks: Double = 0.0,
    val annualPaymentSubtotalCredit: Double = 0.0,
    val selectedGeneralCategory: BillGeneralCategory? = null,
    val showAddDialog: Boolean = false,
    val editingBill: Bill? = null,
    val editingIsCypherLog: Boolean = false,
    val editingPreservedTags: Map<String, List<String>>? = null,
    val dialogStatementEntries: List<StatementEntry> = emptyList(),
    val navigateToBillId: String? = null,
    val totalMonthly: Double = 0.0,
    val categoryTotals: Map<BillGeneralCategory, Double> = emptyMap(),
    val totalAnnual: Double = 0.0,
    val annualCategoryTotals: Map<BillGeneralCategory, Double> = emptyMap(),
    val isSaving: Boolean = false,
    val message: String = "",
    /** Bills due in the next 7 days or overdue (all unpaid bills in that window). Credit/loan first, then by due date. */
    val billsDueInNext7Days: List<BillWithSource> = emptyList(),
    /** Other bills grouped by general category (excluding those in billsDueInNext7Days for the "by category" list). */
    val otherBillsByCategory: Map<BillGeneralCategory, List<BillWithSource>> = emptyMap(),
    /** Autopay bills whose due date has passed (for "were these paid?" prompt). */
    val pastDueAutopayBills: List<BillWithSource> = emptyList(),
    val showPastDueAutopayDialog: Boolean = false,
    /** Credit/loan payment dialog: item to record payment for, then amount + optional new balance. */
    val showCreditLoanPaymentDialog: BillWithSource? = null
)

@HiltViewModel
class BillsViewModel @Inject constructor(
    private val repository: BillRepository,
    private val cypherLogSubscriptionRepository: CypherLogSubscriptionRepository,
    private val creditAccountRepository: CreditAccountRepository,
    private val bankAccountRepository: BankAccountRepository,
    private val billerRepository: BillerRepository,
    private val nostrClient: NostrClient
) : ViewModel() {

    private val _state = MutableStateFlow(BillsState())
    val state: StateFlow<BillsState> = _state.asStateFlow()
    private val monthAnchor = MutableStateFlow(System.currentTimeMillis())

    init {
        viewModelScope.launch {
            runCatching { repository.backfillLegacyCreditLoanPayments() }
        }
        startMonthAnchorUpdates()
        viewModelScope.launch {
            val baseFlow = combine(
                repository.getAllBills(),
                cypherLogSubscriptionRepository.getAllAsBills(),
                creditAccountRepository.getAllCreditAccounts(),
                bankAccountRepository.getAllBankAccounts(),
                billerRepository.getAllBillers()
            ) { nativeBills, cypherLogBills, creditAccounts, bankAccounts, billers ->
                val merged = nativeBills.map { BillWithSource(it, com.fiatlife.app.domain.model.BillSource.NATIVE, null) } +
                    cypherLogBills
                val sortedMerged = merged.sortedBy { it.bill.name.lowercase() }
                Quadruple(sortedMerged, creditAccounts, bankAccounts, billers)
            }

            combine(baseFlow, monthAnchor) { base, currentMonthAnchor ->
                base to currentMonthAnchor
            }.collect { (base, currentMonthAnchor) ->
                val (mergedBills, creditAccounts, bankAccounts, billers) = base
                val accountsById = creditAccounts.associateBy { it.id }
                val costBasisBills = mergedBills.filter { item ->
                    if (item.bill.isCancelled) return@filter false
                    if (item.bill.effectiveGeneralCategory != BillGeneralCategory.CREDIT_LOANS) return@filter true
                    val linkedId = item.bill.linkedCreditAccountId
                    if (!linkedId.isNullOrBlank()) {
                        val account = accountsById[linkedId] ?: return@filter false
                        return@filter account.currentBalance > 0.0
                    }
                    // Legacy fallback: old credit-card bills may not have linkedCreditAccountId populated.
                    val billName = item.bill.name.trim()
                    val matched = creditAccounts.firstOrNull { acc ->
                        acc.currentBalance > 0.0 &&
                            (acc.linkedBillId == item.bill.id || acc.name.equals(billName, ignoreCase = true))
                    }
                    matched != null
                }
                val visibleBills = costBasisBills.filter { item ->
                    // Utilities are variable bills; once paid for current cycle, hide until next cycle/update.
                    !(item.bill.effectiveGeneralCategory == BillGeneralCategory.UTILITIES &&
                        item.bill.isPaidForCurrentCycle())
                }

                val allBills = costBasisBills.map { it.bill }
                val monthlyTotal = allBills.sumOf { b -> b.dueAmountInMonth(currentMonthAnchor) }
                val categoryTotals = allBills.groupBy { it.effectiveGeneralCategory }
                    .mapValues { (_, list) ->
                        list.sumOf { b -> b.dueAmountInMonth(currentMonthAnchor) }
                    }
                val annualTotal = allBills.sumOf { b -> b.dueAmountInYear(currentMonthAnchor) }
                val annualCategoryTotals = allBills.groupBy { it.effectiveGeneralCategory }
                    .mapValues { (_, list) ->
                        list.sumOf { b -> b.dueAmountInYear(currentMonthAnchor) }
                    }

                val now = System.currentTimeMillis()
                val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
                val cal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val todayStart = cal.timeInMillis
                val todayEnd = todayStart + 86_400_000L - 1
                val dueIn7Days = visibleBills.filter { item ->
                    if (item.isCypherLog || item.bill.isPaidForCurrentCycle(now)) return@filter false
                    // Credit/loan: exclude if there has been a payment in the current cycle.
                    if (item.bill.isCreditOrLoan() && item.bill.isPaidForCurrentCycle(now)) return@filter false
                    // Include overdue bills (so they appear in this section regardless of next cycle date).
                    if (item.bill.isPastDue()) return@filter true
                    // Include any bill due in the next 7 days (autopay or not).
                    val nextDue = item.bill.nextDueDateMillis()
                    val inWindow = nextDue != null && nextDue <= now + sevenDaysMs
                    // Due today can appear as "lastDueDate" depending on recurrence calculations.
                    val dueToday = item.bill.lastDueDateMillis()?.let { it in todayStart..todayEnd } == true
                    inWindow || dueToday
                }.sortedWith(
                    compareBy<BillWithSource> { !it.bill.isCreditOrLoan() }
                        .thenBy { it.bill.nextDueDateMillis() ?: Long.MAX_VALUE }
                )
                val dueIn7Ids = dueIn7Days.map { it.id }.toSet()
                val otherByCategory = visibleBills
                    .filter { it.id !in dueIn7Ids }
                    .groupBy { it.bill.effectiveGeneralCategory }
                    .mapValues { (_, list) -> list.sortedWith(billDueSoonestComparator()) }

                val pastDueAutopay = visibleBills.filter { item ->
                    !item.isCypherLog && item.bill.autoPay && item.bill.isPastDue()
                }

                val bankTotals = mutableMapOf<String, Double>()
                val creditTotals = mutableMapOf<String, Double>()
                val annualBankTotals = mutableMapOf<String, Double>()
                val annualCreditTotals = mutableMapOf<String, Double>()
                costBasisBills.forEach { item ->
                    val monthly = item.bill.dueAmountInMonth(currentMonthAnchor)
                    val annual = item.bill.dueAmountInYear(currentMonthAnchor)
                    item.bill.payFromBankAccountId?.let { id ->
                        bankTotals[id] = (bankTotals[id] ?: 0.0) + monthly
                        annualBankTotals[id] = (annualBankTotals[id] ?: 0.0) + annual
                    }
                    item.bill.payFromCreditAccountId?.let { id ->
                        creditTotals[id] = (creditTotals[id] ?: 0.0) + monthly
                        annualCreditTotals[id] = (annualCreditTotals[id] ?: 0.0) + annual
                    }
                }
                val breakdown = buildList {
                    bankAccounts.forEach { acc ->
                        (bankTotals[acc.id] ?: 0.0).takeIf { it > 0 }?.let { total ->
                            add(PaymentBreakdownRow(acc.id, acc.name, false, total))
                        }
                    }
                    creditAccounts.forEach { acc ->
                        (creditTotals[acc.id] ?: 0.0).takeIf { it > 0 }?.let { total ->
                            add(PaymentBreakdownRow(acc.id, acc.name, true, total))
                        }
                    }
                }
                val annualBreakdown = buildList {
                    bankAccounts.forEach { acc ->
                        (annualBankTotals[acc.id] ?: 0.0).takeIf { it > 0 }?.let { total ->
                            add(PaymentBreakdownRow(acc.id, acc.name, false, total))
                        }
                    }
                    creditAccounts.forEach { acc ->
                        (annualCreditTotals[acc.id] ?: 0.0).takeIf { it > 0 }?.let { total ->
                            add(PaymentBreakdownRow(acc.id, acc.name, true, total))
                        }
                    }
                }
                val subtotalBanks = breakdown.filter { !it.isCredit }.sumOf { it.total }
                val subtotalCredit = breakdown.filter { it.isCredit }.sumOf { it.total }
                val annualSubtotalBanks = annualBreakdown.filter { !it.isCredit }.sumOf { it.total }
                val annualSubtotalCredit = annualBreakdown.filter { it.isCredit }.sumOf { it.total }

                _state.update { state ->
                    state.copy(
                        bills = visibleBills,
                        filteredBills = filterBills(visibleBills, state.selectedGeneralCategory),
                        creditAccounts = creditAccounts,
                        bankAccounts = bankAccounts,
                        billers = billers,
                        paymentBreakdown = breakdown,
                        paymentSubtotalBanks = subtotalBanks,
                        paymentSubtotalCredit = subtotalCredit,
                        annualPaymentBreakdown = annualBreakdown,
                        annualPaymentSubtotalBanks = annualSubtotalBanks,
                        annualPaymentSubtotalCredit = annualSubtotalCredit,
                        totalMonthly = monthlyTotal,
                        categoryTotals = categoryTotals,
                        totalAnnual = annualTotal,
                        annualCategoryTotals = annualCategoryTotals,
                        billsDueInNext7Days = dueIn7Days,
                        otherBillsByCategory = otherByCategory,
                        pastDueAutopayBills = pastDueAutopay
                    )
                }
            }
        }
        syncOnConnect()
    }

    private fun startMonthAnchorUpdates() {
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                monthAnchor.value = now
                delay(millisUntilNextMonth(now))
            }
        }
    }

    private fun millisUntilNextMonth(now: Long): Long {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            add(java.util.Calendar.MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return (cal.timeInMillis - now).coerceAtLeast(60_000L)
    }

    private fun syncOnConnect() {
        viewModelScope.launch {
            nostrClient.connectionState
                .filter { it }
                .distinctUntilChanged()
                .collect {
                    try {
                        repository.syncFromNostr()
                        cypherLogSubscriptionRepository.syncFromRelay()
                    } catch (_: Exception) { }
                }
        }
    }

    fun filterByGeneralCategory(generalCategory: BillGeneralCategory?) {
        _state.update {
            it.copy(
                selectedGeneralCategory = generalCategory,
                filteredBills = filterBills(it.bills, generalCategory)
            )
        }
    }

    fun showAddBill() {
        _state.update {
            it.copy(
                showAddDialog = true,
                editingBill = null,
                editingIsCypherLog = false,
                editingPreservedTags = null,
                dialogStatementEntries = emptyList()
            )
        }
    }

    fun showEditBill(item: BillWithSource) {
        _state.update {
            it.copy(
                showAddDialog = true,
                editingBill = item.bill,
                editingIsCypherLog = item.isCypherLog,
                editingPreservedTags = item.preservedTags,
                dialogStatementEntries = item.bill.statementEntries
            )
        }
    }

    fun dismissDialog() {
        _state.update {
            it.copy(
                showAddDialog = false,
                editingBill = null,
                editingIsCypherLog = false,
                editingPreservedTags = null,
                dialogStatementEntries = emptyList()
            )
        }
    }

    fun clearNavigateToBillId() {
        _state.update { it.copy(navigateToBillId = null) }
    }

    /**
     * Save bill. For new subscription, [showInCypherLog] chooses 37004 vs 30078.
     * When editing a CypherLog item, we use [editingPreservedTags] for round-trip.
     */
    fun saveBill(bill: Bill, showInCypherLog: Boolean? = null) {
        viewModelScope.launch {
            val current = _state.value
            val merged = bill.copy(statementEntries = bill.statementEntries + current.dialogStatementEntries)
            val isCypherLog = showInCypherLog ?: current.editingIsCypherLog
            val preservedTags = current.editingPreservedTags

            _state.update { it.copy(isSaving = true) }
            try {
                if (isCypherLog) {
                    val billWithId = if (merged.id.isEmpty()) merged.copy(id = java.util.UUID.randomUUID().toString()) else merged
                    val result = cypherLogSubscriptionRepository.saveSubscriptionDetailed(billWithId, preservedTags)
                    if (result.success) {
                        _state.update {
                            it.copy(
                                isSaving = false,
                                showAddDialog = false,
                                editingBill = null,
                                editingIsCypherLog = false,
                                editingPreservedTags = null,
                                dialogStatementEntries = emptyList(),
                                navigateToBillId = billWithId.id,
                                message = "CypherLog subscription saved."
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                isSaving = false,
                                message = "CypherLog update failed: ${result.reason}"
                            )
                        }
                    }
                } else {
                    val previousBill = current.editingBill
                    val reconciled = reconcileNativeBillerForSave(merged, previousBill)
                    val saved = repository.saveBill(reconciled)
                    val newLinkedId = saved.linkedCreditAccountId
                    val oldLinkedId = previousBill?.linkedCreditAccountId
                    if (oldLinkedId != null && oldLinkedId != newLinkedId) {
                        creditAccountRepository.getCreditAccountById(oldLinkedId).first()?.let { acc ->
                            creditAccountRepository.saveCreditAccount(acc.copy(linkedBillId = null))
                        }
                    }
                    if (newLinkedId != null) {
                        creditAccountRepository.getCreditAccountById(newLinkedId).first()?.let { acc ->
                            creditAccountRepository.saveCreditAccount(acc.copy(linkedBillId = saved.id))
                        }
                    }
                    previousBill?.linkedBillerId
                        ?.takeIf { it != saved.linkedBillerId }
                        ?.let { oldBillerId ->
                            billerRepository.unlinkIfLinkedToBill(oldBillerId, saved.id)
                        }
                    saved.linkedBillerId?.let { billerId ->
                        billerRepository.linkToBill(billerId, saved.id)
                    }
                    _state.update {
                        it.copy(
                            isSaving = false,
                            showAddDialog = false,
                            editingBill = null,
                            editingIsCypherLog = false,
                            editingPreservedTags = null,
                            dialogStatementEntries = emptyList(),
                            navigateToBillId = saved.id
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, message = "Error: ${e.message}") }
            }
        }
    }

    fun deleteBill(item: BillWithSource) {
        viewModelScope.launch {
            if (item.isCypherLog) {
                cypherLogSubscriptionRepository.deleteSubscription(item.bill.id)
            } else {
                item.bill.linkedBillerId?.let { billerId ->
                    billerRepository.unlinkIfLinkedToBill(billerId, item.bill.id)
                }
                repository.deleteBill(item.bill)
            }
        }
    }

    /** Record a payment: add to history with amount and date, mark paid, and for credit cards reduce balance. */
    fun recordPayment(item: BillWithSource) {
        if (item.bill.isCreditOrLoan()) {
            _state.update { it.copy(showCreditLoanPaymentDialog = item) }
            return
        }
        viewModelScope.launch {
            recordPaymentInternal(item, item.bill.effectiveAmountDue(), null)
        }
    }

    /** Record payment for credit/loan with optional amount and new balance. If newBalance is null, amount is subtracted from current balance. */
    fun recordCreditLoanPayment(item: BillWithSource, amount: Double, newBalance: Double?) {
        viewModelScope.launch {
            recordPaymentInternal(item, amount, newBalance)
            _state.update { it.copy(showCreditLoanPaymentDialog = null) }
        }
    }

    fun dismissCreditLoanPaymentDialog() {
        _state.update { it.copy(showCreditLoanPaymentDialog = null) }
    }

    private suspend fun recordPaymentInternal(item: BillWithSource, amount: Double, newBalance: Double?) {
        val bill = item.bill
        val payment = BillPayment(date = System.currentTimeMillis(), amount = amount)
        val updatedCcDetails = bill.creditCardDetails?.let { cc ->
            val balance = newBalance ?: (cc.currentBalance - amount).coerceAtLeast(0.0)
            cc.copy(currentBalance = balance)
        }
        val updatedBill = bill.copy(
            paymentHistory = bill.paymentHistory + payment,
            isPaid = true,
            lastPaidDate = payment.date,
            creditCardDetails = updatedCcDetails
        )
        if (item.isCypherLog) {
            val result = cypherLogSubscriptionRepository.saveSubscriptionDetailed(updatedBill, item.preservedTags)
            if (!result.success) {
                _state.update { it.copy(message = "CypherLog payment log failed: ${result.reason}") }
            }
        } else {
            repository.saveBill(updatedBill)
            bill.linkedCreditAccountId?.let { accountId ->
                creditAccountRepository.getCreditAccountById(accountId).first()?.let { acc ->
                    val balance = newBalance ?: (acc.currentBalance - amount).coerceAtLeast(0.0)
                    creditAccountRepository.saveCreditAccount(acc.copy(currentBalance = balance))
                }
            }
        }
    }

    /** Mark selected past-due autopay bills as paid (logs payment, updates next due via isPaid/lastPaidDate). */
    fun markPastDueAsPaid(selected: List<BillWithSource>) {
        viewModelScope.launch {
            selected.forEach { item ->
                if (item.isCypherLog) return@forEach
                recordPaymentInternal(item, item.bill.effectiveAmountDue(), null)
            }
            _state.update { it.copy(showPastDueAutopayDialog = false) }
        }
    }

    fun showPastDueAutopayDialogIfNeeded() {
        val pastDue = _state.value.pastDueAutopayBills
        if (pastDue.isNotEmpty()) _state.update { it.copy(showPastDueAutopayDialog = true) }
    }

    fun dismissPastDueAutopayDialog() {
        _state.update { it.copy(showPastDueAutopayDialog = false) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = "") }
    }

    fun uploadAttachment(data: ByteArray, contentType: String, filename: String) {
        viewModelScope.launch {
            repository.uploadAttachment(data, contentType, filename)
                .onSuccess { hash ->
                    val entry = StatementEntry(hash = hash, addedAt = System.currentTimeMillis(), label = filename)
                    _state.update { it.copy(dialogStatementEntries = it.dialogStatementEntries + entry) }
                }
                .onFailure { e ->
                    _state.update { it.copy(message = "Upload failed: ${e.message}") }
                }
        }
    }

    private fun filterBills(bills: List<BillWithSource>, generalCategory: BillGeneralCategory?): List<BillWithSource> {
        return if (generalCategory == null) bills
        else bills
            .filter { it.bill.effectiveGeneralCategory == generalCategory }
            .sortedWith(billDueSoonestComparator())
    }

    private fun billDueSortKey(item: BillWithSource): Long {
        val bill = item.bill
        return if (bill.isPastDue()) {
            bill.lastDueDateMillis() ?: Long.MAX_VALUE
        } else {
            bill.nextDueDateMillis() ?: Long.MAX_VALUE
        }
    }

    private fun billDueSoonestComparator(): Comparator<BillWithSource> =
        compareBy<BillWithSource> { billDueSortKey(it) }
            .thenBy { it.bill.name.lowercase() }

    private suspend fun reconcileNativeBillerForSave(incoming: Bill, previous: Bill?): Bill {
        val requestedName = incoming.billerName.trim()
        if (requestedName.isBlank()) {
            previous?.linkedBillerId?.let { oldId ->
                billerRepository.unlinkIfLinkedToBill(oldId, incoming.id)
            }
            return incoming.copy(linkedBillerId = null, billerName = "")
        }
        val existingById = incoming.linkedBillerId?.let { billerRepository.getById(it) }
        val finalBiller = if (existingById != null &&
            billerRepository.normalize(existingById.name) == billerRepository.normalize(requestedName)
        ) {
            if (existingById.name != requestedName) {
                billerRepository.saveBiller(existingById.copy(name = requestedName))
            } else existingById
        } else {
            billerRepository.getOrCreateByName(requestedName)
        }
        return incoming.copy(
            linkedBillerId = finalBiller.id,
            billerName = finalBiller.name
        ).let { candidate ->
            // New entry + existing linked bill for this company -> upsert the existing record.
            if (incoming.id.isNotBlank()) return@let candidate
            val linkedBillId = finalBiller.linkedBillId ?: return@let candidate
            val existing = repository.getBillById(linkedBillId).first() ?: return@let candidate
            candidate.copy(
                id = existing.id,
                createdAt = existing.createdAt,
                paymentHistory = existing.paymentHistory,
                statementEntries = existing.statementEntries,
                attachmentHashes = existing.attachmentHashes,
                // New statement/bill cycle should surface as currently due/unpaid.
                isPaid = false
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
