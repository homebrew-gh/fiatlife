package com.fiatlife.app.ui.screens.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.fiatlife.app.domain.model.NetWorthSummary
import com.fiatlife.app.ui.components.formatCurrency
import com.fiatlife.app.ui.navigation.Screen
import com.fiatlife.app.ui.screens.debt.DebtScreen
import com.fiatlife.app.ui.theme.LossRed
import com.fiatlife.app.ui.theme.ProfitGreen
import com.fiatlife.app.ui.viewmodel.NetWorthViewModel

/** Accounts tab: net worth summary on top, then credit and loan accounts. */
@Composable
fun AccountsScreen(
    navController: NavController,
    netWorthViewModel: NetWorthViewModel = hiltViewModel()
) {
    val netWorth by netWorthViewModel.state.collectAsStateWithLifecycle()
    DebtScreen(navController = navController) {
        item(key = "net_worth") {
            NetWorthSummaryCard(
                summary = netWorth.summary,
                onClick = { navController.navigate(Screen.NetWorth.route) }
            )
        }
        item(key = "debts_heading") {
            Text(
                "Credit & loans",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun NetWorthSummaryCard(summary: NetWorthSummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Net worth",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        summary.total.formatCurrency(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (summary.total >= 0) ProfitGreen else LossRed
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Open net worth",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            SummaryLine("Cash", summary.cash.formatCurrency())
            SummaryLine("Investments", summary.invested.formatCurrency())
            SummaryLine("Bitcoin", summary.bitcoinUsd?.formatCurrency() ?: "—")
            if (summary.homeValue > 0) SummaryLine("Home equity", summary.homeEquity.formatCurrency())
            SummaryLine("Debts", "−${summary.otherDebt.formatCurrency()}")
            if (summary.bitcoinUsd == null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "BTC price unavailable — bitcoin isn't included yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
