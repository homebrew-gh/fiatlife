package com.fiatlife.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fiatlife.app.domain.model.CreditAccount
import com.fiatlife.app.domain.model.CreditAccountType
import com.fiatlife.app.domain.model.MortgagePaymentSnapshot
import com.fiatlife.app.domain.model.currentMortgagePaymentSnapshot
import com.fiatlife.app.domain.model.formatPayoffDate
import com.fiatlife.app.ui.theme.LossRed
import com.fiatlife.app.ui.theme.ProfitGreen

@Composable
fun MortgageSnapshotLines(
    account: CreditAccount,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (account.type != CreditAccountType.MORTGAGE) return
    val snap = currentMortgagePaymentSnapshot(account) ?: return
    if (compact) {
        Text(
            text = "This payment ${snap.principalPlusExtra().formatCurrency()} principal · " +
                "${snap.interest.formatCurrency()} interest",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        return
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SnapshotRow("Principal", snap.principalPlusExtra().formatCurrency(), ProfitGreen)
        SnapshotRow("Interest", snap.interest.formatCurrency(), LossRed)
        if (snap.escrow > 0) SnapshotRow("Escrow", snap.escrow.formatCurrency())
        if (snap.pmi > 0) SnapshotRow("PMI", snap.pmi.formatCurrency())
        SnapshotRow("Servicer draft", snap.servicerDraft.formatCurrency(), emphasize = true)
        SnapshotRow("Balance", snap.remainingBalance.formatCurrency())
        SnapshotRow("Principal paid (est.)", snap.principalPaid.formatCurrency())
        SnapshotRow("Interest paid (est.)", snap.interestPaid.formatCurrency())
        SnapshotRow("Payments left", snap.paymentsRemaining.toString())
        snap.payoffDateMs?.let { SnapshotRow("Payoff", formatPayoffDate(it)) }
    }
}

private fun MortgagePaymentSnapshot.principalPlusExtra(): Double = principal + extraPrincipal

@Composable
private fun SnapshotRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color? = null,
    emphasize: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Medium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}
