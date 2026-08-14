package com.fiatlife.app.ui.screens.debt

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
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
import com.fiatlife.app.domain.model.BillFrequency
import com.fiatlife.app.domain.model.CreditAccount
import com.fiatlife.app.domain.model.CreditAccountType
import com.fiatlife.app.domain.model.CreditCardMinPaymentType
import com.fiatlife.app.domain.model.PromotionAppliesTo
import com.fiatlife.app.domain.model.formatPayoffDate
import com.fiatlife.app.domain.model.suggestedEmergencyFundTarget
import com.fiatlife.app.domain.model.suggestedMaintenanceAnnual
import com.fiatlife.app.ui.components.CurrencyTextField
import com.fiatlife.app.ui.components.EmptyState
import com.fiatlife.app.ui.components.MoneyText
import com.fiatlife.app.ui.components.PercentageTextField
import com.fiatlife.app.ui.components.formatCurrency
import com.fiatlife.app.ui.navigation.Screen
import com.fiatlife.app.ui.viewmodel.DebtAccountCardUiModel
import com.fiatlife.app.ui.viewmodel.DebtViewModel
import java.util.Calendar
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtScreen(
    navController: NavController,
    viewModel: DebtViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var statementAccount by remember { mutableStateOf<CreditAccount?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
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
                        val payoff = state.payoff
                        if (payoff != null && payoff.hasInterestBearingDebt) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Debt-Free",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = if (payoff.allFeasible && payoff.debtFreeDateMillis != null)
                                            formatPayoffDate(payoff.debtFreeDateMillis)
                                        else "Not on track",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Projected Interest",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                    if (payoff.allFeasible) {
                                        MoneyText(
                                            amount = payoff.totalInterest,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    } else {
                                        Text(
                                            text = "${payoff.monthlyInterest.formatCurrency()}/mo",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                            if (!payoff.allFeasible && payoff.infeasibleCount > 0) {
                                Text(
                                    text = "⚠ ${payoff.infeasibleCount} account(s) won't pay off at the current payment.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                                    modifier = Modifier.padding(top = 8.dp)
                                )
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

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Home,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mortgage Calculator",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Compare down payments, rates, and affordability on the FiatLife web app (Debt → Mortgage Calculator).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (state.accounts.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.AccountBalance,
                        title = "No debt accounts yet",
                        subtitle = "Add credit cards and loans here. FiatLife creates a Bills reminder for each due date so you can pay without re-entering the balance."
                    )
                }
            } else {
                item {
                    Card(
                        onClick = { navController.navigate(Screen.DebtPlanner.route) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.TrendingDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Debt Planner",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Snowball vs. avalanche payoff with a debt-free date",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = "Open planner",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                items(state.accounts, key = { it.id }) { account ->
                    DebtAccountCard(
                        account = account,
                        card = state.accountCardById[account.id],
                        onClick = { navController.navigate(Screen.DebtDetail.routeWithId(account.id)) },
                        onUpdateStatement = { statementAccount = account }
                    )
                }
            }

        }
        FloatingActionButton(
            onClick = { viewModel.showAddAccount() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add account")
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

    state.pendingMortgageGoals?.let { mortgage ->
        MortgageGoalsDialog(
            account = mortgage,
            onSkip = { viewModel.skipMortgageGoals() },
            onApply = { months, emergency, maintenance ->
                viewModel.applyMortgageGoals(months, emergency, maintenance)
            }
        )
    }

    statementAccount?.let { account ->
        UpdateStatementDialog(
            account = account,
            onDismiss = { statementAccount = null },
            onSave = { update ->
                viewModel.updateStatement(account, update)
                statementAccount = null
            }
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
    card: DebtAccountCardUiModel?,
    onClick: () -> Unit,
    onUpdateStatement: () -> Unit
) {
    val interest = card?.monthlyInterest ?: 0.0
    val trap = card?.isMinimumPaymentTrap == true
    val monthlyPayment = card?.monthlyPayment ?: account.effectiveMonthlyPayment()
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                    if (interest > 0) {
                        Text(
                            text = "≈ ${interest.formatCurrency()}/mo interest",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (card?.overdue == true) {
                            "${monthlyPayment.formatCurrency()} overdue · ${card.dueDays}d"
                        } else {
                            "${monthlyPayment.formatCurrency()} due in ${card?.dueDays ?: 0}d · day ${account.dueDay}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (card?.overdue == true) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (account.isPromotionActive()) {
                        Text(
                            text = "${"%.2f".format((account.promotionalApr ?: 0.0) * 100)}% promo · " +
                                "${account.monthsUntilPromotionEnds()} mo left",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    MoneyText(
                        amount = account.currentBalance,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${monthlyPayment.formatCurrency()}/mo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onUpdateStatement) {
                        Text("Update")
                    }
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "View details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (trap) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚠ Minimum payment barely covers interest",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
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
    var aprPercent by remember {
        mutableStateOf(
            account?.let { "%.2f".format((it.standardApr ?: it.apr) * 100.0) }
                ?: ""
        )
    }
    var promotionalAprPercent by remember {
        mutableStateOf(
            account?.promotionalApr?.let { "%.2f".format(it * 100.0) } ?: ""
        )
    }
    var promotionalAprEndDate by remember {
        mutableStateOf(
            account?.promotionalAprEndDate?.let { formatIsoDate(it) } ?: ""
        )
    }
    var promotionAppliesTo by remember {
        mutableStateOf(account?.promotionAppliesTo ?: PromotionAppliesTo.PURCHASES)
    }
    var promotionAppliesExpanded by remember { mutableStateOf(false) }
    var deferredInterest by remember {
        mutableStateOf(account?.deferredInterest ?: false)
    }
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
    var loanStartDate by remember {
        mutableStateOf(account?.startDate?.let { formatIsoDate(it) } ?: "")
    }
    var homePrice by remember { mutableStateOf(account?.homePrice?.toString()?.takeIf { it != "0.0" } ?: "") }
    var annualPropertyTax by remember { mutableStateOf(account?.annualPropertyTax?.toString()?.takeIf { it != "0.0" } ?: "") }
    var annualHomeInsurance by remember { mutableStateOf(account?.annualHomeInsurance?.toString()?.takeIf { it != "0.0" } ?: "") }
    var monthlyHoa by remember { mutableStateOf(account?.monthlyHoa?.toString()?.takeIf { it != "0.0" } ?: "") }
    var monthlyPmi by remember { mutableStateOf(account?.monthlyPmi?.toString()?.takeIf { it != "0.0" } ?: "") }
    var extraMonthlyPrincipal by remember { mutableStateOf(account?.extraMonthlyPrincipal?.toString()?.takeIf { it != "0.0" } ?: "") }
    var propertyTaxEscrowed by remember { mutableStateOf(account?.propertyTaxEscrowed ?: true) }
    var homeInsuranceEscrowed by remember { mutableStateOf(account?.homeInsuranceEscrowed ?: true) }
    var hoaEscrowed by remember { mutableStateOf(account?.hoaEscrowed ?: false) }
    var pmiEscrowed by remember { mutableStateOf(account?.pmiEscrowed ?: true) }
    var annualFeeAmount by remember { mutableStateOf(account?.annualFeeAmount?.toString()?.takeIf { it != "0.0" } ?: "") }
    var annualFeeRenewalDate by remember {
        mutableStateOf(account?.annualFeeRenewalDateMillis?.let { formatIsoDate(it) } ?: "")
    }
    var annualFeeFrequency by remember { mutableStateOf(account?.annualFeeFrequency ?: BillFrequency.ANNUALLY) }
    var annualFeeFrequencyExpanded by remember { mutableStateOf(false) }

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
                    HorizontalDivider()
                    Text(
                        text = "Promotional APR (optional)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PercentageTextField(
                        value = promotionalAprPercent,
                        onValueChange = { promotionalAprPercent = it },
                        label = "Promotional APR %",
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "0"
                    )
                    OutlinedTextField(
                        value = promotionalAprEndDate,
                        onValueChange = { promotionalAprEndDate = it.take(10) },
                        label = { Text("Promotion ends (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (promotionalAprPercent.isNotBlank()) {
                        ExposedDropdownMenuBox(
                            expanded = promotionAppliesExpanded,
                            onExpandedChange = { promotionAppliesExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = when (promotionAppliesTo) {
                                    PromotionAppliesTo.PURCHASES -> "Purchases"
                                    PromotionAppliesTo.BALANCE_TRANSFER -> "Balance transfers"
                                    PromotionAppliesTo.BOTH -> "Purchases and balance transfers"
                                },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Applies to") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        promotionAppliesExpanded
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = promotionAppliesExpanded,
                                onDismissRequest = { promotionAppliesExpanded = false }
                            ) {
                                PromotionAppliesTo.entries.forEach { value ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                when (value) {
                                                    PromotionAppliesTo.PURCHASES -> "Purchases"
                                                    PromotionAppliesTo.BALANCE_TRANSFER -> "Balance transfers"
                                                    PromotionAppliesTo.BOTH -> "Both"
                                                }
                                            )
                                        },
                                        onClick = {
                                            promotionAppliesTo = value
                                            promotionAppliesExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = deferredInterest,
                                onCheckedChange = { deferredInterest = it }
                            )
                            Text("Deferred interest may apply at expiry")
                        }
                    }
                }
                if (type == CreditAccountType.CREDIT_CARD) {
                    HorizontalDivider()
                    Text(
                        text = "Membership fee (optional)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CurrencyTextField(
                        value = annualFeeAmount,
                        onValueChange = { annualFeeAmount = it },
                        label = "Fee amount",
                        placeholder = "0"
                    )
                    OutlinedTextField(
                        value = annualFeeRenewalDate,
                        onValueChange = { annualFeeRenewalDate = it.take(10) },
                        label = { Text("Renewal date (YYYY-MM-DD)") },
                        placeholder = { Text("2026-01-15") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                    ExposedDropdownMenuBox(
                        expanded = annualFeeFrequencyExpanded,
                        onExpandedChange = { annualFeeFrequencyExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = annualFeeFrequency.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Fee frequency") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(annualFeeFrequencyExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = MaterialTheme.shapes.medium
                        )
                        ExposedDropdownMenu(
                            expanded = annualFeeFrequencyExpanded,
                            onDismissRequest = { annualFeeFrequencyExpanded = false }
                        ) {
                            BillFrequency.entries.forEach { freq ->
                                DropdownMenuItem(
                                    text = { Text(freq.displayName) },
                                    onClick = {
                                        annualFeeFrequency = freq
                                        annualFeeFrequencyExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                if (isAmortizing) {
                    CurrencyTextField(
                        value = originalPrincipal,
                        onValueChange = { originalPrincipal = it },
                        label = if (type == CreditAccountType.MORTGAGE) "Loan amount" else "Original principal",
                        placeholder = "0"
                    )
                    if (type == CreditAccountType.MORTGAGE) {
                        OutlinedTextField(
                            value = loanStartDate,
                            onValueChange = { loanStartDate = it.take(10) },
                            label = { Text("Loan start (YYYY-MM-DD)") },
                            placeholder = { Text("2020-06-01") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium
                        )
                    }
                    OutlinedTextField(
                        value = termMonths,
                        onValueChange = { termMonths = it.filter { c -> c.isDigit() }.take(5) },
                        label = { Text(if (type == CreditAccountType.MORTGAGE) "Term (months)" else "Term (months)") },
                        placeholder = { Text("e.g. 360") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                    CurrencyTextField(
                        value = monthlyPaymentAmount,
                        onValueChange = { monthlyPaymentAmount = it },
                        label = if (type == CreditAccountType.MORTGAGE) "Monthly P&I payment" else "Monthly payment",
                        placeholder = "0"
                    )
                    if (type == CreditAccountType.MORTGAGE) {
                        CurrencyTextField(
                            value = extraMonthlyPrincipal,
                            onValueChange = { extraMonthlyPrincipal = it },
                            label = "Extra principal $/mo",
                            placeholder = "0"
                        )
                        CurrencyTextField(
                            value = homePrice,
                            onValueChange = { homePrice = it },
                            label = "Home price",
                            placeholder = "0"
                        )
                        CurrencyTextField(
                            value = annualPropertyTax,
                            onValueChange = { annualPropertyTax = it },
                            label = "Property tax $/yr",
                            placeholder = "0"
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = propertyTaxEscrowed,
                                onCheckedChange = { propertyTaxEscrowed = it }
                            )
                            Text("Tax escrowed in this payment")
                        }
                        CurrencyTextField(
                            value = annualHomeInsurance,
                            onValueChange = { annualHomeInsurance = it },
                            label = "Home insurance $/yr",
                            placeholder = "0"
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = homeInsuranceEscrowed,
                                onCheckedChange = { homeInsuranceEscrowed = it }
                            )
                            Text("Insurance escrowed in this payment")
                        }
                        CurrencyTextField(
                            value = monthlyHoa,
                            onValueChange = { monthlyHoa = it },
                            label = "HOA $/mo",
                            placeholder = "0"
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = hoaEscrowed,
                                onCheckedChange = { hoaEscrowed = it }
                            )
                            Text("HOA escrowed in this payment")
                        }
                        CurrencyTextField(
                            value = monthlyPmi,
                            onValueChange = { monthlyPmi = it },
                            label = "PMI $/mo",
                            placeholder = "0"
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = pmiEscrowed,
                                onCheckedChange = { pmiEscrowed = it }
                            )
                            Text("PMI escrowed in this payment")
                        }
                    }
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
                    val promotionalApr = promotionalAprPercent
                        .toDoubleOrNull()
                        ?.div(100.0)
                        ?.coerceAtLeast(0.0)
                    val promotionalEnd = parseIsoDate(promotionalAprEndDate)
                    val balance = currentBalance.toDoubleOrNull() ?: 0.0
                    val minVal = minimumPaymentValue.toDoubleOrNull() ?: when (minimumPaymentType) {
                        CreditCardMinPaymentType.FIXED -> 25.0
                        CreditCardMinPaymentType.PERCENT_OF_BALANCE -> 2.0
                        else -> 0.0
                    }
                    val feeAmount = annualFeeAmount.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                    val feeDateMillis = parseIsoDate(annualFeeRenewalDate)
                    onSave(
                        CreditAccount(
                            id = id,
                            name = name.trim(),
                            type = type,
                            institution = institution.trim(),
                            accountNumberLast4 = accountNumberLast4.trim(),
                            apr = if (
                                isRevolving &&
                                promotionalApr != null &&
                                promotionalEnd != null &&
                                promotionalEnd >= System.currentTimeMillis()
                            ) promotionalApr else apr.coerceAtLeast(0.0),
                            standardApr = apr.coerceAtLeast(0.0),
                            promotionalApr =
                                if (isRevolving) promotionalApr else null,
                            promotionalAprEndDate =
                                if (isRevolving) promotionalEnd else null,
                            promotionAppliesTo =
                                if (isRevolving && promotionalApr != null) {
                                    promotionAppliesTo
                                } else null,
                            deferredInterest =
                                isRevolving && promotionalApr != null && deferredInterest,
                            currentBalance = balance.coerceAtLeast(0.0),
                            statementBalanceAsOfMillis =
                                account?.statementBalanceAsOfMillis,
                            statementAmountDue = account?.statementAmountDue,
                            dueDay = dueDay.toIntOrNull()?.coerceIn(1, 31) ?: 1,
                            notes = notes.trim(),
                            createdAt = account?.createdAt ?: now,
                            updatedAt = now,
                            linkedBillId = account?.linkedBillId,
                            statementEntries = account?.statementEntries ?: emptyList(),
                            attachmentHashes = account?.attachmentHashes ?: emptyList(),
                            creditLimit = if (isRevolving) (creditLimit.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0) else 0.0,
                            minimumPaymentType = minimumPaymentType,
                            minimumPaymentValue = minVal.coerceAtLeast(0.0),
                            originalPrincipal = if (isAmortizing) (originalPrincipal.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0) else 0.0,
                            termMonths = termMonths.toIntOrNull()?.takeIf { it > 0 },
                            monthlyPaymentAmount = monthlyPaymentAmount.toDoubleOrNull()?.takeIf { it >= 0 },
                            startDate = if (type == CreditAccountType.MORTGAGE) parseIsoDate(loanStartDate) else account?.startDate,
                            endDate = account?.endDate,
                            annualFeeLinkedBillId = account?.annualFeeLinkedBillId,
                            annualFeeAmount = if (type == CreditAccountType.CREDIT_CARD) feeAmount else 0.0,
                            annualFeeRenewalDateMillis = if (type == CreditAccountType.CREDIT_CARD && feeAmount > 0.0) feeDateMillis else null,
                            annualFeeFrequency = if (type == CreditAccountType.CREDIT_CARD) annualFeeFrequency else BillFrequency.ANNUALLY,
                            homePrice = if (type == CreditAccountType.MORTGAGE) (homePrice.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0) else 0.0,
                            annualPropertyTax = if (type == CreditAccountType.MORTGAGE) (annualPropertyTax.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0) else 0.0,
                            annualHomeInsurance = if (type == CreditAccountType.MORTGAGE) (annualHomeInsurance.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0) else 0.0,
                            monthlyHoa = if (type == CreditAccountType.MORTGAGE) (monthlyHoa.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0) else 0.0,
                            monthlyPmi = if (type == CreditAccountType.MORTGAGE) (monthlyPmi.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0) else 0.0,
                            extraMonthlyPrincipal = if (type == CreditAccountType.MORTGAGE) (extraMonthlyPrincipal.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0) else 0.0,
                            propertyTaxEscrowed = if (type == CreditAccountType.MORTGAGE) propertyTaxEscrowed else true,
                            homeInsuranceEscrowed = if (type == CreditAccountType.MORTGAGE) homeInsuranceEscrowed else true,
                            hoaEscrowed = if (type == CreditAccountType.MORTGAGE) hoaEscrowed else false,
                            pmiEscrowed = if (type == CreditAccountType.MORTGAGE) pmiEscrowed else true,
                            linkedPropertyTaxBillId = account?.linkedPropertyTaxBillId,
                            linkedHomeInsuranceBillId = account?.linkedHomeInsuranceBillId,
                            linkedHoaBillId = account?.linkedHoaBillId,
                            linkedPmiBillId = account?.linkedPmiBillId
                        )
                    )
                },
                enabled = name.isNotBlank() &&
                    !isSaving &&
                    (promotionalAprPercent.isBlank() ||
                        parseIsoDate(promotionalAprEndDate) != null)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun MortgageGoalsDialog(
    account: CreditAccount,
    onSkip: () -> Unit,
    onApply: (emergencyMonths: Int, includeEmergency: Boolean, includeMaintenance: Boolean) -> Unit
) {
    val housing = account.housingPitiMonthly()
    val maintenanceAnnual = suggestedMaintenanceAnnual(account.homePrice)
    var months by remember { mutableStateOf(3) }
    var wantEmergency by remember { mutableStateOf(housing > 0.0) }
    var wantMaintenance by remember { mutableStateOf(account.homePrice > 0.0) }
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Set aside for the house?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Optional. You can skip or edit these later in Goals.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (housing > 0.0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = wantEmergency, onCheckedChange = { wantEmergency = it })
                        Text(
                            "Emergency fund at $months months of housing (" +
                                suggestedEmergencyFundTarget(housing, months).formatCurrency() + ")"
                        )
                    }
                    if (wantEmergency) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = months == 3,
                                onClick = { months = 3 },
                                label = { Text("3 months") }
                            )
                            FilterChip(
                                selected = months == 6,
                                onClick = { months = 6 },
                                label = { Text("6 months") }
                            )
                        }
                    }
                }
                if (account.homePrice > 0.0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = wantMaintenance, onCheckedChange = { wantMaintenance = it })
                        Text(
                            "Home maintenance ${maintenanceAnnual.formatCurrency()}/yr (" +
                                (maintenanceAnnual / 12.0).formatCurrency() + "/mo)"
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(months, wantEmergency, wantMaintenance) }) {
                Text("Save goals")
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text("Skip") }
        }
    )
}

private fun parseIsoDate(input: String): Long? {
    val value = input.trim()
    if (value.isEmpty()) return null
    val parts = value.split("-")
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    val cal = Calendar.getInstance()
    cal.set(Calendar.YEAR, year)
    cal.set(Calendar.MONTH, (month - 1).coerceIn(0, 11))
    cal.set(Calendar.DAY_OF_MONTH, day.coerceAtLeast(1))
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun formatIsoDate(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return String.format(
        java.util.Locale.US,
        "%04d-%02d-%02d",
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH)
    )
}
