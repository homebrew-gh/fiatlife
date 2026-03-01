package com.fiatlife.app.ui.screens.bills

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.fiatlife.app.domain.model.BankAccount
import com.fiatlife.app.domain.model.BillCategory
import com.fiatlife.app.domain.model.BillGeneralCategory
import com.fiatlife.app.domain.model.BillFrequency
import com.fiatlife.app.domain.model.BillRecurrenceUnit
import com.fiatlife.app.domain.model.Biller
import com.fiatlife.app.domain.model.CreditAccount
import com.fiatlife.app.domain.model.CreditCardDetails
import com.fiatlife.app.domain.model.CreditCardMinPaymentType
import com.fiatlife.app.domain.model.StatementEntry
import com.fiatlife.app.domain.model.Bill
import com.fiatlife.app.domain.model.BillSubcategory
import com.fiatlife.app.domain.model.BillWithSource
import com.fiatlife.app.ui.navigation.Screen
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import com.fiatlife.app.ui.components.CurrencyTextField
import com.fiatlife.app.ui.components.MoneyText
import com.fiatlife.app.ui.components.EmptyState
import com.fiatlife.app.ui.components.PercentageTextField
import com.fiatlife.app.ui.components.formatCurrency
import com.fiatlife.app.ui.theme.ProfitGreen
import com.fiatlife.app.ui.viewmodel.BillsViewModel
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(
    navController: NavController,
    viewModel: BillsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val subscriptionExpandedBySubcategory = remember { mutableStateMapOf<String, Boolean>() }
    var summaryExpanded by rememberSaveable { mutableStateOf(false) }
    var summaryMode by rememberSaveable { mutableStateOf("monthly") }
    val showingAnnual = summaryMode == "annual"
    val summaryTitle = if (showingAnnual) "Annual Summary" else "Monthly Total"
    val summaryTotal = if (showingAnnual) state.totalAnnual else state.totalMonthly
    val summaryCategoryTotals = if (showingAnnual) state.annualCategoryTotals else state.categoryTotals
    val summaryPaymentBreakdown = if (showingAnnual) state.annualPaymentBreakdown else state.paymentBreakdown
    val summaryPaymentSubtotalBanks = if (showingAnnual) state.annualPaymentSubtotalBanks else state.paymentSubtotalBanks
    val summaryPaymentSubtotalCredit = if (showingAnnual) state.annualPaymentSubtotalCredit else state.paymentSubtotalCredit

    LaunchedEffect(Unit) {
        viewModel.showPastDueAutopayDialogIfNeeded()
    }
    LaunchedEffect(state.message) {
        if (state.message.isNotBlank()) {
            Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddBill() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Bill")
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
            // Monthly total card with category totals in header
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(summaryExpanded) {
                            detectTapGestures(onDoubleTap = { summaryExpanded = !summaryExpanded })
                        }
                        .pointerInput(summaryMode) {
                            var dragX = 0f
                            var dragY = 0f
                            detectDragGestures(
                                onDragStart = {
                                    dragX = 0f
                                    dragY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    dragX += dragAmount.x
                                    dragY += dragAmount.y
                                },
                                onDragEnd = {
                                    if (abs(dragX) > 72f && abs(dragX) > abs(dragY)) {
                                        summaryMode = if (summaryMode == "monthly") "annual" else "monthly"
                                    }
                                }
                            )
                        },
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
                            text = summaryTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        MoneyText(
                            amount = summaryTotal,
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${state.bills.size} bill(s) tracked",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "Swipe to switch monthly/annual · Double-tap for details",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                        Button(
                            onClick = { navController.navigate(Screen.CompanyHistory.route) }
                        ) {
                            Text("View Companies")
                        }
                        // Category totals in header (under monthly total)
                        if (summaryExpanded && summaryCategoryTotals.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                summaryCategoryTotals.entries
                                    .sortedBy { it.key.displayName }
                                    .forEach { (generalCategory, total) ->
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = generalCategory.displayName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                                            )
                                            Text(
                                                text = total.formatCurrency(),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                            }
                        }
                        if (summaryExpanded && summaryPaymentBreakdown.isNotEmpty()) {
                            val bankRows = summaryPaymentBreakdown.filter { !it.isCredit }
                            val creditRows = summaryPaymentBreakdown.filter { it.isCredit }
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "By payment account",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                            )
                            if (bankRows.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Accounts",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.95f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                bankRows.forEach { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = row.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                                        )
                                        Text(
                                            text = row.total.formatCurrency(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                            }
                            if (creditRows.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Credit cards",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.95f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                creditRows.forEach { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = row.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                                        )
                                        Text(
                                            text = row.total.formatCurrency(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Subtotals",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.95f)
                            )
                            if (summaryPaymentSubtotalBanks > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Accounts subtotal",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = summaryPaymentSubtotalBanks.formatCurrency(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            if (summaryPaymentSubtotalCredit > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Credit cards subtotal",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = summaryPaymentSubtotalCredit.formatCurrency(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Due in next 7 days (non-autopay, or credit/loan even if autopay)
            if (state.billsDueInNext7Days.isNotEmpty()) {
                item {
                    Text(
                        text = "Due in next 7 days",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(state.billsDueInNext7Days, key = { it.id }) { item ->
                    val linkedId = item.bill.linkedCreditAccountId
                    val linkedAccount = state.creditAccounts.find { it.id == linkedId }
                    BillCard(
                        item = item,
                        linkedAccountName = linkedAccount?.name,
                        linkedAccountId = linkedId,
                        linkedAccountBalance = linkedAccount?.currentBalance,
                        onClick = { navController.navigate(Screen.BillDetail.routeWithId(item.id)) },
                        onMarkPaid = { viewModel.recordPayment(item) },
                        onCreditClick = if (linkedId != null) {
                            { navController.navigate(Screen.DebtDetail.routeWithId(linkedId)) }
                        } else null
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            // By category
            state.otherBillsByCategory.entries
                .sortedBy { it.key.displayName }
                .forEach { (generalCategory, categoryBills) ->
                    if (categoryBills.isEmpty()) return@forEach
                    item(key = "cat_${generalCategory.name}") {
                        Text(
                            text = generalCategory.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (generalCategory == BillGeneralCategory.SUBSCRIPTION) {
                        val groupedSubscriptions = categoryBills
                            .groupBy { it.bill.effectiveSubcategory }
                            .toList()
                            .sortedBy { it.first.displayName }

                        groupedSubscriptions.forEach { (subcategory, subBills) ->
                            val sortedSubBills = subBills.sortedWith(
                                compareBy<BillWithSource> { item ->
                                    val b = item.bill
                                    if (b.isPastDue()) b.lastDueDateMillis() ?: Long.MAX_VALUE
                                    else b.nextDueDateMillis() ?: Long.MAX_VALUE
                                }.thenBy { it.bill.name.lowercase() }
                            )
                            val subcategoryKey = subcategory.name
                            val isCollapsible = sortedSubBills.size > 1
                            val isExpanded = if (isCollapsible) {
                                subscriptionExpandedBySubcategory
                                    .getOrPut(subcategoryKey) { sortedSubBills.size <= 2 }
                            } else {
                                true
                            }
                            val subtotal = sortedSubBills.sumOf { it.bill.effectiveAmountDue() }

                            item(key = "sub_header_${subcategory.name}") {
                                SubscriptionSubcategoryHeader(
                                    subcategory = subcategory,
                                    billCount = sortedSubBills.size,
                                    subtotal = subtotal,
                                    expanded = isExpanded,
                                    collapsible = isCollapsible,
                                    onToggle = {
                                        if (isCollapsible) {
                                            subscriptionExpandedBySubcategory[subcategoryKey] = !isExpanded
                                        }
                                    }
                                )
                            }

                            if (isExpanded) {
                                items(sortedSubBills, key = { it.id }) { item ->
                                    val linkedId = item.bill.linkedCreditAccountId
                                    val linkedAccount = state.creditAccounts.find { it.id == linkedId }
                                    BillCard(
                                        item = item,
                                        linkedAccountName = linkedAccount?.name,
                                        linkedAccountId = linkedId,
                                        linkedAccountBalance = linkedAccount?.currentBalance,
                                        onClick = { navController.navigate(Screen.BillDetail.routeWithId(item.id)) },
                                        onMarkPaid = { viewModel.recordPayment(item) },
                                        onCreditClick = if (linkedId != null) {
                                            { navController.navigate(Screen.DebtDetail.routeWithId(linkedId)) }
                                        } else null
                                    )
                                }
                            }
                        }
                    } else {
                        items(categoryBills, key = { it.id }) { item ->
                            val linkedId = item.bill.linkedCreditAccountId
                            val linkedAccount = state.creditAccounts.find { it.id == linkedId }
                            BillCard(
                                item = item,
                                linkedAccountName = linkedAccount?.name,
                                linkedAccountId = linkedId,
                                linkedAccountBalance = linkedAccount?.currentBalance,
                                onClick = { navController.navigate(Screen.BillDetail.routeWithId(item.id)) },
                                onMarkPaid = { viewModel.recordPayment(item) },
                                onCreditClick = if (linkedId != null) {
                                    { navController.navigate(Screen.DebtDetail.routeWithId(linkedId)) }
                                } else null
                            )
                        }
                    }
                    item(key = "spacer_${generalCategory.name}") { Spacer(modifier = Modifier.height(4.dp)) }
                }

            if (state.bills.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Receipt,
                        title = "No bills yet",
                        subtitle = "Tap + to add your first bill"
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (state.showAddDialog) {
        BillDialog(
            bill = state.editingBill,
            creditAccounts = state.creditAccounts,
            bankAccounts = state.bankAccounts,
            billers = state.billers,
            isEditingCypherLog = state.editingIsCypherLog,
            statementCount = state.dialogStatementEntries.size,
            onDismiss = { viewModel.dismissDialog() },
            onSave = { b, showInCypherLog -> viewModel.saveBill(b, showInCypherLog) },
            onUploadAttachment = { data, type, name ->
                viewModel.uploadAttachment(data, type, name)
            }
        )
    }

    LaunchedEffect(state.navigateToBillId) {
        state.navigateToBillId?.let { id ->
            navController.navigate(Screen.BillDetail.routeWithId(id))
            viewModel.clearNavigateToBillId()
        }
    }

    state.showCreditLoanPaymentDialog?.let { item ->
        CreditLoanPaymentDialog(
            item = item,
            currentBalance = state.creditAccounts.find { it.id == item.bill.linkedCreditAccountId }?.currentBalance
                ?: item.bill.creditCardDetails?.currentBalance ?: 0.0,
            defaultAmount = item.bill.effectiveAmountDue(),
            onDismiss = { viewModel.dismissCreditLoanPaymentDialog() },
            onConfirm = { amount, newBalance ->
                viewModel.recordCreditLoanPayment(item, amount, newBalance)
            }
        )
    }

    if (state.showPastDueAutopayDialog && state.pastDueAutopayBills.isNotEmpty()) {
        var selectedIds by remember { mutableStateOf(state.pastDueAutopayBills.map { it.id }.toSet()) }
        AlertDialog(
            onDismissRequest = { viewModel.dismissPastDueAutopayDialog() },
            title = { Text("Mark autopay bills as paid?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "These autopay bills are past due. Were they paid?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    state.pastDueAutopayBills.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedIds.contains(item.id),
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) selectedIds + item.id else selectedIds - item.id
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${item.bill.name} — ${item.bill.effectiveAmountDue().formatCurrency()}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = state.pastDueAutopayBills.filter { it.id in selectedIds }
                        viewModel.markPastDueAsPaid(selected)
                    }
                ) { Text("Mark selected as paid") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPastDueAutopayDialog() }) { Text("Dismiss") }
            }
        )
    }
}

@Composable
private fun CreditLoanPaymentDialog(
    item: BillWithSource,
    currentBalance: Double,
    defaultAmount: Double,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, newBalance: Double?) -> Unit
) {
    val isCreditCard = item.bill.effectiveSubcategory == BillSubcategory.CREDIT_CARD
    var amountStr by remember { mutableStateOf("%.2f".format(defaultAmount)) }
    var newBalanceStr by remember { mutableStateOf("") }
    var creditCardPaymentMode by remember { mutableStateOf(CreditCardPaymentMode.MINIMUM_DUE) }
    val customAmount = amountStr.toDoubleOrNull() ?: 0.0
    val minimumDue = defaultAmount.coerceAtLeast(0.0)
    val fullBalance = currentBalance.coerceAtLeast(0.0)
    val selectedCreditAmount = when (creditCardPaymentMode) {
        CreditCardPaymentMode.FULL_BALANCE -> fullBalance
        CreditCardPaymentMode.MINIMUM_DUE -> minimumDue
        CreditCardPaymentMode.CUSTOM -> customAmount
    }.coerceAtMost(fullBalance)
    val loanAmount = amountStr.toDoubleOrNull() ?: 0.0
    val newBalance = newBalanceStr.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record payment — ${item.bill.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isCreditCard) {
                    Text(
                        "Current balance: ${currentBalance.formatCurrency()}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { creditCardPaymentMode = CreditCardPaymentMode.FULL_BALANCE },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = creditCardPaymentMode == CreditCardPaymentMode.FULL_BALANCE,
                            onClick = { creditCardPaymentMode = CreditCardPaymentMode.FULL_BALANCE }
                        )
                        Text("Pay total balance (${fullBalance.formatCurrency()})")
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { creditCardPaymentMode = CreditCardPaymentMode.MINIMUM_DUE },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = creditCardPaymentMode == CreditCardPaymentMode.MINIMUM_DUE,
                            onClick = { creditCardPaymentMode = CreditCardPaymentMode.MINIMUM_DUE }
                        )
                        Text("Pay minimum (${minimumDue.formatCurrency()})")
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { creditCardPaymentMode = CreditCardPaymentMode.CUSTOM },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = creditCardPaymentMode == CreditCardPaymentMode.CUSTOM,
                            onClick = { creditCardPaymentMode = CreditCardPaymentMode.CUSTOM }
                        )
                        Text("Custom amount")
                    }
                    if (creditCardPaymentMode == CreditCardPaymentMode.CUSTOM) {
                        CurrencyTextField(
                            value = amountStr,
                            onValueChange = { amountStr = it },
                            label = "Custom amount paid"
                        )
                    }
                    Text(
                        text = "New balance after payment: ${(currentBalance - selectedCreditAmount).coerceAtLeast(0.0).formatCurrency()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Enter the amount paid. You can optionally set the new balance (e.g. from a statement); otherwise the balance will be reduced by the amount paid.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CurrencyTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = "Amount paid"
                    )
                    OutlinedTextField(
                        value = newBalanceStr,
                        onValueChange = { newBalanceStr = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("New balance (optional)") },
                        placeholder = { Text("Leave blank to subtract amount from current (${currentBalance.formatCurrency()})") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isCreditCard) {
                        if (selectedCreditAmount > 0) {
                            val balance = (currentBalance - selectedCreditAmount).coerceAtLeast(0.0)
                            onConfirm(selectedCreditAmount, balance)
                        }
                    } else {
                        if (loanAmount > 0) {
                            val balance = if (newBalance != null && newBalance >= 0) newBalance else null
                            onConfirm(loanAmount, balance)
                        }
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private enum class CreditCardPaymentMode {
    FULL_BALANCE,
    MINIMUM_DUE,
    CUSTOM
}

@Composable
private fun BillCard(
    item: BillWithSource,
    linkedAccountName: String? = null,
    linkedAccountId: String? = null,
    linkedAccountBalance: Double? = null,
    onClick: () -> Unit,
    onMarkPaid: () -> Unit,
    onCreditClick: (() -> Unit)? = null
) {
    val bill = item.bill
    val isPaidForCycle = bill.isPaidForCurrentCycle()
    val effectiveBalance = linkedAccountBalance ?: bill.creditCardDetails?.currentBalance ?: 0.0
    val showPayButton = if (bill.isCreditOrLoan()) effectiveBalance > 0.0 else !isPaidForCycle
    val now = System.currentTimeMillis()
    val dueMillis = if (bill.isCreditOrLoan()) {
        bill.nextDueDateMillis()
    } else {
        if (bill.isPastDue() && !isPaidForCycle) bill.lastDueDateMillis() else bill.nextDueDateMillis()
    }
    val showPastDue = bill.isPastDue() &&
        !isPaidForCycle &&
        (!bill.isCreditOrLoan() || (dueMillis != null && dueMillis <= now))
    val overdueReferenceMillis = bill.lastDueDateMillis()
    val dueDateText = dueMillis?.let { SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(it)) }
    val daysUntilDue = dueMillis?.let { millis ->
        val nowCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dueCal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        ((dueCal.timeInMillis - nowCal.timeInMillis) / 86_400_000L).toInt()
    }
    val countdownLabel = when {
        isPaidForCycle -> "Paid"
        dueMillis == null -> null
        showPastDue -> {
            val overdueFrom = overdueReferenceMillis ?: dueMillis
            val daysOverdue = (((now - overdueFrom) / 86_400_000L).toInt() + 1).coerceAtLeast(1)
            "$daysOverdue d overdue"
        }
        daysUntilDue == 0 -> "Due today"
        daysUntilDue == 1 -> "Due tomorrow"
        else -> "${daysUntilDue ?: 0} d left"
    }
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
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showPayButton) {
                Button(
                    onClick = { onMarkPaid() },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ProfitGreen),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("Paid", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(modifier = Modifier.width(12.dp))
            } else if (isPaidForCycle) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = ProfitGreen.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Paid",
                        style = MaterialTheme.typography.labelMedium,
                        color = ProfitGreen,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bill.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.isCypherLog || countdownLabel != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (item.isCypherLog) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "CypherLog",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (countdownLabel != null) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (showPastDue) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = countdownLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (showPastDue) MaterialTheme.colorScheme.onErrorContainer
                                    else MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = bill.effectiveSubcategory.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = bill.frequency.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (dueDateText != null) {
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = dueDateText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (showPastDue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (bill.statementEntries.isNotEmpty() || bill.attachmentHashes.isNotEmpty()) {
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            Icons.Filled.AttachFile,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val count = bill.statementEntries.size.coerceAtLeast(bill.attachmentHashes.size)
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            MoneyText(
                amount = bill.effectiveAmountDue(),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "View details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SubscriptionSubcategoryHeader(
    subcategory: BillSubcategory,
    billCount: Int,
    subtotal: Double,
    expanded: Boolean,
    collapsible: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = collapsible) { onToggle() },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (collapsible) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse ${subcategory.displayName}" else "Expand ${subcategory.displayName}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subcategory.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (billCount == 1) "1 subscription" else "$billCount subscriptions",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MoneyText(
                amount = subtotal,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BillDialog(
    bill: Bill?,
    creditAccounts: List<CreditAccount> = emptyList(),
    bankAccounts: List<BankAccount> = emptyList(),
    billers: List<Biller> = emptyList(),
    isEditingCypherLog: Boolean = false,
    statementCount: Int = 0,
    onDismiss: () -> Unit,
    onSave: (Bill, showInCypherLog: Boolean?) -> Unit,
    onUploadAttachment: (ByteArray, String, String) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(bill?.name ?: "") }
    var amount by remember { mutableStateOf(bill?.amount?.toString() ?: "") }
    var generalCategory by remember { mutableStateOf(bill?.effectiveGeneralCategory ?: BillGeneralCategory.OTHER) }
    var subcategory by remember { mutableStateOf(bill?.effectiveSubcategory ?: BillSubcategory.OTHER) }
    val showInCypherLogVisible = bill == null && generalCategory == BillGeneralCategory.SUBSCRIPTION
    var showInCypherLog by remember { mutableStateOf(false) }
    var frequency by remember { mutableStateOf(bill?.frequency ?: BillFrequency.MONTHLY) }
    var isRecurring by remember { mutableStateOf(bill?.isRecurring ?: true) }
    var dueDay by remember { mutableStateOf(bill?.dueDay?.toString() ?: "1") }
    var oneTimeDueDate by remember {
        mutableStateOf(
            bill?.takeIf { !it.isRecurring }?.renewalDateMillis?.let { formatIsoDate(it) } ?: ""
        )
    }
    var initialPurchaseDate by remember { mutableStateOf(bill?.initialPurchaseDateMillis?.let { formatIsoDate(it) } ?: "") }
    var annualYearsPerCycle by remember {
        mutableStateOf(
            (bill?.takeIf {
                it.frequency == BillFrequency.ANNUALLY &&
                    (it.recurrenceUnit == com.fiatlife.app.domain.model.BillRecurrenceUnit.YEAR || it.recurrenceIntervalCount > 1)
            }?.recurrenceIntervalCount ?: 1).toString()
        )
    }
    var autoPay by remember { mutableStateOf(bill?.autoPay ?: false) }
    var rateValidUntil by remember { mutableStateOf(bill?.rateValidUntilMillis?.let { formatIsoDate(it) } ?: "") }
    var accountName by remember { mutableStateOf(bill?.accountName ?: "") }
    var billerName by remember { mutableStateOf(bill?.billerName ?: "") }
    var payFromBankAccountId by remember { mutableStateOf(bill?.payFromBankAccountId ?: "") }
    var payFromCreditAccountId by remember { mutableStateOf(bill?.payFromCreditAccountId ?: "") }
    var payFromExpanded by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf(bill?.notes ?: "") }
    var generalCategoryExpanded by remember { mutableStateOf(false) }
    var subcategoryExpanded by remember { mutableStateOf(false) }
    var frequencyExpanded by remember { mutableStateOf(false) }

    /** When adding a new bill, hide Credit/Loans (those bills are created from the Debt tab). */
    val generalCategoriesForForm = remember(bill) {
        if (bill == null) BillGeneralCategory.entries.filter { it != BillGeneralCategory.CREDIT_LOANS }
        else BillGeneralCategory.entries
    }
    val subcategoriesForGeneral = remember(generalCategory) {
        BillSubcategory.entries.filter { it.generalCategory == generalCategory }
    }

    val cc = bill?.creditCardDetails
    var currentBalance by remember(bill, subcategory) {
        mutableStateOf(if (subcategory == BillSubcategory.CREDIT_CARD) (cc?.currentBalance ?: 0.0).toString() else "0")
    }
    var aprPercent by remember(bill, subcategory) {
        mutableStateOf(if (subcategory == BillSubcategory.CREDIT_CARD) "%.2f".format((cc?.apr ?: 0.0) * 100.0) else "0")
    }
    var minPaymentType by remember(bill, subcategory) {
        mutableStateOf(cc?.minimumPaymentType ?: CreditCardMinPaymentType.PERCENT_OF_BALANCE)
    }
    var minPaymentValue by remember(bill, subcategory) {
        mutableStateOf(
            when (cc?.minimumPaymentType) {
                CreditCardMinPaymentType.FIXED -> "%.2f".format(cc?.minimumPaymentValue ?: 25.0)
                CreditCardMinPaymentType.PERCENT_OF_BALANCE -> "%.1f".format(cc?.minimumPaymentValue ?: 2.0)
                else -> "25"
            }
        )
    }
    var minPaymentTypeExpanded by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                val bytes = stream.readBytes()
                val mimeType = context.contentResolver.getType(it) ?: "application/octet-stream"
                val fileName = "attachment_${System.currentTimeMillis()}"
                onUploadAttachment(bytes, mimeType, fileName)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (bill == null) "Add Bill" else "Edit Bill") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Bill Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                }
                if (subcategory != BillSubcategory.CREDIT_CARD) {
                    item {
                        CurrencyTextField(
                            value = amount,
                            onValueChange = { amount = it },
                            label = "Amount"
                        )
                    }
                }
                if (subcategory == BillSubcategory.CREDIT_CARD) {
                    item {
                        CurrencyTextField(
                            value = currentBalance,
                            onValueChange = { currentBalance = it },
                            label = "Current balance"
                        )
                    }
                    item {
                        PercentageTextField(
                            value = aprPercent,
                            onValueChange = { aprPercent = it },
                            label = "APR %",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        ExposedDropdownMenuBox(
                            expanded = minPaymentTypeExpanded,
                            onExpandedChange = { minPaymentTypeExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = when (minPaymentType) {
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
                                CreditCardMinPaymentType.entries.forEach { type ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                when (type) {
                                                    CreditCardMinPaymentType.FIXED -> "Fixed amount"
                                                    CreditCardMinPaymentType.PERCENT_OF_BALANCE -> "% of balance"
                                                    CreditCardMinPaymentType.FULL_BALANCE -> "Pay in full"
                                                }
                                            )
                                        },
                                        onClick = {
                                            minPaymentType = type
                                            minPaymentValue = when (type) {
                                                CreditCardMinPaymentType.FIXED -> "25"
                                                CreditCardMinPaymentType.PERCENT_OF_BALANCE -> "2.0"
                                                CreditCardMinPaymentType.FULL_BALANCE -> minPaymentValue
                                            }
                                            minPaymentTypeExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = minPaymentValue,
                            onValueChange = { minPaymentValue = it.filter { c -> c.isDigit() || c == '.' } },
                            label = {
                                Text(
                                    when (minPaymentType) {
                                        CreditCardMinPaymentType.FIXED -> "Minimum $ amount"
                                        CreditCardMinPaymentType.PERCENT_OF_BALANCE -> "Percent (e.g. 2)"
                                        CreditCardMinPaymentType.FULL_BALANCE -> "—"
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            enabled = minPaymentType != CreditCardMinPaymentType.FULL_BALANCE
                        )
                    }
                }
                item {
                    ExposedDropdownMenuBox(
                        expanded = generalCategoryExpanded,
                        onExpandedChange = { generalCategoryExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = generalCategory.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("General Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(generalCategoryExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = MaterialTheme.shapes.medium
                        )
                        ExposedDropdownMenu(
                            expanded = generalCategoryExpanded,
                            onDismissRequest = { generalCategoryExpanded = false }
                        ) {
                            generalCategoriesForForm.forEach { gen ->
                                DropdownMenuItem(
                                    text = { Text(gen.displayName) },
                                    onClick = {
                                        generalCategory = gen
                                        subcategory = BillSubcategory.entries.first { it.generalCategory == gen }
                                        generalCategoryExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                item {
                    ExposedDropdownMenuBox(
                        expanded = subcategoryExpanded,
                        onExpandedChange = { subcategoryExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = subcategory.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Subcategory") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(subcategoryExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = MaterialTheme.shapes.medium
                        )
                        ExposedDropdownMenu(
                            expanded = subcategoryExpanded,
                            onDismissRequest = { subcategoryExpanded = false }
                        ) {
                            subcategoriesForGeneral.forEach { sub ->
                                DropdownMenuItem(
                                    text = { Text(sub.displayName) },
                                    onClick = {
                                        subcategory = sub
                                        subcategoryExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                if (showInCypherLogVisible) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = showInCypherLog,
                                onCheckedChange = { showInCypherLog = it }
                            )
                            Text(
                                text = "Show in CypherLog (home-related)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recurring bill")
                        Switch(
                            checked = isRecurring,
                            onCheckedChange = {
                                isRecurring = it
                            }
                        )
                    }
                }
                if (isRecurring) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExposedDropdownMenuBox(
                                expanded = frequencyExpanded,
                                onExpandedChange = { frequencyExpanded = it },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = frequency.displayName,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Frequency") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(frequencyExpanded) },
                                    modifier = Modifier.menuAnchor(),
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.medium
                                )
                                ExposedDropdownMenu(
                                    expanded = frequencyExpanded,
                                    onDismissRequest = { frequencyExpanded = false }
                                ) {
                                    BillFrequency.entries.forEach { freq ->
                                        DropdownMenuItem(
                                            text = { Text(freq.displayName) },
                                            onClick = {
                                                frequency = freq
                                                frequencyExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = dueDay,
                                onValueChange = { dueDay = it.filter { c -> c.isDigit() }.take(2) },
                                label = { Text("Due Day") },
                                modifier = Modifier.weight(0.5f),
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium
                            )
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = initialPurchaseDate,
                            onValueChange = { initialPurchaseDate = it.take(10) },
                            label = { Text("Initial purchase date (YYYY-MM-DD)") },
                            placeholder = { Text("2025-03-15") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium
                        )
                    }
                    if (frequency == BillFrequency.ANNUALLY) {
                        item {
                            OutlinedTextField(
                                value = annualYearsPerCycle,
                                onValueChange = { annualYearsPerCycle = it.filter { c -> c.isDigit() }.take(2) },
                                label = { Text("Years per cycle (1+)") },
                                placeholder = { Text("1") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium
                            )
                        }
                    }
                } else {
                    item {
                        OutlinedTextField(
                            value = oneTimeDueDate,
                            onValueChange = { oneTimeDueDate = it.take(10) },
                            label = { Text("Due date (YYYY-MM-DD)") },
                            placeholder = { Text("2026-03-01") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = rateValidUntil,
                        onValueChange = { rateValidUntil = it.take(10) },
                        label = { Text("Rate valid until (optional)") },
                        placeholder = { Text("2026-12-31") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                }
                item {
                    val suggestedBillerNames = billers.map { it.name }.distinct()
                    OutlinedTextField(
                        value = billerName,
                        onValueChange = { billerName = it },
                        label = { Text("Company/Biller (optional)") },
                        placeholder = { Text("e.g. Duke Energy") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                    if (suggestedBillerNames.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Existing: ${suggestedBillerNames.take(4).joinToString(", ")}" +
                                if (suggestedBillerNames.size > 4) "…" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    val payFromDisplay = when {
                        payFromBankAccountId.isNotBlank() -> bankAccounts.find { it.id == payFromBankAccountId }?.let { "${it.name} (Bank)" } ?: "…"
                        payFromCreditAccountId.isNotBlank() -> creditAccounts.find { it.id == payFromCreditAccountId }?.let { "${it.name} (Credit card)" } ?: "…"
                        else -> "None"
                    }
                    ExposedDropdownMenuBox(
                        expanded = payFromExpanded,
                        onExpandedChange = { payFromExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = payFromDisplay,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Pay from account") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(payFromExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = MaterialTheme.shapes.medium
                        )
                        ExposedDropdownMenu(
                            expanded = payFromExpanded,
                            onDismissRequest = { payFromExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None") },
                                onClick = {
                                    payFromBankAccountId = ""
                                    payFromCreditAccountId = ""
                                    payFromExpanded = false
                                }
                            )
                            bankAccounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text("${acc.name} (Bank)") },
                                    onClick = {
                                        payFromBankAccountId = acc.id
                                        payFromCreditAccountId = ""
                                        payFromExpanded = false
                                    }
                                )
                            }
                            creditAccounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text("${acc.name} (${acc.type.displayName})") },
                                    onClick = {
                                        payFromBankAccountId = ""
                                        payFromCreditAccountId = acc.id
                                        payFromExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto Pay")
                        Switch(checked = autoPay, onCheckedChange = { autoPay = it })
                    }
                }
                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = MaterialTheme.shapes.medium
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { filePicker.launch("*/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.AttachFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Attach Statement (PDF/Image)")
                    }
                    if (statementCount > 0) {
                        Text(
                            text = "$statementCount file(s) attached",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val balance = currentBalance.toDoubleOrNull() ?: 0.0
                    val apr = (aprPercent.toDoubleOrNull() ?: 0.0) / 100.0
                    val minVal = minPaymentValue.toDoubleOrNull() ?: when (minPaymentType) {
                        CreditCardMinPaymentType.FIXED -> 25.0
                        CreditCardMinPaymentType.PERCENT_OF_BALANCE -> 2.0
                        else -> 0.0
                    }
                    val ccDetails = if (subcategory == BillSubcategory.CREDIT_CARD) {
                        CreditCardDetails(
                            currentBalance = balance.coerceAtLeast(0.0),
                            apr = apr.coerceAtLeast(0.0),
                            minimumPaymentType = minPaymentType,
                            minimumPaymentValue = minVal.coerceAtLeast(0.0),
                            interestChargedLastPeriod = bill?.creditCardDetails?.interestChargedLastPeriod ?: 0.0
                        )
                    } else null
                    val effectiveAmount = ccDetails?.minimumDue(ccDetails.currentBalance) ?: (amount.toDoubleOrNull() ?: 0.0)
                    val showInCypherLogArg = if (showInCypherLogVisible) showInCypherLog else null
                    val initialPurchaseDateMillis = if (isRecurring) parseIsoDate(initialPurchaseDate) else null
                    val oneTimeDueDateMillis = if (!isRecurring) parseIsoDate(oneTimeDueDate) else null
                    val annualInterval = annualYearsPerCycle.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val recurrenceUnit = if (isRecurring && frequency == BillFrequency.ANNUALLY && annualInterval > 1) {
                        com.fiatlife.app.domain.model.BillRecurrenceUnit.YEAR
                    } else null
                    val intervalCount = if (isRecurring && frequency == BillFrequency.ANNUALLY) annualInterval else 1
                    val rateValidUntilMillis = parseIsoDate(rateValidUntil)
                    val originalBillerName = bill?.billerName?.trim().orEmpty()
                    val normalizedInputBiller = billerName.trim()
                    val linkedBillerIdForSave = if (
                        normalizedInputBiller.equals(originalBillerName, ignoreCase = true)
                    ) bill?.linkedBillerId else null
                    onSave(
                        Bill(
                            id = bill?.id ?: "",
                            name = name,
                            amount = effectiveAmount,
                            category = BillCategory.OTHER,
                            subcategory = subcategory,
                            frequency = frequency,
                            dueDay = dueDay.toIntOrNull() ?: 1,
                            autoPay = autoPay,
                            renewalDateMillis = if (isRecurring) bill?.renewalDateMillis else oneTimeDueDateMillis,
                            initialPurchaseDateMillis = initialPurchaseDateMillis,
                            recurrenceUnit = if (isRecurring) recurrenceUnit else null,
                            recurrenceIntervalCount = if (isRecurring) intervalCount else 1,
                            recurrenceTimezone = null,
                            isRecurring = isRecurring,
                            rateValidUntilMillis = rateValidUntilMillis,
                            accountName = accountName,
                            billerName = normalizedInputBiller,
                            notes = notes,
                            attachmentHashes = bill?.attachmentHashes ?: emptyList(),
                            statementEntries = bill?.statementEntries ?: emptyList(),
                            paymentHistory = bill?.paymentHistory ?: emptyList(),
                            isPaid = bill?.isPaid ?: false,
                            lastPaidDate = bill?.lastPaidDate,
                            createdAt = bill?.createdAt ?: 0L,
                            updatedAt = 0L,
                            creditCardDetails = ccDetails,
                            linkedCreditAccountId = bill?.linkedCreditAccountId,
                            linkedBillerId = linkedBillerIdForSave,
                            payFromBankAccountId = payFromBankAccountId.takeIf { it.isNotBlank() },
                            payFromCreditAccountId = payFromCreditAccountId.takeIf { it.isNotBlank() }
                        ),
                        showInCypherLogArg
                    )
                },
                enabled = name.isNotBlank() &&
                    (if (subcategory == BillSubcategory.CREDIT_CARD) true else (amount.toDoubleOrNull() ?: 0.0) > 0) &&
                    (if (isRecurring) true else parseIsoDate(oneTimeDueDate) != null)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    return String.format(
        Locale.US,
        "%04d-%02d-%02d",
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH)
    )
}
