package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.repository.BankAccountRepository
import com.fiatlife.app.data.repository.BitcoinWalletRepository
import com.fiatlife.app.data.repository.BtcPriceRepository
import com.fiatlife.app.data.repository.CreditAccountRepository
import com.fiatlife.app.data.repository.SimpleFinRefresh
import com.fiatlife.app.data.repository.SimpleFinRepository
import com.fiatlife.app.data.repository.stateWhileSubscribed
import com.fiatlife.app.domain.model.BankAccount
import com.fiatlife.app.domain.model.BitcoinWallet
import com.fiatlife.app.domain.model.BtcPrice
import com.fiatlife.app.domain.model.CreditAccount
import com.fiatlife.app.domain.model.NetWorthSummary
import com.fiatlife.app.domain.model.computeNetWorth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    private val bankAccountRepository: BankAccountRepository,
    private val creditAccountRepository: CreditAccountRepository,
    private val walletRepository: BitcoinWalletRepository,
    private val priceRepository: BtcPriceRepository,
    private val simpleFinRepository: SimpleFinRepository
) : ViewModel() {

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Outcome of the last refresh's SimpleFIN step, when there is something to say. */
    private val _refreshMessage = MutableStateFlow<String?>(null)
    val refreshMessage: StateFlow<String?> = _refreshMessage.asStateFlow()

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

    /** BTC price, accounts and wallets from the relay, then bank balances from SimpleFIN. */
    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            _refreshMessage.value = null
            try {
                listOf(
                    async { priceRepository.refresh(force = true) },
                    async { bankAccountRepository.syncFromNostr() },
                    async { creditAccountRepository.syncFromNostr() },
                    async { walletRepository.syncFromNostr() }
                ).awaitAll()
                _refreshMessage.value = describe(simpleFinRepository.refresh())
            } finally {
                _refreshing.value = false
            }
        }
    }

    private fun describe(result: SimpleFinRefresh): String? = when (result) {
        SimpleFinRefresh.NotConnected -> {
            val linked = state.value.bankAccounts.any { it.simplefinAccountKey != null } ||
                state.value.creditAccounts.any { it.simplefinAccountKey != null }
            if (linked) {
                "Open the web app once so it can share its SimpleFIN connection with this device."
            } else null
        }
        is SimpleFinRefresh.Recent -> {
            val minutesAgo = ((System.currentTimeMillis() - result.lastFetchedAtMs) / 60_000).coerceAtLeast(0)
            "SimpleFIN was checked $minutesAgo min ago. It allows one check an hour, " +
                "so balances are current as of then."
        }
        is SimpleFinRefresh.Fetched -> buildString {
            append(
                when (result.updatedAccounts) {
                    0 -> if (result.staleSinceMs == null) "Bank balances are up to date."
                    else "SimpleFIN returned no new balances."
                    1 -> "Updated 1 balance from SimpleFIN."
                    else -> "Updated ${result.updatedAccounts} balances from SimpleFIN."
                }
            )
            result.staleSinceMs?.let {
                val days = (System.currentTimeMillis() - it) / 86_400_000
                append("\nSimpleFIN hasn't had new balances from your bank in $days days. ")
                append("Check the connection at bridge.simplefin.org; the bank may need you to sign in again.")
            }
            if (result.unmatchedAccounts.isNotEmpty()) {
                append("\n${result.unmatchedAccounts.joinToString()} ")
                append(if (result.unmatchedAccounts.size == 1) "is" else "are")
                append(" linked to a SimpleFIN account that wasn't returned. Link again in the web app's Settings.")
            }
            result.messages.forEach { append("\n").append(it) }
        }
        is SimpleFinRefresh.Failed -> result.message
    }

    fun saveWallet(wallet: BitcoinWallet) {
        viewModelScope.launch { walletRepository.saveWallet(wallet) }
    }

    fun deleteWallet(wallet: BitcoinWallet) {
        viewModelScope.launch { walletRepository.deleteWallet(wallet) }
    }
}
