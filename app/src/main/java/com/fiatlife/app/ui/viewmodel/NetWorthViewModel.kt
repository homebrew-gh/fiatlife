package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.repository.BankAccountRepository
import com.fiatlife.app.data.repository.BitcoinWalletRepository
import com.fiatlife.app.data.repository.BtcPriceRepository
import com.fiatlife.app.data.repository.CreditAccountRepository
import com.fiatlife.app.data.repository.stateWhileSubscribed
import com.fiatlife.app.domain.model.BankAccount
import com.fiatlife.app.domain.model.BitcoinWallet
import com.fiatlife.app.domain.model.BtcPrice
import com.fiatlife.app.domain.model.CreditAccount
import com.fiatlife.app.domain.model.NetWorthSummary
import com.fiatlife.app.domain.model.computeNetWorth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NetWorthState(
    val bankAccounts: List<BankAccount> = emptyList(),
    val wallets: List<BitcoinWallet> = emptyList(),
    val creditAccounts: List<CreditAccount> = emptyList(),
    val price: BtcPrice? = null,
    val priceError: String? = null,
    val summary: NetWorthSummary = computeNetWorth(emptyList(), emptyList(), null, emptyList()),
    val loading: Boolean = true
)

@HiltViewModel
class NetWorthViewModel @Inject constructor(
    bankAccountRepository: BankAccountRepository,
    creditAccountRepository: CreditAccountRepository,
    private val walletRepository: BitcoinWalletRepository,
    private val priceRepository: BtcPriceRepository
) : ViewModel() {

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    val state: StateFlow<NetWorthState> = combine(
        bankAccountRepository.getAllBankAccounts(),
        walletRepository.getAllWallets(),
        creditAccountRepository.getAllCreditAccounts(),
        priceRepository.price,
        priceRepository.error
    ) { bank, wallets, credit, price, priceError ->
        NetWorthState(
            bankAccounts = bank,
            wallets = wallets,
            creditAccounts = credit,
            price = price,
            priceError = priceError,
            summary = computeNetWorth(bank, wallets, price?.usd, credit),
            loading = false
        )
    }.stateWhileSubscribed(viewModelScope, NetWorthState())

    init {
        viewModelScope.launch { priceRepository.refresh() }
    }

    fun refreshPrice() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                priceRepository.refresh(force = true)
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun saveWallet(wallet: BitcoinWallet) {
        viewModelScope.launch { walletRepository.saveWallet(wallet) }
    }

    fun deleteWallet(wallet: BitcoinWallet) {
        viewModelScope.launch { walletRepository.deleteWallet(wallet) }
    }
}
