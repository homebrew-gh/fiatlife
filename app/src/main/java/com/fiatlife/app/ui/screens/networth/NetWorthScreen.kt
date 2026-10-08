package com.fiatlife.app.ui.screens.networth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.fiatlife.app.domain.model.BankAccount
import com.fiatlife.app.domain.model.Bitcoin
import com.fiatlife.app.domain.model.BitcoinWallet
import com.fiatlife.app.domain.model.CreditAccount
import com.fiatlife.app.domain.model.CreditAccountType
import com.fiatlife.app.ui.components.SectionCard
import com.fiatlife.app.ui.components.formatCurrency
import com.fiatlife.app.ui.navigation.Screen
import com.fiatlife.app.ui.theme.LossRed
import com.fiatlife.app.ui.theme.ProfitGreen
import com.fiatlife.app.ui.viewmodel.NetWorthViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class BreakdownItem(
    val key: String,
    val label: String,
    val detail: String?,
    val value: String,
    val negative: Boolean = false,
    val onClick: (() -> Unit)? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetWorthScreen(
    navController: NavController,
    viewModel: NetWorthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val summary = state.summary
    val usdPerBtc = state.price?.usd

    var editing by remember { mutableStateOf<BitcoinWallet?>(null) }
    var dialogOpen by remember { mutableStateOf(false) }
    val openWallet: (BitcoinWallet?) -> Unit = { wallet ->
        editing = wallet
        dialogOpen = true
    }

    val toSettings = { navController.navigate(Screen.Settings.route) }
    val toDebt: (String) -> Unit = { id -> navController.navigate(Screen.DebtDetail.routeWithId(id)) }

    val isPricedMortgage = { a: CreditAccount -> a.type == CreditAccountType.MORTGAGE && a.homePrice > 0.0 }
    val cashItems = bankItems(state.bankAccounts, cash = true, onClick = toSettings)
    val investedItems = bankItems(state.bankAccounts, cash = false, onClick = toSettings)
    val walletItems = state.wallets.sortedByDescending { it.sats }.map { w ->
        BreakdownItem(
            key = w.id,
            label = w.name,
            detail = Bitcoin.walletDetail(w),
            value = usdPerBtc?.let { Bitcoin.satsToUsd(w.sats, it).formatCurrency() } ?: "—",
            onClick = { openWallet(w) }
        )
    }
    val homeItems = state.creditAccounts.filter(isPricedMortgage).map { a ->
        val owed = a.currentBalance.coerceAtLeast(0.0)
        val equity = a.homePrice - owed
        BreakdownItem(
            key = a.id,
            label = a.name,
            detail = "${a.homePrice.formatCurrency()} purchase price − ${owed.formatCurrency()} owed",
            value = equity.formatCurrency(),
            negative = equity < 0,
            onClick = { toDebt(a.id) }
        )
    }
    val debtItems = state.creditAccounts
        .filter { !isPricedMortgage(it) && it.currentBalance > 0 }
        .sortedByDescending { it.currentBalance }
        .map { a ->
            BreakdownItem(
                key = a.id,
                label = a.name,
                detail = syncedDetail(a.type.displayName, a.simplefinBalanceAsOf.takeIf { a.simplefinAccountKey != null }),
                value = "−${a.currentBalance.formatCurrency()}",
                negative = true,
                onClick = { toDebt(a.id) }
            )
        }
    val retirementSats = state.wallets.filter { it.retirement }.sumOf { it.sats }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Net Worth") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refreshPrice, enabled = !refreshing) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh BTC price")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.loading) {
                Text("Loading net worth…", style = MaterialTheme.typography.bodyMedium)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "NET WORTH",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        summary.total.formatCurrency(),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (summary.total >= 0) ProfitGreen else LossRed
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Assets ${summary.assets.formatCurrency()} · Debts ${summary.debt.formatCurrency()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    if (summary.bitcoinUsd == null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "BTC price unavailable — bitcoin isn't included yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            SectionCard(title = "Breakdown", icon = Icons.AutoMirrored.Filled.ShowChart) {
                BreakdownRow(
                    label = "Cash",
                    detail = "Checking & savings",
                    value = summary.cash.formatCurrency(),
                    items = cashItems,
                    emptyText = "No checking or savings accounts yet. Add them in Settings."
                )
                BreakdownRow(
                    label = "Investments",
                    detail = "Retirement & brokerage",
                    value = summary.invested.formatCurrency(),
                    items = investedItems,
                    emptyText = "No retirement or investment accounts yet. Add them in Settings."
                )
                BreakdownRow(
                    label = "Bitcoin",
                    detail = if (retirementSats > 0) {
                        "${Bitcoin.formatBtc(summary.bitcoinSats)} BTC · ${Bitcoin.formatBtc(retirementSats)} in retirement"
                    } else {
                        "${Bitcoin.formatBtc(summary.bitcoinSats)} BTC"
                    },
                    value = summary.bitcoinUsd?.formatCurrency() ?: "—",
                    items = walletItems,
                    emptyText = "No wallets yet. Add one below."
                )
                if (summary.homeValue > 0) {
                    BreakdownRow(
                        label = "Home equity",
                        detail = "${summary.homeValue.formatCurrency()} purchase price − ${summary.mortgageDebt.formatCurrency()} owed",
                        value = summary.homeEquity.formatCurrency(),
                        negative = summary.homeEquity < 0,
                        items = homeItems,
                        emptyText = "No mortgages with a home price."
                    )
                }
                BreakdownRow(
                    label = "Debts",
                    detail = if (summary.homeValue > 0) "Cards & loans" else "Cards, loans & mortgage",
                    value = "−${summary.otherDebt.formatCurrency()}",
                    negative = summary.otherDebt > 0,
                    items = debtItems,
                    emptyText = "No balances owed.",
                    showDivider = false
                )
                if (summary.missingHomePrice) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your mortgage has no home price, so it only counts as debt. " +
                            "Add the purchase price on the mortgage's Debt page to count your equity.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionCard(
                title = "Bitcoin",
                icon = Icons.Default.CurrencyBitcoin,
                action = {
                    TextButton(onClick = { openWallet(null) }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add wallet")
                    }
                }
            ) {
                Text(
                    state.price?.let { "BTC ${it.usd.formatCurrency()} · ${it.source} · ${formatAsOf(it.fetchedAtMs)}" }
                        ?: state.priceError
                        ?: "Loading BTC price…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                if (state.wallets.isEmpty()) {
                    Text(
                        "No wallets yet. Add one per wallet or exchange and update the amount whenever it changes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        state.wallets.forEachIndexed { index, wallet ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            val updated = wallet.updatedAt.takeIf { it > 0 }?.let { " · updated ${formatAsOf(it)}" }.orEmpty()
                            ItemRow(
                                BreakdownItem(
                                    key = wallet.id,
                                    label = wallet.name,
                                    detail = Bitcoin.walletDetail(wallet) + updated,
                                    value = usdPerBtc?.let { Bitcoin.satsToUsd(wallet.sats, it).formatCurrency() } ?: "—",
                                    onClick = { openWallet(wallet) }
                                ),
                                horizontalPadding = 16.dp,
                                verticalPadding = 12.dp
                            )
                        }
                    }
                }
            }
        }
    }

    if (dialogOpen) {
        BitcoinWalletDialog(
            wallet = editing,
            usdPerBtc = usdPerBtc,
            onDismiss = {
                dialogOpen = false
                editing = null
            },
            onSave = { name, sats, notes, retirement ->
                val base = editing
                viewModel.saveWallet(
                    BitcoinWallet(
                        id = base?.id.orEmpty(),
                        name = name,
                        sats = sats,
                        notes = notes,
                        retirement = retirement,
                        createdAt = base?.createdAt ?: 0L
                    )
                )
                dialogOpen = false
                editing = null
            },
            onDelete = editing?.takeIf { it.id.isNotEmpty() }?.let { wallet ->
                {
                    viewModel.deleteWallet(wallet)
                    dialogOpen = false
                    editing = null
                }
            }
        )
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    detail: String?,
    value: String,
    items: List<BreakdownItem>,
    emptyText: String,
    negative: Boolean = false,
    showDivider: Boolean = true
) {
    var open by rememberSaveable(label) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (open) 90f else 0f, label = "chevron")
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = if (open) "Collapse" else "Expand",
                modifier = Modifier.size(20.dp).rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                if (detail != null) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
        AnimatedVisibility(visible = open) {
            Column(Modifier.padding(start = 28.dp, bottom = 12.dp)) {
                if (items.isEmpty()) {
                    Text(
                        emptyText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        items.forEachIndexed { index, item ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            ItemRow(item)
                        }
                    }
                }
            }
        }
        if (showDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ItemRow(
    item: BreakdownItem,
    horizontalPadding: androidx.compose.ui.unit.Dp = 12.dp,
    verticalPadding: androidx.compose.ui.unit.Dp = 8.dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (item.onClick != null) Modifier.clickable(onClick = item.onClick) else Modifier)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.detail != null) {
                Text(
                    item.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            item.value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (item.negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BitcoinWalletDialog(
    wallet: BitcoinWallet?,
    usdPerBtc: Double?,
    onDismiss: () -> Unit,
    onSave: (name: String, sats: Long, notes: String, retirement: Boolean) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(wallet?.name.orEmpty()) }
    var amount by remember { mutableStateOf(wallet?.let { Bitcoin.formatBtc(it.sats).replace(",", "") }.orEmpty()) }
    var notes by remember { mutableStateOf(wallet?.notes.orEmpty()) }
    var retirement by remember { mutableStateOf(wallet?.retirement ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val sats = Bitcoin.parseBtcToSats(amount)
    val amountError = amount.isNotBlank() && sats == null
    val canSave = name.isNotBlank() && sats != null

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete wallet?") },
            text = { Text("\"${wallet?.name}\" will be removed from your net worth on every device.") },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (wallet == null) "Add wallet" else "Edit wallet") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Cold storage") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (BTC)") },
                    placeholder = { Text("0.05") },
                    singleLine = true,
                    isError = amountError,
                    supportingText = {
                        when {
                            amountError -> Text("Enter a BTC amount with up to 8 decimals.")
                            sats != null && usdPerBtc != null ->
                                Text("≈ ${Bitcoin.satsToUsd(sats, usdPerBtc).formatCurrency()}")
                            else -> {}
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { retirement = !retirement },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = retirement, onCheckedChange = { retirement = it })
                    Column {
                        Text("Retirement account", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "e.g. a bitcoin IRA",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete wallet", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (sats != null) onSave(name.trim(), sats, notes.trim(), retirement) },
                enabled = canSave
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun bankItems(
    accounts: List<BankAccount>,
    cash: Boolean,
    onClick: () -> Unit
): List<BreakdownItem> {
    val rows = accounts.filter { it.isCash == cash }
    val withBalance = rows.filter { it.balance != null }.sortedByDescending { it.balance }
    val withoutBalance = rows.filter { it.balance == null }
    return (withBalance + withoutBalance).map { a ->
        BreakdownItem(
            key = a.id,
            label = a.name,
            detail = syncedDetail(a.accountType.displayName, a.balanceAsOf.takeIf { a.simplefinAccountKey != null }),
            value = a.balance?.formatCurrency() ?: "No balance",
            negative = (a.balance ?: 0.0) < 0,
            onClick = onClick
        )
    }
}

private fun syncedDetail(typeLabel: String, syncedAt: Long?): String =
    if (syncedAt != null && syncedAt > 0) "$typeLabel · synced ${formatAsOf(syncedAt)}" else typeLabel

/** "today 3:04 PM", "yesterday", "Mar 4", or "Mar 4, 2025" for older years. */
private fun formatAsOf(ms: Long): String {
    val then = Calendar.getInstance().apply { timeInMillis = ms }
    val now = Calendar.getInstance()
    val sameYear = then.get(Calendar.YEAR) == now.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> "today " + SimpleDateFormat("h:mm a", Locale.US).format(Date(ms))
        sameYear && dayDiff == 1 -> "yesterday"
        sameYear -> SimpleDateFormat("MMM d", Locale.US).format(Date(ms))
        else -> SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(ms))
    }
}
