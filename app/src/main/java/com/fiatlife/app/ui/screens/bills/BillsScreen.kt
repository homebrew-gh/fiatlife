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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.fiatlife.app.domain.model.BillCategory
import com.fiatlife.app.domain.model.BillGeneralCategory
import com.fiatlife.app.domain.model.BillFrequency
import com.fiatlife.app.domain.model.CreditAccount
import com.fiatlife.app.domain.model.CreditCardDetails
import com.fiatlife.app.domain.model.CreditCardMinPaymentType
import com.fiatlife.app.domain.model.StatementEntry
import com.fiatlife.app.domain.model.Bill
import com.fiatlife.app.domain.model.BillSubcategory
import com.fiatlife.app.domain.model.BillWithSource
import com.fiatlife.app.ui.navigation.Screen
import androidx.compose.foundation.clickable
import com.fiatlife.app.ui.components.CurrencyTextField
import com.fiatlife.app.ui.components.MoneyText
import com.fiatlife.app.ui.components.EmptyState
import com.fiatlife.app.ui.components.formatCurrency
import com.fiatlife.app.ui.theme.ProfitGreen
import com.fiatlife.app.ui.viewmodel.BillsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(
    navController: NavController,
    viewModel: BillsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.showPastDueAutopayDialogIfNeeded()
    }

    Scaffold(
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
                            text = "Monthly Total",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        MoneyText(
                            amount = state.totalMonthly,
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${state.bills.size} bill(s) tracked",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        // Category totals in header (under monthly total)
                        if (state.categoryTotals.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                state.categoryTotals.entries
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
                    items(categoryBills, key = { it.id }) { item ->
                        val linkedId = item.bill.linkedCreditAccountId
                        val linkedAccount = state.creditAccounts.find { it.id == linkedId }
                        BillCard(
                            item = item,
                            linkedAccountName = linkedAccount?.name,
                            linkedAccountId = linkedId,
                            onClick = { navController.navigate(Screen.BillDetail.routeWithId(item.id)) },
                            onMarkPaid = { viewModel.recordPayment(item) },
                            onCreditClick = if (linkedId != null) {
                                { navController.navigate(Screen.DebtDetail.routeWithId(linkedId)) }
                            } else null
                        )
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
            currentBalance = item.bill.creditCardDetails?.currentBalance
                ?: state.creditAccounts.find { it.id == item.bill.linkedCreditAccountId }?.currentBalance ?: 0.0,
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
    var amountStr by remember { mutableStateOf("%.2f".format(defaultAmount)) }
    var newBalanceStr by remember { mutableStateOf("") }
    val amount = amountStr.toDoubleOrNull() ?: 0.0
    val newBalance = newBalanceStr.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record payment — ${item.bill.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (amount > 0) {
                        val balance = if (newBalance != null && newBalance >= 0) newBalance else null
                        onConfirm(amount, balance)
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun BillCard(
    item: BillWithSource,
    linkedAccountName: String? = null,
    linkedAccountId: String? = null,
    onClick: () -> Unit,
    onMarkPaid: () -> Unit,
    onCreditClick: (() -> Unit)? = null
) {
    val bill = item.bill
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (bill.isPaid)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (bill.isPaid) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!item.isCypherLog && !bill.isPaid) {
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
            } else if (!item.isCypherLog && bill.isPaid) {
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
            } else if (item.isCypherLog) {
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = bill.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
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
                    if (linkedAccountId != null && onCreditClick != null) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.clickable { onCreditClick() }
                        ) {
                            Text(
                                text = "Credit: ${linkedAccountName ?: "…"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BillDialog(
    bill: Bill?,
    creditAccounts: List<CreditAccount> = emptyList(),
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
    var dueDay by remember { mutableStateOf(bill?.dueDay?.toString() ?: "1") }
    var autoPay by remember { mutableStateOf(bill?.autoPay ?: false) }
    var accountName by remember { mutableStateOf(bill?.accountName ?: "") }
    var notes by remember { mutableStateOf(bill?.notes ?: "") }
    var generalCategoryExpanded by remember { mutableStateOf(false) }
    var subcategoryExpanded by remember { mutableStateOf(false) }
    var frequencyExpanded by remember { mutableStateOf(false) }

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
    var linkedCreditAccountId by remember { mutableStateOf(bill?.linkedCreditAccountId ?: "") }
    var linkedCreditAccountExpanded by remember { mutableStateOf(false) }

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
                            BillGeneralCategory.entries.forEach { gen ->
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
                        value = accountName,
                        onValueChange = { accountName = it },
                        label = { Text("Pay From Account") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
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
                if (!isEditingCypherLog && creditAccounts.isNotEmpty()) {
                    item {
                        ExposedDropdownMenuBox(
                            expanded = linkedCreditAccountExpanded,
                            onExpandedChange = { linkedCreditAccountExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = linkedCreditAccountId.let { id ->
                                    if (id.isBlank()) "None" else creditAccounts.find { it.id == id }?.name ?: "…"
                                },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Link to credit/loan") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(linkedCreditAccountExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                shape = MaterialTheme.shapes.medium
                            )
                            ExposedDropdownMenu(
                                expanded = linkedCreditAccountExpanded,
                                onDismissRequest = { linkedCreditAccountExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("None") },
                                    onClick = {
                                        linkedCreditAccountId = ""
                                        linkedCreditAccountExpanded = false
                                    }
                                )
                                creditAccounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text("${acc.name} (${acc.type.displayName})") },
                                        onClick = {
                                            linkedCreditAccountId = acc.id
                                            linkedCreditAccountExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
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
                            accountName = accountName,
                            notes = notes,
                            attachmentHashes = bill?.attachmentHashes ?: emptyList(),
                            statementEntries = bill?.statementEntries ?: emptyList(),
                            paymentHistory = bill?.paymentHistory ?: emptyList(),
                            isPaid = bill?.isPaid ?: false,
                            lastPaidDate = bill?.lastPaidDate,
                            createdAt = bill?.createdAt ?: 0L,
                            updatedAt = 0L,
                            creditCardDetails = ccDetails,
                            linkedCreditAccountId = linkedCreditAccountId.takeIf { it.isNotBlank() }
                        ),
                        showInCypherLogArg
                    )
                },
                enabled = name.isNotBlank() && (
                    if (subcategory == BillSubcategory.CREDIT_CARD) true
                    else (amount.toDoubleOrNull() ?: 0.0) > 0
                )
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
