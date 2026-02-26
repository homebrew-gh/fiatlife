package com.fiatlife.app.ui.screens.bills

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.fiatlife.app.ui.components.EmptyState
import com.fiatlife.app.ui.components.formatCurrency
import com.fiatlife.app.domain.model.BillFrequency
import com.fiatlife.app.domain.model.BillGeneralCategory
import com.fiatlife.app.ui.navigation.Screen
import com.fiatlife.app.ui.viewmodel.CompanyHistoryViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyHistoryDetailScreen(
    navController: NavController,
    companyKey: String,
    companyName: String,
    viewModel: CompanyHistoryViewModel = hiltViewModel()
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val payments = state.paymentsByCompanyKey[companyKey].orEmpty().sortedByDescending { it.paidDate }
    val statements = state.statementsByCompanyKey[companyKey].orEmpty().sortedByDescending { it.addedAt }
    val companyBills = state.billsByCompanyKey[companyKey].orEmpty()
    val subscriptions = companyBills.filter { it.effectiveGeneralCategory == BillGeneralCategory.SUBSCRIPTION }
    val companyMeta = state.companies.firstOrNull { it.key == companyKey }
    val isArchived = companyMeta?.isArchived == true
    var selectedBillId by remember(companyKey) { mutableStateOf(companyBills.firstOrNull()?.id.orEmpty()) }
    var billDropdownExpanded by remember { mutableStateOf(false) }
    var showDeleteCompanyConfirm by remember { mutableStateOf(false) }
    var activateBillId by remember { mutableStateOf<String?>(null) }
    var activateFrequency by remember { mutableStateOf(BillFrequency.MONTHLY) }
    var activateDate by remember { mutableStateOf("") }
    var activateFrequencyExpanded by remember { mutableStateOf(false) }
    val message = state.message
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                val bytes = stream.readBytes()
                val mimeType = context.contentResolver.getType(it) ?: "application/octet-stream"
                val fileName = "statement_${System.currentTimeMillis()}"
                viewModel.uploadStatementForCompany(
                    companyKey = companyKey,
                    bytes = bytes,
                    contentType = mimeType,
                    filename = fileName,
                    targetBillId = selectedBillId.takeIf { id -> id.isNotBlank() }
                )
            }
        }
    }

    LaunchedEffect(message) {
        if (message.isNotBlank()) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(companyName) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            viewModel.setCompanyArchived(companyKey, companyName, archived = !isArchived)
                        }
                    ) {
                        Text(if (isArchived) "Unarchive company" else "Archive company")
                    }
                    OutlinedButton(onClick = { showDeleteCompanyConfirm = true }) {
                        Text("Delete company")
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Deletion is permanent locally. Nostr events may still exist depending on relay deletion policy.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (subscriptions.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Subscriptions",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                items(subscriptions, key = { it.id }) { sub ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = sub.name,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (sub.isCancelled) "Cancelled" else "Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!sub.isCancelled && sub.canSkipInterval()) {
                                    OutlinedButton(
                                        onClick = { viewModel.skipSubscriptionInterval(companyKey, sub.id) }
                                    ) { Text("Skip interval") }
                                }
                                if (!sub.isCancelled) {
                                    OutlinedButton(
                                        onClick = { viewModel.cancelSubscription(companyKey, sub.id) }
                                    ) { Text("Cancel") }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            activateBillId = sub.id
                                            activateFrequency = sub.frequency
                                            activateDate = sub.nextDueDateMillis()?.let { millisToIsoDate(it) } ?: ""
                                        }
                                    ) { Text("Activate") }
                                }
                            }
                        }
                    }
                }
            }

            if (payments.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Receipt,
                        title = "No activity yet",
                        subtitle = "Payments and statements for this company appear here."
                    )
                }
            } else {
                item {
                    Text(
                        text = "Paid history",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                items(payments, key = { it.id }) { payment ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = payment.billName,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = dateFormat.format(Date(payment.paidDate)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = payment.amount.formatCurrency(),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            TextButton(
                                onClick = {
                                    navController.navigate(Screen.BillDetail.routeWithId(payment.billId))
                                },
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text("Open bill")
                            }
                            if (payment.hasInvoiceOrStatement) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Has statement/invoice attachment",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Statements & attachments",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (companyBills.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = billDropdownExpanded,
                        onExpandedChange = { billDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = companyBills.firstOrNull { it.id == selectedBillId }?.name ?: "Latest bill",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Upload to bill") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = billDropdownExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = billDropdownExpanded,
                            onDismissRequest = { billDropdownExpanded = false }
                        ) {
                            companyBills.forEach { bill ->
                                DropdownMenuItem(
                                    text = { Text(bill.name) },
                                    onClick = {
                                        selectedBillId = bill.id
                                        billDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { filePicker.launch("*/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Upload statement/attachment")
                    }
                }
            }

            if (statements.isEmpty()) {
                item {
                    Text(
                        text = "No statements or attachments yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(statements, key = { it.id }) { row ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = row.label,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${row.billName} · ${dateFormat.format(Date(row.addedAt))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            TextButton(
                                onClick = { navController.navigate(Screen.BillDetail.routeWithId(row.billId)) }
                            ) {
                                Text("Open bill")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteCompanyConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteCompanyConfirm = false },
            title = { Text("Delete company?") },
            text = {
                Text(
                    "This will delete all company-associated bills, payments, and attachments in FiatLife. " +
                        "Relay-side Nostr history may still remain depending on relay policy."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCompany(companyKey, companyName)
                        showDeleteCompanyConfirm = false
                        navController.popBackStack()
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCompanyConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (activateBillId != null) {
        AlertDialog(
            onDismissRequest = { activateBillId = null },
            title = { Text("Reactivate subscription") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Review details and confirm new billing schedule.")
                    ExposedDropdownMenuBox(
                        expanded = activateFrequencyExpanded,
                        onExpandedChange = { activateFrequencyExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = activateFrequency.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Frequency") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(activateFrequencyExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = activateFrequencyExpanded,
                            onDismissRequest = { activateFrequencyExpanded = false }
                        ) {
                            BillFrequency.entries.forEach { freq ->
                                DropdownMenuItem(
                                    text = { Text(freq.displayName) },
                                    onClick = {
                                        activateFrequency = freq
                                        activateFrequencyExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = activateDate,
                        onValueChange = { activateDate = it.take(10) },
                        label = { Text("New billing date (YYYY-MM-DD)") },
                        placeholder = { Text("2026-03-15") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = parseIsoDate(activateDate)
                        val id = activateBillId
                        if (millis != null && id != null) {
                            viewModel.reactivateSubscription(
                                companyKey = companyKey,
                                billId = id,
                                frequency = activateFrequency,
                                newBillingDateMillis = millis
                            )
                            activateBillId = null
                        }
                    },
                    enabled = parseIsoDate(activateDate) != null
                ) { Text("Activate") }
            },
            dismissButton = {
                TextButton(onClick = { activateBillId = null }) { Text("Cancel") }
            }
        )
    }
}

private fun parseIsoDate(input: String): Long? {
    val parts = input.trim().split("-")
    if (parts.size != 3) return null
    val y = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    val d = parts[2].toIntOrNull() ?: return null
    val cal = Calendar.getInstance()
    cal.set(Calendar.YEAR, y)
    cal.set(Calendar.MONTH, (m - 1).coerceIn(0, 11))
    cal.set(Calendar.DAY_OF_MONTH, d.coerceAtLeast(1))
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun millisToIsoDate(millis: Long): String {
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
