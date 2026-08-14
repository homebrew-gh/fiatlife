package com.fiatlife.app.ui.screens.debt

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
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
import com.fiatlife.app.domain.model.CreditAccount
import com.fiatlife.app.domain.model.CreditStatementUpdate
import com.fiatlife.app.domain.model.StatementEntry
import com.fiatlife.app.domain.model.formatMonths
import com.fiatlife.app.domain.model.formatPayoffDate
import com.fiatlife.app.domain.model.monthlyInterest
import com.fiatlife.app.domain.model.projectPayoff
import com.fiatlife.app.ui.components.MoneyText
import com.fiatlife.app.ui.components.CurrencyTextField
import com.fiatlife.app.ui.navigation.Screen
import com.fiatlife.app.ui.components.SectionCard
import com.fiatlife.app.ui.components.formatCurrency
import com.fiatlife.app.ui.viewmodel.DebtDetailViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtDetailScreen(
    navController: NavController,
    viewModel: DebtDetailViewModel = hiltViewModel()
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val linkedBill by viewModel.linkedBill.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showStatementDialog by remember { mutableStateOf(false) }
    var hasLoadedAccount by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                val bytes = stream.readBytes()
                val mimeType = context.contentResolver.getType(it) ?: "application/octet-stream"
                val fileName = "statement_${System.currentTimeMillis()}"
                account?.let { acc -> viewModel.uploadAndAddStatement(acc, bytes, mimeType, fileName) }
            }
        }
    }

    LaunchedEffect(account) {
        if (account != null) {
            hasLoadedAccount = true
        } else if (hasLoadedAccount) {
            navController.popBackStack()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(account?.name ?: "Account") },
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
        val acc = account ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            content = {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = acc.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        AssistChip(
                            onClick = {},
                            label = { Text(acc.type.displayName, style = MaterialTheme.typography.labelMedium) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        MoneyText(
                            amount = acc.currentBalance,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${acc.effectiveMonthlyPayment().formatCurrency()}/mo · Due day ${acc.dueDay}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        if (acc.isPromotionActive()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        "${"%.2f".format((acc.promotionalApr ?: 0.0) * 100)}% promo · " +
                                            "${acc.monthsUntilPromotionEnds()} mo left"
                                    )
                                }
                            )
                            acc.paymentToClearPromotion()?.let { required ->
                                Text(
                                    text = "${required.formatCurrency()}/mo clears this balance " +
                                        "before expiry" +
                                        if (acc.deferredInterest) {
                                            " · deferred interest may apply"
                                        } else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (
                                        required > acc.effectiveMonthlyPayment() ||
                                        acc.deferredInterest
                                    ) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = { showStatementDialog = true }) {
                            Text("Update statement")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (acc.apr > 0 && acc.currentBalance > 0) {
                    PayoffProjectionCard(acc)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (linkedBill != null) {
                    val bill = linkedBill!!
                    SectionCard(title = "Tracked in Bills", icon = Icons.Filled.Receipt) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navController.navigate(Screen.BillDetail.routeWithId(bill.id)) },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = bill.name,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Icon(Icons.Filled.ChevronRight, contentDescription = "View", modifier = Modifier.size(20.dp))
                                }
                                Text(
                                    text = "See payment history in Bills",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                SectionCard(title = "Overview") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Current balance", style = MaterialTheme.typography.bodyMedium)
                            MoneyText(amount = acc.currentBalance, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (acc.type.isRevolving && acc.creditLimit > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Credit limit", style = MaterialTheme.typography.bodyMedium)
                                MoneyText(amount = acc.creditLimit, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Available", style = MaterialTheme.typography.bodyMedium)
                                MoneyText(
                                    amount = (acc.creditLimit - acc.currentBalance).coerceAtLeast(0.0),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        if (acc.effectiveApr() > 0 || acc.isPromotionActive()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Current APR", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "%.2f%%".format(acc.effectiveApr() * 100),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Due day", style = MaterialTheme.typography.bodyMedium)
                            Text("${acc.dueDay}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (acc.type.isRevolving) "Minimum due" else "Monthly payment",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            MoneyText(
                                amount = acc.effectiveMonthlyPayment(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (acc.type.isAmortizing) {
                            acc.originalPrincipal.takeIf { it > 0 }?.let { principal ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Original principal", style = MaterialTheme.typography.bodyMedium)
                                    MoneyText(amount = principal, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            acc.termMonths?.let { term ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Term", style = MaterialTheme.typography.bodyMedium)
                                    Text("$term months", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        if (acc.institution.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Institution", style = MaterialTheme.typography.bodyMedium)
                                Text(acc.institution, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (acc.notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Notes",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(acc.notes, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                SectionCard(title = "Statements", icon = Icons.Filled.AttachFile) {
                    val statements = acc.statementEntries.sortedByDescending { it.addedAt }
                    if (statements.isEmpty()) {
                        Text(
                            text = "No statements attached.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { filePicker.launch("*/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.AttachFile, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Attach statement")
                        }
                    } else {
                        statements.forEach { entry ->
                            DebtStatementRow(
                                entry = entry,
                                onView = {
                                    scope.launch {
                                        viewModel.getStatementBytes(entry.hash).onSuccess { bytes ->
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
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { filePicker.launch("*/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Attach another")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        )
    }

    val accountForDialog = account
    if (showDeleteConfirm && accountForDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete account?") },
            text = { Text("This will remove \"${accountForDialog.name}\" and cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAccount(accountForDialog)
                        showDeleteConfirm = false
                        navController.popBackStack()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showEditDialog && accountForDialog != null) {
        CreditAccountDialog(
            account = accountForDialog,
            onDismiss = { showEditDialog = false },
            onSave = { acc ->
                viewModel.saveAccount(acc)
                showEditDialog = false
            },
            isSaving = false
        )
    }

    if (showStatementDialog && accountForDialog != null) {
        UpdateStatementDialog(
            account = accountForDialog,
            onDismiss = { showStatementDialog = false },
            onSave = { update ->
                viewModel.updateStatement(accountForDialog, update)
                showStatementDialog = false
            }
        )
    }
}

@Composable
internal fun UpdateStatementDialog(
    account: CreditAccount,
    onDismiss: () -> Unit,
    onSave: (CreditStatementUpdate) -> Unit
) {
    val today = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
    var statementBalance by remember(account.id) {
        mutableStateOf(account.currentBalance.toString())
    }
    var statementDate by remember(account.id) {
        mutableStateOf(
            account.statementBalanceAsOfMillis?.let {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it))
            } ?: today
        )
    }
    var amountDue by remember(account.id) {
        mutableStateOf(account.effectiveAmountDue().toString())
    }
    var useFormula by remember(account.id) {
        mutableStateOf(account.statementAmountDue == null)
    }
    var dueDay by remember(account.id) { mutableStateOf(account.dueDay.toString()) }
    var paymentAmount by remember(account.id) { mutableStateOf("") }
    var balanceAfter by remember(account.id) {
        mutableStateOf(account.currentBalance.toString())
    }
    var error by remember(account.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update statement") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Debt owns the balance; the linked Bill tracks this amount due and its payments.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CurrencyTextField(
                    value = statementBalance,
                    onValueChange = {
                        statementBalance = it
                        if (paymentAmount.isBlank()) balanceAfter = it
                    },
                    label = "Statement balance"
                )
                OutlinedTextField(
                    value = statementDate,
                    onValueChange = { statementDate = it.take(10) },
                    label = { Text("Statement date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                CurrencyTextField(
                    value = if (useFormula) account.minimumDue().toString() else amountDue,
                    onValueChange = {
                        useFormula = false
                        amountDue = it
                    },
                    label = "Amount due this cycle"
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = useFormula,
                        onCheckedChange = {
                            useFormula = it
                            if (it) amountDue = account.minimumDue().toString()
                        }
                    )
                    Text("Use minimum-payment formula (${account.minimumDue().formatCurrency()})")
                }
                OutlinedTextField(
                    value = dueDay,
                    onValueChange = { dueDay = it.filter(Char::isDigit).take(2) },
                    label = { Text("Due day (1–31)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                CurrencyTextField(
                    value = paymentAmount,
                    onValueChange = {
                        paymentAmount = it
                        val balance = statementBalance.toDoubleOrNull() ?: 0.0
                        val payment = it.toDoubleOrNull() ?: 0.0
                        balanceAfter = (balance - payment).coerceAtLeast(0.0).toString()
                    },
                    label = "Payment made now (optional)"
                )
                CurrencyTextField(
                    value = balanceAfter,
                    onValueChange = { balanceAfter = it },
                    label = "Balance after payment"
                )
                if (paymentAmount.isNotBlank()) {
                    Text(
                        "Records ${(paymentAmount.toDoubleOrNull() ?: 0.0).formatCurrency()} " +
                            "and sets the balance to " +
                            (balanceAfter.toDoubleOrNull() ?: 0.0).formatCurrency(),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val balance = statementBalance.toDoubleOrNull()
                    val asOf = runCatching {
                        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                            isLenient = false
                        }.parse(statementDate)?.time
                    }.getOrNull()
                    val due = if (useFormula) null else amountDue.toDoubleOrNull()
                    val day = dueDay.toIntOrNull()
                    val after = balanceAfter.toDoubleOrNull()
                    if (balance == null || balance < 0 || asOf == null ||
                        (!useFormula && (due == null || due < 0)) ||
                        day == null || day !in 1..31 ||
                        after == null || after < 0
                    ) {
                        error = "Check the balance, dates, amount due, and due day."
                    } else {
                        onSave(
                            CreditStatementUpdate(
                                statementBalance = balance,
                                statementBalanceAsOfMillis = asOf,
                                statementAmountDue = due,
                                dueDay = day,
                                paymentAmount =
                                    (paymentAmount.toDoubleOrNull() ?: 0.0)
                                        .coerceAtLeast(0.0),
                                balanceAfterPayment = after
                            )
                        )
                    }
                }
            ) { Text("Save statement") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun PayoffProjectionCard(account: CreditAccount) {
    val interest = account.monthlyInterest()
    val payment = account.effectiveMonthlyPayment()
    val proj = account.projectPayoff()

    SectionCard(title = "Payoff Projection", icon = Icons.AutoMirrored.Filled.TrendingDown) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!proj.feasible) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Minimum payment trap",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "At ${payment.formatCurrency()}/mo this balance won't be paid off — " +
                                "about ${interest.formatCurrency()} of that goes to interest each month. " +
                                "Increase the payment to make progress.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Interest this month", style = MaterialTheme.typography.bodyMedium)
                MoneyText(amount = interest, style = MaterialTheme.typography.bodyMedium)
            }
            if (proj.feasible) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Debt-free", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = proj.payoffDateMillis?.let {
                            "${formatPayoffDate(it)} · ${formatMonths(proj.months)}"
                        } ?: "—",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total interest", style = MaterialTheme.typography.bodyMedium)
                    MoneyText(amount = proj.totalInterest, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                text = "Estimated at the current ${payment.formatCurrency()}/mo payment.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DebtStatementRow(
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
