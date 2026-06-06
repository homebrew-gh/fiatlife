package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.repository.CreditAccountRepository
import com.fiatlife.app.domain.model.CreditAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DebtPlannerState(
    val accounts: List<CreditAccount> = emptyList(),
    val loading: Boolean = true
)

@HiltViewModel
class DebtPlannerViewModel @Inject constructor(
    repository: CreditAccountRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DebtPlannerState())
    val state: StateFlow<DebtPlannerState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllCreditAccounts().collect { accounts ->
                _state.update { it.copy(accounts = accounts, loading = false) }
            }
        }
    }
}
