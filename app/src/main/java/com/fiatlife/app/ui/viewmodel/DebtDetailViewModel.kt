package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.repository.BillRepository
import com.fiatlife.app.data.repository.CreditAccountRepository
import com.fiatlife.app.domain.model.Bill
import com.fiatlife.app.domain.model.CreditAccount
import com.fiatlife.app.domain.model.StatementEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebtDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CreditAccountRepository,
    private val billRepository: BillRepository
) : ViewModel() {

    val accountId: String = checkNotNull(savedStateHandle["accountId"]) { "accountId required" }

    val account: StateFlow<CreditAccount?> = repository.getCreditAccountById(accountId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val linkedBill: StateFlow<Bill?> = account.flatMapLatest { acc ->
        val billId = acc?.linkedBillId
        if (billId != null) billRepository.getBillById(billId) else flowOf(null)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun deleteAccount(account: CreditAccount) {
        viewModelScope.launch {
            repository.deleteCreditAccount(account)
        }
    }

    fun saveAccount(account: CreditAccount) {
        viewModelScope.launch {
            repository.saveCreditAccount(account)
        }
    }

    suspend fun getStatementBytes(hash: String): Result<ByteArray> =
        repository.downloadAttachment(hash)

    fun uploadAndAddStatement(account: CreditAccount, data: ByteArray, contentType: String, filename: String) {
        viewModelScope.launch {
            repository.uploadAttachment(data, contentType, filename).onSuccess { sha256 ->
                val now = System.currentTimeMillis()
                val entry = StatementEntry(hash = sha256, addedAt = now, label = filename)
                val updated = account.copy(
                    statementEntries = account.statementEntries + entry,
                    attachmentHashes = account.attachmentHashes + sha256
                )
                repository.saveCreditAccount(updated)
            }
        }
    }
}
