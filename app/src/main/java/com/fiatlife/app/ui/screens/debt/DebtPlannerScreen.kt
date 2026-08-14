package com.fiatlife.app.ui.screens.debt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.fiatlife.app.domain.model.CreditAccount
import com.fiatlife.app.domain.model.PayoffStrategy
import com.fiatlife.app.domain.model.buildDebtPlan
import com.fiatlife.app.domain.model.formatMonths
import com.fiatlife.app.domain.model.formatPayoffDate
import com.fiatlife.app.domain.model.promoExpiryWarnings
import com.fiatlife.app.ui.components.MoneyText
import com.fiatlife.app.ui.components.SectionCard
import com.fiatlife.app.ui.components.formatCurrency
import com.fiatlife.app.ui.viewmodel.DebtPlannerViewModel

private val EXTRA_PRESETS = listOf(0.0, 50.0, 100.0, 250.0, 500.0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtPlannerScreen(
    navController: NavController,
    viewModel: DebtPlannerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var strategy by remember { mutableStateOf(PayoffStrategy.AVALANCHE) }
    var extra by remember { mutableStateOf(100.0) }
    val extraText = remember { mutableStateMapOf<String, String>() }

    val payable = state.accounts
        .filter { it.currentBalance > 0.005 }
        .sortedWith(compareBy<CreditAccount> { !it.type.isRevolving }.thenBy { it.name.lowercase() })
    val perAccountExtra = extraText
        .mapNotNull { (id, text) -> text.toDoubleOrNull()?.takeIf { it > 0 }?.let { id to it } }
        .toMap()
    val plan = remember(state.accounts, strategy, extra, perAccountExtra) {
        buildDebtPlan(state.accounts, strategy, extra, perAccountExtra)
    }
    val promoWarnings = remember(state.accounts, plan) {
        promoExpiryWarnings(state.accounts, plan)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Debt Planner") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                Text("Loading accounts…", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            if (payable.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "No interest-bearing debt",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Add a credit card or loan with an APR and balance to build a payoff plan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                return@Column
            }

            SectionCard(title = "Strategy") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PayoffStrategy.entries.forEach { s ->
                        val selected = s == strategy
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            onClick = { strategy = s }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selected, onClick = { strategy = s })
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        s.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        s.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SectionCard(title = "Extra Per Month") {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Extra payment", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            extra.formatCurrency(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Slider(
                        value = extra.toFloat(),
                        onValueChange = { extra = it.toDouble() },
                        valueRange = 0f..2000f,
                        steps = 79
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EXTRA_PRESETS.forEach { amount ->
                            FilterChip(
                                selected = extra == amount,
                                onClick = { extra = amount },
                                label = {
                                    Text(if (amount == 0.0) "Minimums" else "+${amount.formatCurrency()}")
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Total budget: ${plan.monthlyBudget.formatCurrency()}/mo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Plan Result",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(12.dp))
                    if (plan.feasible) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Debt-Free",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                Text(
                                    plan.debtFreeDateMillis?.let { formatPayoffDate(it) } ?: "—",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    formatMonths(plan.months),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Total Interest",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                MoneyText(
                                    amount = plan.totalInterest,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        if (plan.interestSaved > 0 || plan.monthsSaved > 0) {
                            Spacer(Modifier.height(12.dp))
                            val parts = buildList {
                                if (plan.interestSaved > 0) add("Saves ${plan.interestSaved.formatCurrency()} in interest")
                                if (plan.monthsSaved > 0) add("${formatMonths(plan.monthsSaved)} sooner")
                            }
                            Text(
                                parts.joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "vs. paying minimums only",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        if (plan.timeline.size > 1) {
                            Spacer(Modifier.height(16.dp))
                            PayoffChart(
                                timeline = plan.timeline,
                                lineColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(96.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Now · ${plan.timeline.first().formatCurrency()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    plan.debtFreeDateMillis?.let { formatPayoffDate(it) } ?: "Paid off",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        Text(
                            "These balances won't be paid off within 100 years at the current budget. Increase the extra monthly payment.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            SectionCard(title = "Extra Per Account") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Commit a fixed extra payment to specific accounts. Whatever is " +
                            "left of your monthly extra still funds the ${strategy.label} target.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    payable.forEach { account ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    account.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "${account.type.displayName} · ${account.currentBalance.formatCurrency()}" +
                                        if (account.apr > 0) " · %.2f%%".format(account.apr * 100) else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = extraText[account.id] ?: "",
                                onValueChange = { input ->
                                    extraText[account.id] = input.filter { it.isDigit() || it == '.' }
                                },
                                label = { Text("+$/mo") },
                                placeholder = { Text("0") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(120.dp),
                                shape = MaterialTheme.shapes.medium
                            )
                        }
                    }
                }
            }

            if (promoWarnings.isNotEmpty()) {
                SectionCard(title = "Promotional APR") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        promoWarnings.forEach { warning ->
                            Text(
                                buildString {
                                    append(warning.name)
                                    append(" won't be paid off before the promo ends in ")
                                    append(warning.monthsUntilExpiry)
                                    append(if (warning.monthsUntilExpiry == 1) " month." else " months.")
                                    if (warning.deferredInterest) {
                                        append(" Deferred interest may be charged if the balance is not cleared.")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            SectionCard(title = "Payoff Order") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    plan.accounts.forEach { a ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "${a.order}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    a.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "${a.startingBalance.formatCurrency()} · ${a.totalInterest.formatCurrency()} interest",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    a.payoffDateMillis?.let { formatPayoffDate(it) } ?: "—",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    if (a.payoffMonths != Int.MAX_VALUE)
                                        formatMonths(a.payoffMonths)
                                    else "Not on track",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Lightweight area + line chart of total debt declining to zero. */
@Composable
private fun PayoffChart(
    timeline: List<Double>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    if (timeline.size < 2) return
    val maxBalance = (timeline.maxOrNull() ?: 0.0).coerceAtLeast(1.0)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val n = timeline.size
        fun pointAt(i: Int): Offset {
            val x = if (n == 1) 0f else (i.toFloat() / (n - 1)) * w
            val y = h - (timeline[i] / maxBalance).toFloat() * h
            return Offset(x, y)
        }

        val linePath = Path().apply {
            val first = pointAt(0)
            moveTo(first.x, first.y)
            for (i in 1 until n) {
                val p = pointAt(i)
                lineTo(p.x, p.y)
            }
        }
        val areaPath = Path().apply {
            addPath(linePath)
            lineTo(pointAt(n - 1).x, h)
            lineTo(pointAt(0).x, h)
            close()
        }

        drawPath(path = areaPath, color = lineColor.copy(alpha = 0.18f))
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 3f)
        )
    }
}
