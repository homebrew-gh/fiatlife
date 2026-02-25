package com.fiatlife.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiatlife.app.data.repository.BillRepository
import com.fiatlife.app.data.repository.BillerRepository
import com.fiatlife.app.data.repository.CypherLogSubscriptionRepository
import com.fiatlife.app.domain.model.Bill
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
    val totalPaid: Double,
    val paymentCount: Int,
    val lastPaidDate: Long
)

data class CompanyPaymentRow(
    val id: String,
    val companyKey: String,
    val companyName: String,
    val billName: String,
    val amount: Double,
    val paidDate: Long,
    val hasInvoiceOrStatement: Boolean
)

data class CompanyHistoryState(
    val companies: List<CompanyHistoryRow> = emptyList(),
    val paymentsByCompanyKey: Map<String, List<CompanyPaymentRow>> = emptyMap()
)

@HiltViewModel
class CompanyHistoryViewModel @Inject constructor(
    private val billRepository: BillRepository,
    private val cypherLogSubscriptionRepository: CypherLogSubscriptionRepository,
    private val billerRepository: BillerRepository
) : ViewModel() {
    private val _state = MutableStateFlow(CompanyHistoryState())
    val state: StateFlow<CompanyHistoryState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                billRepository.getAllBills(),
                cypherLogSubscriptionRepository.getAllAsBills(),
                billerRepository.getAllBillers()
            ) { nativeBills, cypherLogBills, billers ->
                val allBills = nativeBills + cypherLogBills.map { it.bill }
                buildState(allBills, billers.associateBy { it.id })
            }.collect { next ->
                _state.update { next }
            }
        }
    }

    private fun buildState(bills: List<Bill>, billersById: Map<String, com.fiatlife.app.domain.model.Biller>): CompanyHistoryState {
        val paymentRows = bills.flatMap { bill ->
            val companyLabel = bill.billerName.ifBlank { bill.accountName }.trim()
            val companyKey = when {
                !bill.linkedBillerId.isNullOrBlank() -> "id:${bill.linkedBillerId}"
                companyLabel.isNotBlank() -> "name:${normalizeCompany(companyLabel)}"
                else -> ""
            }
            if (companyKey.isBlank()) return@flatMap emptyList()
            val companyName = when {
                !bill.linkedBillerId.isNullOrBlank() -> billersById[bill.linkedBillerId]?.name?.ifBlank { companyLabel }
                else -> companyLabel
            }.orEmpty().ifBlank { "Unknown company" }
            bill.paymentHistory.mapIndexed { index, p ->
                CompanyPaymentRow(
                    id = "${bill.id}|${p.date}|$index",
                    companyKey = companyKey,
                    companyName = companyName,
                    billName = bill.name,
                    amount = p.amount,
                    paidDate = p.date,
                    hasInvoiceOrStatement = bill.statementEntries.isNotEmpty() || bill.attachmentHashes.isNotEmpty()
                )
            }
        }

        val grouped = paymentRows.groupBy { it.companyKey }
        val companyRows = grouped.mapNotNull { (key, rows) ->
            if (rows.isEmpty()) return@mapNotNull null
            val totalPaid = rows.sumOf { it.amount }
            val paymentCount = rows.size
            val lastPaid = rows.maxOfOrNull { it.paidDate } ?: return@mapNotNull null
            val displayName = rows.firstOrNull()?.companyName?.ifBlank { "Unknown company" } ?: "Unknown company"
            CompanyHistoryRow(
                key = key,
                name = displayName,
                totalPaid = totalPaid,
                paymentCount = paymentCount,
                lastPaidDate = lastPaid
            )
        }.sortedByDescending { it.lastPaidDate }

        val paymentsSorted = grouped.mapValues { (_, rows) ->
            rows.sortedByDescending { it.paidDate }
        }

        return CompanyHistoryState(
            companies = companyRows,
            paymentsByCompanyKey = paymentsSorted
        )
    }

    private fun normalizeCompany(value: String): String =
        value.trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
}
