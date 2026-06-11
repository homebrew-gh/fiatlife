package com.fiatlife.app.ui.screens.budget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fiatlife.app.domain.model.BudgetCategoryKind
import com.fiatlife.app.domain.model.BudgetRow
import com.fiatlife.app.ui.components.CurrencyTextField
import com.fiatlife.app.ui.components.EmptyState
import com.fiatlife.app.ui.components.ProgressBar
import com.fiatlife.app.ui.components.formatCurrency
import com.fiatlife.app.ui.theme.LossRed
import com.fiatlife.app.ui.theme.ProfitGreen
import com.fiatlife.app.ui.theme.WarningAmber
import com.fiatlife.app.ui.viewmodel.BudgetViewModel
import kotlin.math.abs

@Composable
fun BudgetScreen(
    viewModel: BudgetViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val summary = state.summary

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!state.hasData) {
            item {
                EmptyState(
                    icon = Icons.Filled.PieChart,
                    title = "No budget yet",
                    subtitle = "Add your paycheck and bills, then set monthly targets below to start budgeting."
                )
            }
        }

        item {
            BudgetOverviewCard(
                takeHome = summary.takeHome,
                totalTarget = summary.totalTarget,
                unbudgeted = summary.unbudgeted,
                totalActual = summary.totalActual,
                remaining = summary.remaining
            )
        }

        item {
            SectionHeader(
                title = "Spending",
                subtitle = "Variable purchases that aren't bills. Enter what you've spent this month."
            )
        }
        items(summary.variableRows, key = { "var-${it.key}" }) { row ->
            VariableBudgetCard(
                row = row,
                onTargetChange = { viewModel.updateTarget(row.key, BudgetCategoryKind.VARIABLE, it) },
                onSpentChange = { viewModel.updateSpent(row.key, it) }
            )
        }

        item {
            SectionHeader(
                title = "Bills",
                subtitle = "Pulled automatically from your recurring bills. Set a target to budget against them."
            )
        }
        if (summary.billRows.isEmpty()) {
            item {
                Text(
                    text = "No bills yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(summary.billRows, key = { "bill-${it.key}" }) { row ->
                BillBudgetCard(
                    row = row,
                    onTargetChange = { viewModel.updateTarget(row.key, BudgetCategoryKind.BILL, it) }
                )
            }
        }
    }
}

@Composable
private fun BudgetOverviewCard(
    takeHome: Double,
    totalTarget: Double,
    unbudgeted: Double,
    totalActual: Double,
    remaining: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OverviewStat("Take-home", takeHome.formatCurrency(), MaterialTheme.colorScheme.onPrimaryContainer)
                OverviewStat("Budgeted", totalTarget.formatCurrency(), MaterialTheme.colorScheme.onPrimaryContainer)
                OverviewStat(
                    "Unbudgeted",
                    unbudgeted.formatCurrency(),
                    if (unbudgeted >= 0) ProfitGreen else LossRed
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Spent / committed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = totalActual.formatCurrency(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = "${remaining.formatCurrency()} left",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (remaining >= 0) ProfitGreen else LossRed
                )
            }
        }
    }
}

@Composable
private fun OverviewStat(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VariableBudgetCard(
    row: BudgetRow,
    onTargetChange: (Double) -> Unit,
    onSpentChange: (Double) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BudgetAmountField(
                    value = row.actual,
                    onCommit = onSpentChange,
                    label = "Spent",
                    modifier = Modifier.weight(1f)
                )
                BudgetAmountField(
                    value = row.target,
                    onCommit = onTargetChange,
                    label = "Target",
                    modifier = Modifier.weight(1f)
                )
            }
            BudgetProgress(row)
        }
    }
}

@Composable
private fun BillBudgetCard(
    row: BudgetRow,
    onTargetChange: (Double) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${row.actual.formatCurrency()}/mo in bills",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BudgetAmountField(
                    value = row.target,
                    onCommit = onTargetChange,
                    label = "Target",
                    modifier = Modifier.width(140.dp)
                )
            }
            if (row.target > 0) {
                BudgetProgress(row)
            }
        }
    }
}

@Composable
private fun BudgetProgress(row: BudgetRow) {
    if (row.target <= 0 && row.actual <= 0) return
    val over = row.target > 0 && row.actual > row.target
    val color = when {
        over -> LossRed
        row.percentUsed >= 85 -> WarningAmber
        else -> ProfitGreen
    }
    Spacer(modifier = Modifier.height(10.dp))
    ProgressBar(
        progress = if (row.target > 0) (row.actual / row.target).toFloat() else if (over) 1f else 0f,
        color = color,
        height = 8
    )
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = if (row.target > 0) "${row.percentUsed.toInt()}% of target" else "No target set",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (row.target > 0) {
            Text(
                text = if (row.remaining >= 0) {
                    "${row.remaining.formatCurrency()} left"
                } else {
                    "${(-row.remaining).formatCurrency()} over"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (row.remaining >= 0) ProfitGreen else LossRed
            )
        }
    }
}

private fun trimAmount(value: Double): String {
    if (value <= 0) return ""
    return if (value == value.toLong().toDouble()) value.toLong().toString()
    else "%.2f".format(value)
}

/** Currency input that reflects external (synced) updates without disrupting typing. */
@Composable
private fun BudgetAmountField(
    value: Double,
    onCommit: (Double) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf(trimAmount(value)) }
    LaunchedEffect(value) {
        val parsed = text.toDoubleOrNull() ?: 0.0
        if (abs(parsed - value) > 0.001) {
            text = trimAmount(value)
        }
    }
    CurrencyTextField(
        value = text,
        onValueChange = {
            text = it
            onCommit(it.toDoubleOrNull() ?: 0.0)
        },
        label = label,
        modifier = modifier
    )
}
