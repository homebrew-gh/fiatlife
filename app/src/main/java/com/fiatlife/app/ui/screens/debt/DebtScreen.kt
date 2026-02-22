package com.fiatlife.app.ui.screens.debt

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.fiatlife.app.domain.model.CreditAccount
import com.fiatlife.app.domain.model.CreditAccountType
import com.fiatlife.app.domain.model.CreditCardMinPaymentType
import com.fiatlife.app.ui.components.CurrencyTextField
import com.fiatlife.app.ui.components.EmptyState
import com.fiatlife.app.ui.components.MoneyText
import com.fiatlife.app.ui.components.PercentageTextField
import com.fiatlife.app.ui.components.formatCurrency
import com.fiatlife.app.ui.navigation.Screen
import com.fiatlife.app.ui.viewmodel.DebtViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtScreen(
    navController: NavController,
    viewModel: DebtViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddAccount() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add account")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Debt Summary",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Total Debt",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                MoneyText(
                                    amount = state.totalDebt,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Monthly Payment",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                MoneyText(
                                    amount = state.totalMonthlyPayment,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        if (state.totalCreditAvailable > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Credit Available",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                    MoneyText(
                                        amount = state.totalCreditAvailable,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Utilization",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = "%.0f%%".format(state.utilizationPercent * 100),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${state.accounts.size} account(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            if (state.accounts.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.AccountBalance,
                        title = "No debt accounts yet",
                        subtitle = "Tap + to add a credit card or loan"
                    )
                }
            } else {
                items(state.accounts, key = { it.id }) { account ->
                    DebtAccountCard(
                        account = account,
                        onClick = { navController.navigate(Screen.DebtDetail.routeWithId(account.id)) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (state.showAddDialog) {
        CreditAccountDialog(
            account = state.editingAccount,
            onDismiss = { viewModel.dismissDialog() },
            onSave = { acc -> viewModel.saveAccount(acc) },
            isSaving = state.isSaving
        )
    }

    LaunchedEffect(state.navigateToAccountId) {
        state.navigateToAccountId?.let { id ->
            navController.navigate(Screen.DebtDetail.routeWithId(id))
            viewModel.clearNavigateToAccountId()
        }
    }
}

@Composable
private fun DebtAccountCard(
    account: CreditAccount,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = account.type.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (account.institution.isNotBlank()) {
                    Text(
                        text = account.institution,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(
                    amount = account.currentBalance,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${account.effectiveMonthlyPayment().formatCurrency()}/mo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "View details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreditAccountDialog(
    account: CreditAccount?,
    onDismiss: () -> Unit,
    onSave: (CreditAccount) -> Unit,
    isSaving: Boolean
) {
    var name by remember { mutableStateOf(account?.name ?: "") }
    var type by remember { mutableStateOf(account?.type ?: CreditAccountType.CREDIT_CARD) }
    var institution by remember { mutableStateOf(account?.institution ?: "") }
    var accountNumberLast4 by remember { mutableStateOf(account?.accountNumberLast4 ?: "") }
    var aprPercent by remember { mutableStateOf(if (account != null) "%.2f".format(account!!.apr * 100.0) else "") }
    var currentBalance by remember { mutableStateOf(account?.currentBalance?.toString()?.takeIf { it != "0.0" } ?: "") }
    var dueDay by remember { mutableStateOf(account?.dueDay?.toString() ?: "") }
    var notes by remember { mutableStateOf(account?.notes ?: "") }
    var typeExpanded by remember { mutableStateOf(false) }

    var creditLimit by remember { mutableStateOf(account?.creditLimit?.toString()?.takeIf { it != "0.0" } ?: "") }
    var minimumPaymentType by remember { mutableStateOf(account?.minimumPaymentType ?: CreditCardMinPaymentType.PERCENT_OF_BALANCE) }
    var minimumPaymentValue by remember { mutableStateOf(
        if (account != null) when (account!!.minimumPaymentType) {
            CreditCardMinPaymentType.FIXED -> "%.2f".format(account!!.minimumPaymentValue)
            CreditCardMinPaymentType.PERCENT_OF_BALANCE -> "%.1f".format(account!!.minimumPaymentValue)
            else -> "25"
        } else ""
    ) }
    var minPaymentTypeExpanded by remember { mutableStateOf(false) }

    var originalPrincipal by remember { mutableStateOf(account?.originalPrincipal?.toString()?.takeIf { it != "0.0" } ?: "") }
    var termMonths by remember { mutableStateOf(account?.termMonths?.toString() ?: "") }
    var monthlyPaymentAmount by remember { mutableStateOf(account?.monthlyPaymentAmount?.toString() ?: "") }

    val isRevolving = type.isRevolving
    val isAmortizing = type.isAmortizing

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (account == null) "Add account" else "Edit account") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = type.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Account type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = MaterialTheme.shapes.medium
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        CreditAccountType.entries.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.displayName) },
                                onClick = {
                                    type = t
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
                OutlinedTextField(
                    value = institution,
                    onValueChange = { institution = it },
                    label = { Text("Institution") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
                OutlinedTextField(
                    value = accountNumberLast4,
                    onValueChange = { accountNumberLast4 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("Last 4 digits") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
                CurrencyTextField(
                    value = currentBalance,
                    onValueChange = { currentBalance = it },
                    label = "Current balance",
                    placeholder = "0"
                )
                PercentageTextField(
                    value = aprPercent,
                    onValueChange = { aprPercent = it },
                    label = "APR %",
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "0"
                )
                OutlinedTextField(
                    value = dueDay,
                    onValueChange = { dueDay = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("Due day (1–31)") },
                    placeholder = { Text("1") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
                if (isRevolving) {
                    CurrencyTextField(
                        value = creditLimit,
                        onValueChange = { creditLimit = it },
                        label = "Credit limit",
                        placeholder = "0"
                    )
                    ExposedDropdownMenuBox(
                        expanded = minPaymentTypeExpanded,
                        onExpandedChange = { minPaymentTypeExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = when (minimumPaymentType) {
                                CreditCardMinPaymentType.FIXED -> "Fixed amount"
                                CreditCardMinPaymentType.PERCENT_OF_BALANCE -> "% of balance"
                                CreditCardMinPaymentType.FULL_BALANCE -> "Pay in full"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Minimum payment") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(minPaymentTypeExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = MaterialTheme.shapes.medium
                        )
                        ExposedDropdownMenu(
                            expanded = minPaymentTypeExpanded,
                            onDismissRequest = { minPaymentTypeExpanded = false }
                        ) {
                            CreditCardMinPaymentType.entries.forEach { t ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when (t) {
                                                CreditCardMinPaymentType.FIXED -> "Fixed amount"
                                                CreditCardMinPaymentType.PERCENT_OF_BALANCE -> "% of balance"
                                                CreditCardMinPaymentType.FULL_BALANCE -> "Pay in full"
                                            }
                                        )
                                    },
                                    onClick = {
                                        minimumPaymentType = t
                                        minimumPaymentValue = when (t) {
                                            CreditCardMinPaymentType.FIXED -> "25"
                                            CreditCardMinPaymentType.PERCENT_OF_BALANCE -> "2.0"
                                            else -> minimumPaymentValue
                                        }
                                        minPaymentTypeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = minimumPaymentValue,
                        onValueChange = { minimumPaymentValue = it.filter { c -> c.isDigit() || c == '.' } },
                        label = {
                            Text(
                                when (minimumPaymentType) {
                                    CreditCardMinPaymentType.FIXED -> "Minimum $ amount"
                                    CreditCardMinPaymentType.PERCENT_OF_BALANCE -> "Percent (e.g. 2)"
                                    else -> "—"
                                }
                            )
                        },
                        placeholder = {
                            Text(
                                when (minimumPaymentType) {
                                    CreditCardMinPaymentType.FIXED -> "25"
                                    CreditCardMinPaymentType.PERCENT_OF_BALANCE -> "2"
                                    else -> ""
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        enabled = minimumPaymentType != CreditCardMinPaymentType.FULL_BALANCE
                    )
                }
                if (isAmortizing) {
                    CurrencyTextField(
                        value = originalPrincipal,
                        onValueChange = { originalPrincipal = it },
                        label = "Original principal",
                        placeholder = "0"
                    )
                    OutlinedTextField(
                        value = termMonths,
                        onValueChange = { termMonths = it.filter { c -> c.isDigit() }.take(5) },
                        label = { Text("Term (months)") },
                        placeholder = { Text("e.g. 60") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                    CurrencyTextField(
                        value = monthlyPaymentAmount,
                        onValueChange = { monthlyPaymentAmount = it },
                        label = "Monthly payment",
                        placeholder = "0"
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = MaterialTheme.shapes.medium
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val id = account?.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
                    val now = System.currentTimeMillis()
                    val apr = (aprPercent.toDoubleOrNull() ?: 0.0) / 100.0
                    val balance = currentBalance.toDoubleOrNull() ?: 0.0
                    val minVal = minimumPaymentValue.toDoubleOrNull() ?: when (minimumPaymentType) {
                        CreditCardMinPaymentType.FIXED -> 25.0
                        CreditCardMinPaymentType.PERCENT_OF_BALANCE -> 2.0
                        else -> 0.0
                    }
                    onSave(
                        CreditAccount(
                            id = id,
                            name = name.trim(),
                            type = type,
                            institution = institution.trim(),
                            accountNumberLast4 = accountNumberLast4.trim(),
                            apr = apr.coerceAtLeast(0.0),
                            currentBalance = balance.coerceAtLeast(0.0),
                            dueDay = dueDay.toIntOrNull()?.coerceIn(1, 31) ?: 1,
                            notes = notes.trim(),
                            createdAt = account?.createdAt ?: now,
                            updatedAt = now,
                            creditLimit = if (isRevolving) (creditLimit.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0) else 0.0,
                            minimumPaymentType = minimumPaymentType,
                            minimumPaymentValue = minVal.coerceAtLeast(0.0),
                            originalPrincipal = if (isAmortizing) (originalPrincipal.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0) else 0.0,
                            termMonths = termMonths.toIntOrNull()?.takeIf { it > 0 },
                            monthlyPaymentAmount = monthlyPaymentAmount.toDoubleOrNull()?.takeIf { it >= 0 },
                            startDate = account?.startDate,
                            endDate = account?.endDate
                        )
                    )
                },
                enabled = name.isNotBlank() && !isSaving
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
