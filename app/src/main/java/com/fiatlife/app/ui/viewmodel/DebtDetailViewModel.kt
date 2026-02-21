package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.repository.CreditAccountRepository
import com.fiatlife.app.domain.model.CreditAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebtDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CreditAccountRepository
) : ViewModel() {

    val accountId: String = checkNotNull(savedStateHandle["accountId"]) { "accountId required" }

    val account: StateFlow<CreditAccount?> = repository.getCreditAccountById(accountId)
        .stateIn(
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
}
