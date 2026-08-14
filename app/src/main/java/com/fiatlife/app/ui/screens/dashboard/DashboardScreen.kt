package com.fiatlife.app.ui.screens.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.fiatlife.app.domain.model.MonthlyTakeHomeSource
import com.fiatlife.app.domain.model.formatPayoffDate
import com.fiatlife.app.ui.components.*
import com.fiatlife.app.ui.navigation.Screen
import com.fiatlife.app.ui.theme.*
import com.fiatlife.app.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    fun openTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!state.hasData) {
            item {
                EmptyState(
                    icon = Icons.Filled.Home,
                    title = "Get started",
                    subtitle = "Set up your paycheck and bills to see leftover cash and what's due."
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { openTab(Screen.Salary.route) }) {
                            Text("Paycheck")
                        }
                        OutlinedButton(onClick = { openTab(Screen.Bills.route) }) {
                            Text("Bills")
                        }
                    }
                }
            }
        } else {
        if (
            state.overdueBillCount > 0 ||
            state.billsComingDueCount > 0 ||
            state.missingPaycheckCount > 0
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.overdueBillCount > 0) {
                        AssistChip(
                            onClick = { openTab(Screen.Bills.route) },
                            label = {
                                Text(
                                    "${state.overdueBillCount} overdue",
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                labelColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        )
                    }
                    if (state.billsComingDueCount > 0) {
                        AssistChip(
                            onClick = { openTab(Screen.Bills.route) },
                            label = {
                                Text("${state.billsComingDueCount} due in 7 days")
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                    if (state.missingPaycheckCount > 0) {
                        AssistChip(
                            onClick = { openTab(Screen.Salary.route) },
                            label = {
                                Text(
                                    if (state.missingPaycheckCount == 1) "Log paycheck"
                                    else "${state.missingPaycheckCount} missing paychecks"
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.AttachMoney,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }
        }

        item {
            MonthHeroCard(
                leftover = state.monthlyDisposable,
                takeHome = state.takeHomePay,
                monthlyBills = state.monthlyBills,
                source = state.monthlyTakeHomeSource,
                loggedTakeHome = state.monthlyLoggedTakeHome,
                projectedRemainder = state.monthlyProjectedRemainder,
                loggedPaycheckCount = state.monthlyLoggedPaycheckCount,
                remainingPaycheckCount = state.monthlyRemainingPaycheckCount,
                loggedOvertimeHours = state.monthlyLoggedOvertimeHours,
                loggedBonus = state.monthlyLoggedBonus,
                perPaycheckEstimate = state.monthlyPerPaycheckEstimate,
                hasSalary = state.hasSalary,
                onClick = { openTab(Screen.Salary.route) }
            )
        }

        val showBudget = state.hasBudgetTargets || state.takeHomePay > 0
        val showDebt = state.debtAccountCount > 0
        val showHousing = state.housingMonthly > 0.0
        if (showBudget || showDebt || showHousing) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (showBudget) {
                        SnapshotTile(
                            label = if (state.hasBudgetTargets) "Unbudgeted" else "Budget",
                            value = if (state.hasBudgetTargets) {
                                state.budgetUnbudgeted.formatCurrency()
                            } else {
                                "Set targets"
                            },
                            icon = Icons.Filled.PieChart,
                            onClick = { openTab(Screen.Budget.route) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (showDebt) {
                        val freeDate = state.debtFreeDateMs
                        val debtDetail = if (state.debtPayoffFeasible && freeDate != null) {
                            "Free ${formatPayoffDate(freeDate)}"
                        } else {
                            null
                        }
                        SnapshotTile(
                            label = "Debt",
                            value = state.totalDebt.formatCurrency(),
                            detail = debtDetail,
                            icon = Icons.Filled.AccountBalance,
                            onClick = { openTab(Screen.Debt.route) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (showHousing) {
                        SnapshotTile(
                            label = "Housing (PITI)",
                            value = state.housingMonthly.formatCurrency(),
                            icon = Icons.Filled.Home,
                            onClick = {
                                val id = state.mortgageAccountId
                                if (id != null) {
                                    navController.navigate(Screen.DebtDetail.routeWithId(id))
                                } else {
                                    openTab(Screen.Debt.route)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (state.upcomingBills.isNotEmpty()) {
            item {
                SectionCard(
                    title = "Due soon",
                    icon = Icons.Filled.Receipt,
                    action = {
                        TextButton(onClick = { openTab(Screen.Bills.route) }) {
                            Text("View all")
                        }
                    }
                ) {
                    state.upcomingBills.forEach { bill ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    navController.navigate(Screen.BillDetail.routeWithId(bill.id))
                                }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bill.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = bill.subcategoryName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (bill.dueDateText.isNotEmpty()) {
                                    Text(
                                        text = bill.dueDateText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (bill.isPastDue)
                                            MaterialTheme.colorScheme.error
                                        else
                                            MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            MoneyText(
                                amount = bill.amountDue,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        if (bill != state.upcomingBills.last()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Goal",
                icon = Icons.Filled.Flag,
                modifier = Modifier.clickable { openTab(Screen.Goals.route) }
            ) {
                val goal = state.primaryGoal
                if (state.goalCount == 0) {
                    Text(
                        text = "No goals yet. Add your first goal.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (goal == null) {
                    Text(
                        text = "All goals complete.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = goal.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${goal.currentAmount.formatCurrency()} / ${goal.targetAmount.formatCurrency()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ProgressBar(
                            progress = (goal.progressPercent / 100).toFloat(),
                            color = ProfitGreen
                        )
                        Text(
                            text = "%.1f%% complete".format(goal.progressPercent),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun MonthHeroCard(
    leftover: Double,
    takeHome: Double,
    monthlyBills: Double,
    source: MonthlyTakeHomeSource,
    loggedTakeHome: Double,
    projectedRemainder: Double,
    loggedPaycheckCount: Int,
    remainingPaycheckCount: Int,
    loggedOvertimeHours: Double,
    loggedBonus: Double,
    perPaycheckEstimate: Double,
    hasSalary: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "This month",
                style = MaterialTheme.typography.titleMedium,
                color = onContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "After bills",
                        style = MaterialTheme.typography.labelMedium,
                        color = onContainer.copy(alpha = 0.75f)
                    )
                    MoneyText(
                        amount = leftover,
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (leftover >= 0) ProfitGreen else LossRed
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Take-home",
                        style = MaterialTheme.typography.labelMedium,
                        color = onContainer.copy(alpha = 0.75f)
                    )
                    MoneyText(
                        amount = takeHome,
                        style = MaterialTheme.typography.titleLarge,
                        color = onContainer
                    )
                }
            }
            if (monthlyBills > 0.0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Bills ${monthlyBills.formatCurrency()} this month",
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.75f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when {
                    !hasSalary -> "Add paycheck info for take-home estimate"
                    source == MonthlyTakeHomeSource.LOGGED ->
                        "From $loggedPaycheckCount logged paycheck" +
                            (if (loggedPaycheckCount == 1) "" else "s") + " this month"
                    source == MonthlyTakeHomeSource.MIXED ->
                        "Logged paychecks + projected remainder at base pay"
                    else -> "Estimated from current pay rate"
                },
                style = MaterialTheme.typography.bodySmall,
                color = onContainer.copy(alpha = 0.7f)
            )
            if (hasSalary && (loggedTakeHome > 0.0 || projectedRemainder > 0.0)) {
                Spacer(modifier = Modifier.height(12.dp))
                if (loggedTakeHome > 0.0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Paid so far",
                            style = MaterialTheme.typography.bodySmall,
                            color = onContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            loggedTakeHome.formatCurrency(),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = onContainer
                        )
                    }
                }
                if (projectedRemainder > 0.0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = buildString {
                                append("Projected remainder")
                                if (remainingPaycheckCount > 0) {
                                    append(" ($remainingPaycheckCount × ${perPaycheckEstimate.formatCurrency()})")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = onContainer.copy(alpha = 0.8f),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            projectedRemainder.formatCurrency(),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = onContainer
                        )
                    }
                }
                if (loggedOvertimeHours > 0.0 || loggedBonus > 0.0) {
                    Text(
                        text = buildString {
                            append("Includes")
                            if (loggedOvertimeHours > 0.0) {
                                append(" %.1f OT hrs".format(loggedOvertimeHours))
                            }
                            if (loggedOvertimeHours > 0.0 && loggedBonus > 0.0) {
                                append(" and")
                            }
                            if (loggedBonus > 0.0) {
                                append(" ${loggedBonus.formatCurrency()} in bonuses")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = onContainer.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SnapshotTile(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
