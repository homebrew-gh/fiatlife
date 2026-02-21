package com.fiatlife.app.ui.screens.bills

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.fiatlife.app.domain.model.Bill
import com.fiatlife.app.domain.model.StatementEntry
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var hasLoadedBill by remember { mutableStateOf(false) }

    LaunchedEffect(bill) {
        if (bill != null) {
            hasLoadedBill = true
        } else if (hasLoadedBill) {
            navController.popBackStack()
        }
    }

    Scaffold(
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
                        modifier = Modifier.padding(20.dp),
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
                        Text(
                            text = if (b.isCreditCard()) "Minimum due · Due day ${b.dueDay}"
                            else "${b.frequency.displayName} · Due day ${b.dueDay}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
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
                    if (!b.isPaid && billWithSource?.isCypherLog != true) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.recordPayment(b) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mark as paid")
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
