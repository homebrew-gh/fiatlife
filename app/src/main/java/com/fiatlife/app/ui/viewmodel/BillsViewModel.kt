package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.repository.BankAccountRepository
import com.fiatlife.app.data.repository.BillRepository
import com.fiatlife.app.data.repository.BillerRepository
import com.fiatlife.app.data.repository.CreditAccountRepository
import com.fiatlife.app.data.repository.CypherLogSubscriptionRepository
import com.fiatlife.app.domain.model.BankAccount
import com.fiatlife.app.domain.model.Bill
import com.fiatlife.app.domain.model.CreditAccount
import com.fiatlife.app.domain.model.CreditStatementUpdate
import com.fiatlife.app.domain.model.Biller
import com.fiatlife.app.domain.model.BillGeneralCategory
import com.fiatlife.app.domain.model.BillSubcategory
import com.fiatlife.app.domain.model.BillPayment
import com.fiatlife.app.domain.model.BillWithSource
import com.fiatlife.app.domain.model.StatementEntry
import com.fiatlife.app.data.repository.stateWhileSubscribed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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

data class SubscriptionSubcategoryGroup(
    val subcategory: BillSubcategory,
    val bills: List<BillWithSource>,
    val subtotal: Double
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
    val showCreditLoanPaymentDialog: BillWithSource? = null,
    /** Precomputed display fields for bill list cards (built off the main thread). */
    val billCardById: Map<String, BillCardUiModel> = emptyMap(),
    /** Subscription bills grouped and sorted off the main thread. */
    val subscriptionGroups: List<SubscriptionSubcategoryGroup> = emptyList()
)

@HiltViewModel
class BillsViewModel @Inject constructor(
    private val repository: BillRepository,
    private val cypherLogSubscriptionRepository: CypherLogSubscriptionRepository,
    private val creditAccountRepository: CreditAccountRepository,
    private val bankAccountRepository: BankAccountRepository,
    private val billerRepository: BillerRepository
) : ViewModel() {

    private val monthAnchor = MutableStateFlow(System.currentTimeMillis())
    private val uiOverlay = MutableStateFlow(BillsUiOverlay())

    val state: StateFlow<BillsState> = run {
        MonthAnchor.startUpdates(viewModelScope, monthAnchor)
        val baseFlow = combine(
            repository.getAllBills(),
            cypherLogSubscriptionRepository.getAllAsBills(),
            creditAccountRepository.getAllCreditAccounts(),
            bankAccountRepository.getAllBankAccounts(),
            billerRepository.getAllBillers()
        ) { nativeBills, cypherLogBills, creditAccounts, bankAccounts, billers ->
            val merged = nativeBills.map { BillWithSource(it, com.fiatlife.app.domain.model.BillSource.NATIVE, null) } +
                cypherLogBills
            BillsRepoInputs(merged.sortedBy { it.bill.name.lowercase() }, creditAccounts, bankAccounts, billers)
        }

        combine(
            combine(baseFlow, monthAnchor) { base, anchor -> base to anchor }
                .map { (base, anchor) -> buildBillsComputed(base, anchor) }
                .flowOn(Dispatchers.Default)
                .distinctUntilChanged(),
            uiOverlay
        ) { computed, ui -> mergeBillsState(computed, ui) }
            .stateWhileSubscribed(viewModelScope, BillsState())
    }

    init {
        viewModelScope.launch {
            runCatching { repository.backfillLegacyCreditLoanPayments() }
        }
    }

    fun filterByGeneralCategory(generalCategory: BillGeneralCategory?) {
        uiOverlay.update { it.copy(selectedGeneralCategory = generalCategory) }
    }

    fun showAddBill() {
        uiOverlay.update {
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
        uiOverlay.update {
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
        uiOverlay.update {
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
        uiOverlay.update { it.copy(navigateToBillId = null) }
    }

    /**
     * Save bill. For new subscription, [showInCypherLog] chooses 37004 vs 30078.
     * When editing a CypherLog item, we use [editingPreservedTags] for round-trip.
     */
    fun saveBill(bill: Bill, showInCypherLog: Boolean? = null) {
        viewModelScope.launch {
            val current = state.value
            val merged = bill.copy(statementEntries = bill.statementEntries + current.dialogStatementEntries)
            val isCypherLog = showInCypherLog ?: current.editingIsCypherLog
            val preservedTags = current.editingPreservedTags

            uiOverlay.update { it.copy(isSaving = true) }
            try {
                if (isCypherLog) {
                    val billWithId = if (merged.id.isEmpty()) merged.copy(id = java.util.UUID.randomUUID().toString()) else merged
                    val result = cypherLogSubscriptionRepository.saveSubscriptionDetailed(billWithId, preservedTags)
                    if (result.success) {
                        uiOverlay.update {
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
                        uiOverlay.update {
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
                    uiOverlay.update {
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
                uiOverlay.update { it.copy(isSaving = false, message = "Error: ${e.message}") }
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
            uiOverlay.update { it.copy(showCreditLoanPaymentDialog = item) }
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
            uiOverlay.update { it.copy(showCreditLoanPaymentDialog = null) }
        }
    }

    fun dismissCreditLoanPaymentDialog() {
        uiOverlay.update { it.copy(showCreditLoanPaymentDialog = null) }
    }

    fun updateStatement(account: CreditAccount, update: CreditStatementUpdate) {
        viewModelScope.launch {
            uiOverlay.update { it.copy(isSaving = true) }
            try {
                creditAccountRepository.updateStatement(account, update)
                uiOverlay.update { it.copy(isSaving = false) }
            } catch (e: Exception) {
                uiOverlay.update {
                    it.copy(isSaving = false, message = "Statement update failed: ${e.message}")
                }
            }
        }
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
                uiOverlay.update { it.copy(message = "CypherLog payment log failed: ${result.reason}") }
            }
        } else {
            repository.saveBill(updatedBill)
            bill.linkedCreditAccountId?.let { accountId ->
                creditAccountRepository.getCreditAccountById(accountId).first()?.let { acc ->
                    val balance = newBalance ?: (acc.currentBalance - amount).coerceAtLeast(0.0)
                    creditAccountRepository.saveCreditAccount(
                        acc.copy(currentBalance = balance),
                        inferPayment = false
                    )
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
            uiOverlay.update { it.copy(showPastDueAutopayDialog = false) }
        }
    }

    fun showPastDueAutopayDialogIfNeeded() {
        val pastDue = state.value.pastDueAutopayBills
        if (pastDue.isNotEmpty()) uiOverlay.update { it.copy(showPastDueAutopayDialog = true) }
    }

    fun dismissPastDueAutopayDialog() {
        uiOverlay.update { it.copy(showPastDueAutopayDialog = false) }
    }

    fun clearMessage() {
        uiOverlay.update { it.copy(message = "") }
    }

    fun uploadAttachment(data: ByteArray, contentType: String, filename: String) {
        viewModelScope.launch {
            repository.uploadAttachment(data, contentType, filename)
                .onSuccess { hash ->
                    val entry = StatementEntry(hash = hash, addedAt = System.currentTimeMillis(), label = filename)
                    uiOverlay.update { it.copy(dialogStatementEntries = it.dialogStatementEntries + entry) }
                }
                .onFailure { e ->
                    uiOverlay.update { it.copy(message = "Upload failed: ${e.message}") }
                }
        }
    }

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

private data class BillsRepoInputs(
    val mergedBills: List<BillWithSource>,
    val creditAccounts: List<CreditAccount>,
    val bankAccounts: List<BankAccount>,
    val billers: List<Biller>
)

private data class BillsComputed(
    val bills: List<BillWithSource> = emptyList(),
    val creditAccounts: List<CreditAccount> = emptyList(),
    val bankAccounts: List<BankAccount> = emptyList(),
    val billers: List<Biller> = emptyList(),
    val paymentBreakdown: List<PaymentBreakdownRow> = emptyList(),
    val paymentSubtotalBanks: Double = 0.0,
    val paymentSubtotalCredit: Double = 0.0,
    val annualPaymentBreakdown: List<PaymentBreakdownRow> = emptyList(),
    val annualPaymentSubtotalBanks: Double = 0.0,
    val annualPaymentSubtotalCredit: Double = 0.0,
    val totalMonthly: Double = 0.0,
    val categoryTotals: Map<BillGeneralCategory, Double> = emptyMap(),
    val totalAnnual: Double = 0.0,
    val annualCategoryTotals: Map<BillGeneralCategory, Double> = emptyMap(),
    val billsDueInNext7Days: List<BillWithSource> = emptyList(),
    val otherBillsByCategory: Map<BillGeneralCategory, List<BillWithSource>> = emptyMap(),
    val pastDueAutopayBills: List<BillWithSource> = emptyList(),
    val billCardById: Map<String, BillCardUiModel> = emptyMap(),
    val subscriptionGroups: List<SubscriptionSubcategoryGroup> = emptyList()
)

private data class BillsUiOverlay(
    val selectedGeneralCategory: BillGeneralCategory? = null,
    val showAddDialog: Boolean = false,
    val editingBill: Bill? = null,
    val editingIsCypherLog: Boolean = false,
    val editingPreservedTags: Map<String, List<String>>? = null,
    val dialogStatementEntries: List<StatementEntry> = emptyList(),
    val navigateToBillId: String? = null,
    val isSaving: Boolean = false,
    val message: String = "",
    val showPastDueAutopayDialog: Boolean = false,
    val showCreditLoanPaymentDialog: BillWithSource? = null
)

private fun mergeBillsState(computed: BillsComputed, ui: BillsUiOverlay): BillsState =
    BillsState(
        bills = computed.bills,
        filteredBills = filterBills(computed.bills, ui.selectedGeneralCategory),
        creditAccounts = computed.creditAccounts,
        bankAccounts = computed.bankAccounts,
        billers = computed.billers,
        paymentBreakdown = computed.paymentBreakdown,
        paymentSubtotalBanks = computed.paymentSubtotalBanks,
        paymentSubtotalCredit = computed.paymentSubtotalCredit,
        annualPaymentBreakdown = computed.annualPaymentBreakdown,
        annualPaymentSubtotalBanks = computed.annualPaymentSubtotalBanks,
        annualPaymentSubtotalCredit = computed.annualPaymentSubtotalCredit,
        selectedGeneralCategory = ui.selectedGeneralCategory,
        showAddDialog = ui.showAddDialog,
        editingBill = ui.editingBill,
        editingIsCypherLog = ui.editingIsCypherLog,
        editingPreservedTags = ui.editingPreservedTags,
        dialogStatementEntries = ui.dialogStatementEntries,
        navigateToBillId = ui.navigateToBillId,
        totalMonthly = computed.totalMonthly,
        categoryTotals = computed.categoryTotals,
        totalAnnual = computed.totalAnnual,
        annualCategoryTotals = computed.annualCategoryTotals,
        isSaving = ui.isSaving,
        message = ui.message,
        billsDueInNext7Days = computed.billsDueInNext7Days,
        otherBillsByCategory = computed.otherBillsByCategory,
        pastDueAutopayBills = computed.pastDueAutopayBills,
        showPastDueAutopayDialog = ui.showPastDueAutopayDialog,
        showCreditLoanPaymentDialog = ui.showCreditLoanPaymentDialog,
        billCardById = computed.billCardById,
        subscriptionGroups = computed.subscriptionGroups
    )

private fun buildBillsComputed(base: BillsRepoInputs, currentMonthAnchor: Long): BillsComputed {
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
        val billName = item.bill.name.trim()
        val matched = creditAccounts.firstOrNull { acc ->
            acc.currentBalance > 0.0 &&
                (acc.linkedBillId == item.bill.id || acc.name.equals(billName, ignoreCase = true))
        }
        matched != null
    }
    val visibleBills = costBasisBills.filter { item ->
        !(item.bill.effectiveGeneralCategory == BillGeneralCategory.UTILITIES &&
            item.bill.isPaidForCurrentCycle())
    }

    val allBills = costBasisBills.map { it.bill }
    val monthlyTotal = allBills.sumOf { b -> b.dueAmountInMonth(currentMonthAnchor) }
    val categoryTotals = allBills.groupBy { it.effectiveGeneralCategory }
        .mapValues { (_, list) -> list.sumOf { b -> b.dueAmountInMonth(currentMonthAnchor) } }
    val annualTotal = allBills.sumOf { b -> b.dueAmountInYear(currentMonthAnchor) }
    val annualCategoryTotals = allBills.groupBy { it.effectiveGeneralCategory }
        .mapValues { (_, list) -> list.sumOf { b -> b.dueAmountInYear(currentMonthAnchor) } }

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
        if (item.bill.isCreditOrLoan() && item.bill.isPaidForCurrentCycle(now)) return@filter false
        if (item.bill.isPastDue()) return@filter true
        val nextDue = item.bill.nextDueDateMillis()
        val inWindow = nextDue != null && nextDue <= now + sevenDaysMs
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

    val subscriptionGroups = otherByCategory[BillGeneralCategory.SUBSCRIPTION]
        .orEmpty()
        .groupBy { it.bill.effectiveSubcategory }
        .toList()
        .sortedBy { it.first.displayName }
        .map { (subcategory, subBills) ->
            val sorted = subBills.sortedWith(billDueSoonestComparator())
            SubscriptionSubcategoryGroup(
                subcategory = subcategory,
                bills = sorted,
                subtotal = sorted.sumOf { it.bill.effectiveAmountDue() }
            )
        }
    val otherByCategoryExcludingSubscriptions = otherByCategory.filterKeys {
        it != BillGeneralCategory.SUBSCRIPTION
    }

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

    val billCardById = visibleBills.associate { item ->
        item.id to buildBillCardUiModel(item, accountsById, now)
    }

    return BillsComputed(
        bills = visibleBills,
        creditAccounts = creditAccounts,
        bankAccounts = bankAccounts,
        billers = billers,
        billCardById = billCardById,
        paymentBreakdown = breakdown,
        paymentSubtotalBanks = breakdown.filter { !it.isCredit }.sumOf { it.total },
        paymentSubtotalCredit = breakdown.filter { it.isCredit }.sumOf { it.total },
        annualPaymentBreakdown = annualBreakdown,
        annualPaymentSubtotalBanks = annualBreakdown.filter { !it.isCredit }.sumOf { it.total },
        annualPaymentSubtotalCredit = annualBreakdown.filter { it.isCredit }.sumOf { it.total },
        totalMonthly = monthlyTotal,
        categoryTotals = categoryTotals,
        totalAnnual = annualTotal,
        annualCategoryTotals = annualCategoryTotals,
        billsDueInNext7Days = dueIn7Days,
        otherBillsByCategory = otherByCategoryExcludingSubscriptions,
        pastDueAutopayBills = pastDueAutopay,
        subscriptionGroups = subscriptionGroups
    )
}

private fun filterBills(bills: List<BillWithSource>, generalCategory: BillGeneralCategory?): List<BillWithSource> =
    if (generalCategory == null) bills
    else bills
        .filter { it.bill.effectiveGeneralCategory == generalCategory }
        .sortedWith(billDueSoonestComparator())

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
