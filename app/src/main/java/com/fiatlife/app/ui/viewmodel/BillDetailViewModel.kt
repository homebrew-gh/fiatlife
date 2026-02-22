package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.repository.BillRepository
import com.fiatlife.app.data.repository.CreditAccountRepository
import com.fiatlife.app.data.repository.CypherLogSubscriptionRepository
import com.fiatlife.app.domain.model.Bill
import com.fiatlife.app.domain.model.BillPayment
import com.fiatlife.app.domain.model.BillSource
import com.fiatlife.app.domain.model.BillWithSource
import com.fiatlife.app.domain.model.CreditAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BillDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: BillRepository,
    private val creditAccountRepository: CreditAccountRepository,
    private val cypherLogSubscriptionRepository: CypherLogSubscriptionRepository
) : ViewModel() {

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

    fun recordPayment(bill: Bill) {
        val item = billWithSource.value ?: return
        if (item.isCypherLog) return
        if (bill.isCreditOrLoan()) return
        viewModelScope.launch {
            recordPaymentWithAmount(bill, bill.effectiveAmountDue(), null)
        }
    }

    fun recordPaymentWithAmount(bill: Bill, amount: Double, newBalance: Double?) {
        val item = billWithSource.value ?: return
        if (item.isCypherLog) return
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
            repository.saveBill(updatedBill)
            bill.linkedCreditAccountId?.let { accountId ->
                creditAccountRepository.getCreditAccountById(accountId).first()?.let { acc ->
                    val balance = newBalance ?: (acc.currentBalance - amount).coerceAtLeast(0.0)
                    creditAccountRepository.saveCreditAccount(acc.copy(currentBalance = balance))
                }
            }
        }
    }

    fun deleteBill(item: BillWithSource) {
        viewModelScope.launch {
            if (item.isCypherLog) {
                cypherLogSubscriptionRepository.deleteSubscription(item.bill.id)
            } else {
                repository.deleteBill(item.bill)
            }
        }
    }

    fun saveBill(bill: Bill) {
        val item = billWithSource.value ?: return
        viewModelScope.launch {
            if (item.isCypherLog) {
                cypherLogSubscriptionRepository.saveSubscription(bill, item.preservedTags)
            } else {
                val previousBill = item.bill
                val saved = repository.saveBill(bill)
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
            }
        }
    }

    suspend fun getStatementBytes(hash: String): Result<ByteArray> =
        repository.downloadAttachment(hash)
}
