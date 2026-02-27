package com.fiatlife.app.ui.screens.bills

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.fiatlife.app.domain.model.Bill
import com.fiatlife.app.ui.navigation.Screen
import com.fiatlife.app.domain.model.BillPayment
import com.fiatlife.app.domain.model.StatementEntry
import com.fiatlife.app.ui.components.CurrencyTextField
import com.fiatlife.app.ui.components.MoneyText
import com.fiatlife.app.ui.components.SectionCard
import com.fiatlife.app.ui.components.formatCurrency
import com.fiatlife.app.ui.theme.LossRed
import com.fiatlife.app.ui.theme.ProfitGreen
import com.fiatlife.app.ui.viewmodel.BillDetailViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillDetailScreen(
    navController: NavController,
    viewModel: BillDetailViewModel = hiltViewModel()
) {
    val bill by viewModel.bill.collectAsStateWithLifecycle()
    val billWithSource by viewModel.billWithSource.collectAsStateWithLifecycle()
    val linkedCreditAccount by viewModel.linkedCreditAccount.collectAsStateWithLifecycle()
    val creditAccounts by viewModel.creditAccounts.collectAsStateWithLifecycle()
    val bankAccounts by viewModel.bankAccounts.collectAsStateWithLifecycle()
    val billers by viewModel.billers.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showCreditLoanPaymentDialog by remember { mutableStateOf(false) }
    var hasLoadedBill by remember { mutableStateOf(false) }

    LaunchedEffect(bill) {
        if (bill != null) {
            hasLoadedBill = true
        } else if (hasLoadedBill) {
            navController.popBackStack()
        }
    }
    LaunchedEffect(message) {
        if (message.isNotBlank()) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(bill?.name ?: "Bill") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { padding ->
        val b = bill ?: return@Scaffold
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = b.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        AssistChip(
                            onClick = {},
                            label = { Text(b.effectiveSubcategory.displayName, style = MaterialTheme.typography.labelMedium) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        MoneyText(
                            amount = b.effectiveAmountDue(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        val shortDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                        val renewalText = b.renewalDateMillis?.let {
                            "Renews ${shortDateFormat.format(Date(it))}"
                        }
                        Text(
                            text = when {
                                b.isCreditCard() -> "Minimum due · Due day ${b.dueDay}"
                                !b.isRecurring -> b.renewalDateMillis?.let { "One-time bill · Due ${shortDateFormat.format(Date(it))}" }
                                    ?: "One-time bill"
                                else -> renewalText ?: "${b.frequency.displayName} · Due day ${b.dueDay}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        if (!b.isRecurring) {
                            Text(
                                text = "Non-recurring",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            )
                        }
                        b.rateValidUntilMillis?.let { validUntil ->
                            Text(
                                text = "Rate valid until ${shortDateFormat.format(Date(validUntil))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            )
                        }
                        val payFromName = when {
                            !b.payFromBankAccountId.isNullOrBlank() ->
                                bankAccounts.find { it.id == b.payFromBankAccountId }?.name
                            !b.payFromCreditAccountId.isNullOrBlank() ->
                                creditAccounts.find { it.id == b.payFromCreditAccountId }?.name
                            b.accountName.isNotBlank() -> b.accountName
                            else -> null
                        }
                        payFromName?.let { account ->
                            Text(
                                text = "Pay from $account",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            )
                        }
                        b.initialPurchaseDateMillis?.let { started ->
                            Text(
                                text = "Started ${shortDateFormat.format(Date(started))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            if (b.isCreditCard() && b.creditCardDetails != null) {
                item {
                    val cc = b.creditCardDetails!!
                    SectionCard(title = "Credit card", icon = Icons.Filled.CreditCard) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Current balance", style = MaterialTheme.typography.bodyMedium)
                            MoneyText(
                                amount = cc.currentBalance,
                                style = MaterialTheme.typography.titleMedium,
                                color = LossRed
                            )
                        }
                        if (cc.apr > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("APR", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "%.2f%%".format(cc.apr * 100),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val estInterest = cc.estimatedMonthlyInterest(cc.currentBalance)
                            if (estInterest > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Est. interest next month", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = estInterest.formatCurrency(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (cc.interestChargedLastPeriod > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Interest last period", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = cc.interestChargedLastPeriod.formatCurrency(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (linkedCreditAccount != null && billWithSource?.isCypherLog != true) {
                item {
                    val account = linkedCreditAccount!!
                    SectionCard(title = "Credit account", icon = Icons.Filled.AccountBalance) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navController.navigate(Screen.DebtDetail.routeWithId(account.id)) },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = account.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Icon(Icons.Filled.ChevronRight, contentDescription = "View", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            val companyName = b.billerName.ifBlank { b.accountName }.trim().takeIf { it.isNotBlank() }
            if (companyName != null) {
                item {
                    SectionCard(title = "Company", icon = Icons.Filled.Business) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    navController.navigate(
                                        Screen.CompanyHistoryDetail.routeWith(
                                            companyKey = companyKeyForBill(b),
                                            companyName = companyName
                                        )
                                    )
                                },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = companyName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Icon(Icons.Filled.ChevronRight, contentDescription = "View", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "This year", icon = Icons.Filled.CalendarToday) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Total paid so far",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        MoneyText(
                            amount = b.annualTotalPaidSoFar(),
                            style = MaterialTheme.typography.titleMedium,
                            color = ProfitGreen
                        )
                    }
                    val nextDue = b.nextDueDateMillis()
                    if (nextDue != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Next due",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(nextDue)),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (!b.isPaidForCurrentCycle()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (b.isCreditOrLoan()) showCreditLoanPaymentDialog = true
                                else viewModel.recordPayment(b)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = ProfitGreen)
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mark as paid")
                        }
                    }
                }
            }

            item {
                    var showFullPaymentHistory by remember { mutableStateOf(false) }
                    val paymentsReversed = b.paymentHistory.reversed()
                    val displayPayments = if (showFullPaymentHistory) paymentsReversed else paymentsReversed.take(10)
                    val hasMore = paymentsReversed.size > 10
                    SectionCard(title = "Payment history", icon = Icons.Filled.History) {
                        if (b.paymentHistory.isEmpty()) {
                            Text(
                                text = "No payments recorded yet. Tap \"Mark as paid\" when you pay this bill.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            displayPayments.forEach { payment ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(payment.date)),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    MoneyText(
                                        amount = payment.amount,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ProfitGreen
                                    )
                                }
                            }
                            if (hasMore && !showFullPaymentHistory) {
                                TextButton(
                                    onClick = { showFullPaymentHistory = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("View entire history (${b.paymentHistory.size} payments)")
                                }
                            } else if (hasMore && showFullPaymentHistory) {
                                TextButton(
                                    onClick = { showFullPaymentHistory = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Show less")
                                }
                            }
                        }
                    }
                }

            item {
                SectionCard(title = "Statements", icon = Icons.Filled.AttachFile) {
                    val statements = b.statementsOrderedByDate()
                    if (statements.isEmpty()) {
                        Text(
                            text = "No statements attached. Add one when editing the bill.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        statements.forEach { entry ->
                            StatementRow(
                                entry = entry,
                                onView = {
                                    scope.launch {
                                        viewModel.getStatementBytes(entry.hash)
                                            .onSuccess { bytes ->
                                                val ext = when {
                                                    entry.label.contains(".pdf", ignoreCase = true) -> "pdf"
                                                    entry.label.contains("png", ignoreCase = true) -> "png"
                                                    entry.label.contains("jpg", ignoreCase = true) -> "jpg"
                                                    else -> "bin"
                                                }
                                                val file = File(context.cacheDir, "statement_${entry.hash.take(8)}.$ext")
                                                file.writeBytes(bytes)
                                                val uri = Uri.fromFile(file)
                                                val mime = when (ext) {
                                                    "pdf" -> "application/pdf"
                                                    "png", "jpg" -> "image/*"
                                                    else -> "application/octet-stream"
                                                }
                                                try {
                                                    context.startActivity(
                                                        Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    )
                                                } catch (_: Exception) {
                                                    context.startActivity(
                                                        Intent.createChooser(
                                                            Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri),
                                                            "Open statement"
                                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    )
                                                }
                                            }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (b.notes.isNotBlank()) {
                item {
                    SectionCard(title = "Notes", icon = Icons.Filled.Notes) {
                        Text(
                            text = b.notes,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    if (showDeleteConfirm && billWithSource != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete bill?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBill(billWithSource!!)
                        showDeleteConfirm = false
                        navController.popBackStack()
                    }
                ) { Text("Delete", color = LossRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showEditDialog) {
        val editingBill = bill
        if (editingBill != null) {
            BillDialog(
                bill = editingBill,
                creditAccounts = creditAccounts,
                bankAccounts = bankAccounts,
                billers = billers,
                isEditingCypherLog = billWithSource?.isCypherLog == true,
                statementCount = editingBill.statementEntries.size,
                onDismiss = { showEditDialog = false },
                onSave = { b, _ ->
                    viewModel.saveBill(b)
                    showEditDialog = false
                },
                onUploadAttachment = { _, _, _ -> }
            )
        }
    }

    if (showCreditLoanPaymentDialog && bill != null) {
        val b = bill!!
        val currentBalance = b.creditCardDetails?.currentBalance ?: linkedCreditAccount?.currentBalance ?: 0.0
        var amountStr by remember(b) { mutableStateOf("%.2f".format(b.effectiveAmountDue())) }
        var newBalanceStr by remember { mutableStateOf("") }
        val amount = amountStr.toDoubleOrNull() ?: 0.0
        val newBalance = newBalanceStr.toDoubleOrNull()
        AlertDialog(
            onDismissRequest = { showCreditLoanPaymentDialog = false },
            title = { Text("Record payment — ${b.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Enter the amount paid. Optionally set the new balance (e.g. from a statement); otherwise the balance will be reduced by the amount paid.",
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
                        placeholder = { Text("Leave blank to subtract from current (${currentBalance.formatCurrency()})") },
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
                            viewModel.recordPaymentWithAmount(b, amount, if (newBalance != null && newBalance >= 0) newBalance else null)
                            showCreditLoanPaymentDialog = false
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showCreditLoanPaymentDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun companyKeyForBill(bill: Bill): String {
    val billerId = bill.linkedBillerId?.takeIf { it.isNotBlank() }
    if (billerId != null) return "id:$billerId"
    val label = bill.billerName.ifBlank { bill.accountName }.trim()
    val normalized = label.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), " ").trim()
    return "name:$normalized"
}

@Composable
private fun StatementRow(
    entry: StatementEntry,
    onView: () -> Unit
) {
    val dateStr = if (entry.addedAt > 0)
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(entry.addedAt))
    else "—"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.label.ifBlank { "Statement" },
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = dateStr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onView) { Text("View") }
    }
}
