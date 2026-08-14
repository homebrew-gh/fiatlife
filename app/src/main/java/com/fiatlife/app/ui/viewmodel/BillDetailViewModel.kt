package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.repository.BankAccountRepository
import com.fiatlife.app.data.repository.BillRepository
import com.fiatlife.app.data.repository.BillerRepository
import com.fiatlife.app.data.repository.CreditAccountRepository
import com.fiatlife.app.data.repository.CypherLogSubscriptionRepository
import com.fiatlife.app.domain.model.BankAccount
import com.fiatlife.app.domain.model.Bill
import com.fiatlife.app.domain.model.BillPayment
import com.fiatlife.app.domain.model.BillSource
import com.fiatlife.app.domain.model.BillWithSource
import com.fiatlife.app.domain.model.Biller
import com.fiatlife.app.domain.model.CreditAccount
import com.fiatlife.app.domain.model.CreditStatementUpdate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BillDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: BillRepository,
    private val creditAccountRepository: CreditAccountRepository,
    private val bankAccountRepository: BankAccountRepository,
    private val billerRepository: BillerRepository,
    private val cypherLogSubscriptionRepository: CypherLogSubscriptionRepository
) : ViewModel() {
    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    val billId: String = checkNotNull(savedStateHandle["billId"]) { "billId required" }

    private val nativeBill = repository.getBillById(billId)
    private val cypherLogBill = cypherLogSubscriptionRepository.getByDTag(billId)

    val billWithSource: StateFlow<BillWithSource?> = combine(nativeBill, cypherLogBill) { native, cypher ->
        when {
            native != null -> BillWithSource(native, BillSource.NATIVE, null)
            cypher != null -> cypher
            else -> null
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val bill: StateFlow<Bill?> = billWithSource.map { it?.bill }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val linkedCreditAccount: StateFlow<CreditAccount?> = bill.flatMapLatest { b ->
        val id = b?.linkedCreditAccountId
        if (id != null) creditAccountRepository.getCreditAccountById(id) else flowOf(null)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val creditAccounts: StateFlow<List<CreditAccount>> = creditAccountRepository.getAllCreditAccounts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val bankAccounts: StateFlow<List<BankAccount>> = bankAccountRepository.getAllBankAccounts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val billers: StateFlow<List<Biller>> = billerRepository.getAllBillers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun recordPayment(bill: Bill) {
        val item = billWithSource.value ?: return
        if (bill.isCreditOrLoan()) return
        viewModelScope.launch {
            recordPaymentWithAmount(bill, bill.effectiveAmountDue(), null)
        }
    }

    fun recordPaymentWithAmount(bill: Bill, amount: Double, newBalance: Double?) {
        val item = billWithSource.value ?: return
        viewModelScope.launch {
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
                _message.update {
                    if (result.success) "CypherLog subscription saved."
                    else "CypherLog payment log failed: ${result.reason}"
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

    fun saveBill(bill: Bill) {
        val item = billWithSource.value ?: return
        viewModelScope.launch {
            if (item.isCypherLog) {
                val result = cypherLogSubscriptionRepository.saveSubscriptionDetailed(bill, item.preservedTags)
                _message.update {
                    if (result.success) "CypherLog subscription saved."
                    else "CypherLog update failed: ${result.reason}"
                }
            } else {
                val previousBill = item.bill
                val reconciled = reconcileNativeBillerForSave(bill, previousBill)
                val saved = repository.saveBill(reconciled)
                val newLinkedId = saved.linkedCreditAccountId
                val oldLinkedId = previousBill.linkedCreditAccountId
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
                previousBill.linkedBillerId
                    ?.takeIf { it != saved.linkedBillerId }
                    ?.let { oldBillerId ->
                        billerRepository.unlinkIfLinkedToBill(oldBillerId, saved.id)
                    }
                saved.linkedBillerId?.let { billerId ->
                    billerRepository.linkToBill(billerId, saved.id)
                }
            }
        }
    }

    fun clearMessage() {
        _message.update { "" }
    }

    fun updateStatement(account: CreditAccount, update: CreditStatementUpdate) {
        viewModelScope.launch {
            try {
                creditAccountRepository.updateStatement(account, update)
            } catch (e: Exception) {
                _message.update { "Statement update failed: ${e.message}" }
            }
        }
    }

    suspend fun getStatementBytes(hash: String): Result<ByteArray> =
        repository.downloadAttachment(hash)

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
        )
    }
}
