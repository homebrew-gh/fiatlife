package com.fiatlife.app.ui.screens.budget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fiatlife.app.domain.model.BudgetCategoryKind
import com.fiatlife.app.domain.model.BudgetMetric
import com.fiatlife.app.domain.model.BudgetRow
import com.fiatlife.app.domain.model.VARIABLE_BUDGET_CATEGORIES
import com.fiatlife.app.domain.model.budgetBreakdown
import com.fiatlife.app.domain.model.savingsAmount
import com.fiatlife.app.domain.model.savingsRate
import com.fiatlife.app.ui.components.CurrencyTextField
import com.fiatlife.app.ui.components.EmptyState
import com.fiatlife.app.ui.components.ProgressBar
import com.fiatlife.app.ui.components.formatCurrency
import com.fiatlife.app.ui.theme.LossRed
import com.fiatlife.app.ui.theme.ProfitGreen
import com.fiatlife.app.ui.theme.WarningAmber
import com.fiatlife.app.ui.viewmodel.BudgetViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

/** Categorical palette shared by the chart and the per-category dots (mirrors the web app). */
private val CHART_PALETTE = listOf(
    Color(0xFF2E9E5B), Color(0xFF3B82F6), Color(0xFFF59E0B), Color(0xFF8B5CF6),
    Color(0xFFEC4899), Color(0xFF14B8A6), Color(0xFFEF4444), Color(0xFFEAB308),
    Color(0xFF6366F1), Color(0xFF06B6D4), Color(0xFFF97316), Color(0xFF84CC16)
)

private val CATEGORY_COLOR_ORDER: List<String> =
    VARIABLE_BUDGET_CATEGORIES.map { it.key } +
        listOf("HOME", "UTILITIES", "AUTO", "CREDIT_LOANS", "SUBSCRIPTION", "HEALTH", "PERSONAL", "OTHER")

private val CATEGORY_COLORS: Map<String, Color> =
    CATEGORY_COLOR_ORDER.mapIndexed { i, key -> key to CHART_PALETTE[i % CHART_PALETTE.size] }.toMap()

private fun colorForKey(key: String): Color = CATEGORY_COLORS[key] ?: CHART_PALETTE[0]

@Composable
fun BudgetScreen(
    viewModel: BudgetViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val summary = state.summary
    var chartMetric by remember { mutableStateOf(BudgetMetric.ACTUAL) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!state.hasData) {
            item {
                EmptyState(
                    icon = Icons.Filled.PieChart,
                    title = "No budget yet",
                    subtitle = "Add your paycheck and bills, then set monthly targets below to start budgeting."
                )
            }
        }

        item {
            BudgetOverviewCard(
                takeHome = summary.takeHome,
                totalTarget = summary.totalTarget,
                unbudgeted = summary.unbudgeted,
                totalActual = summary.totalActual,
                remaining = summary.remaining,
                savings = savingsAmount(summary),
                savingsRate = savingsRate(summary)
            )
        }

        if (state.hasData) {
            item {
                BreakdownCard(
                    summary = summary,
                    metric = chartMetric,
                    onMetricChange = { chartMetric = it }
                )
            }
        }

        item {
            SectionHeader(
                title = "Spending",
                subtitle = "Variable purchases that aren't bills. Enter what you've spent this month."
            )
        }
        items(summary.variableRows, key = { "var-${it.key}" }) { row ->
            VariableBudgetCard(
                row = row,
                onTargetChange = { viewModel.updateTarget(row.key, BudgetCategoryKind.VARIABLE, it) },
                onSpentChange = { viewModel.updateSpent(row.key, it) }
            )
        }

        item {
            SectionHeader(
                title = "Bills",
                subtitle = "Pulled automatically from your recurring bills. Set a target to budget against them."
            )
        }
        if (summary.billRows.isEmpty()) {
            item {
                Text(
                    text = "No bills yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(summary.billRows, key = { "bill-${it.key}" }) { row ->
                BillBudgetCard(
                    row = row,
                    onTargetChange = { viewModel.updateTarget(row.key, BudgetCategoryKind.BILL, it) }
                )
            }
        }
    }
}

@Composable
private fun BudgetOverviewCard(
    takeHome: Double,
    totalTarget: Double,
    unbudgeted: Double,
    totalActual: Double,
    remaining: Double,
    savings: Double,
    savingsRate: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OverviewStat("Take-home", takeHome.formatCurrency(), MaterialTheme.colorScheme.onPrimaryContainer)
                OverviewStat("Budgeted", totalTarget.formatCurrency(), MaterialTheme.colorScheme.onPrimaryContainer)
                OverviewStat(
                    "Unbudgeted",
                    unbudgeted.formatCurrency(),
                    if (unbudgeted >= 0) ProfitGreen else LossRed
                )
            }

            if (takeHome > 0) {
                Spacer(modifier = Modifier.height(14.dp))
                AllocationBar(takeHome = takeHome, budgeted = totalTarget)
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Spent / committed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = totalActual.formatCurrency(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = "${remaining.formatCurrency()} left",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (remaining >= 0) ProfitGreen else LossRed
                )
            }

            if (takeHome > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Saving / investing this month",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "${savings.formatCurrency()}  (${(savingsRate * 100).roundToInt()}% of pay)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

/** Stacked bar visualizing how take-home is allocated across targets. */
@Composable
private fun AllocationBar(takeHome: Double, budgeted: Double) {
    val over = budgeted > takeHome
    val budgetedFraction = (budgeted / takeHome).coerceIn(0.0, 1.0).toFloat()
    val overFraction = if (over) ((budgeted - takeHome) / takeHome).coerceIn(0.0, 1.0).toFloat() else 0f
    val trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(trackColor)
        ) {
            Box(
                modifier = Modifier
                    .weight(budgetedFraction.coerceAtLeast(0.0001f))
                    .fillMaxHeight()
                    .background(if (over) WarningAmber else ProfitGreen)
            )
            if (over && overFraction > 0f) {
                Box(
                    modifier = Modifier
                        .weight(overFraction)
                        .fillMaxHeight()
                        .background(LossRed)
                )
            }
            val remainder = (1f - budgetedFraction).coerceAtLeast(0f)
            if (!over && remainder > 0f) {
                Spacer(modifier = Modifier.weight(remainder))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${(budgeted / takeHome * 100).roundToInt()}% of take-home budgeted",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Text(
                text = if (over) {
                    "${(budgeted - takeHome).formatCurrency()} over income"
                } else {
                    "${(takeHome - budgeted).formatCurrency()} left to allocate"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun OverviewStat(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BreakdownCard(
    summary: com.fiatlife.app.domain.model.BudgetSummary,
    metric: BudgetMetric,
    onMetricChange: (BudgetMetric) -> Unit
) {
    val slices = budgetBreakdown(summary, metric)
    val total = slices.sumOf { it.value }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Where your money goes",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                MetricToggle(metric = metric, onMetricChange = onMetricChange)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (metric == BudgetMetric.ACTUAL) {
                    "Breakdown of spending and committed bills this month."
                } else {
                    "Breakdown of your monthly budget targets."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (slices.isEmpty()) {
                Text(
                    text = if (metric == BudgetMetric.ACTUAL) {
                        "No spending recorded yet this month."
                    } else {
                        "No budget targets set yet."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BudgetDonut(
                        slices = slices.map { colorForKey(it.key) to it.value.toFloat() },
                        centerLabel = total.formatCurrency(),
                        centerSub = if (metric == BudgetMetric.ACTUAL) "spent" else "budgeted"
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        slices.forEach { slice ->
                            val pct = if (total > 0) (slice.value / total * 100).roundToInt() else 0
                            LegendRow(
                                color = colorForKey(slice.key),
                                label = slice.label,
                                amount = slice.value.formatCurrency(),
                                pct = pct
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricToggle(metric: BudgetMetric, onMetricChange: (BudgetMetric) -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(2.dp)
    ) {
        ToggleSegment("Spending", metric == BudgetMetric.ACTUAL) { onMetricChange(BudgetMetric.ACTUAL) }
        ToggleSegment("Budget", metric == BudgetMetric.TARGET) { onMetricChange(BudgetMetric.TARGET) }
    }
}

@Composable
private fun ToggleSegment(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun BudgetDonut(
    slices: List<Pair<Color, Float>>,
    centerLabel: String,
    centerSub: String
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 22.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )
            val sum = slices.sumOf { it.second.toDouble() }.toFloat()
            if (sum > 0f) {
                var start = -90f
                slices.forEach { (color, value) ->
                    val sweep = value / sum * 360f
                    drawArc(
                        color = color,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth)
                    )
                    start += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = centerSub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, amount: String, pct: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = amount,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "$pct%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun CategoryDot(color: Color) {
    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
}

@Composable
private fun VariableBudgetCard(
    row: BudgetRow,
    onTargetChange: (Double) -> Unit,
    onSpentChange: (Double) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryDot(colorForKey(row.key))
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BudgetAmountField(
                    value = row.actual,
                    onCommit = onSpentChange,
                    label = "Spent",
                    modifier = Modifier.weight(1f)
                )
                BudgetAmountField(
                    value = row.target,
                    onCommit = onTargetChange,
                    label = "Target",
                    modifier = Modifier.weight(1f)
                )
            }
            BudgetProgress(row)
        }
    }
}

@Composable
private fun BillBudgetCard(
    row: BudgetRow,
    onTargetChange: (Double) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryDot(colorForKey(row.key))
                        Text(
                            text = row.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = if (row.actual > 0) {
                            "${row.actual.formatCurrency()}/mo in bills"
                        } else {
                            "No bills yet"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BudgetAmountField(
                    value = row.target,
                    onCommit = onTargetChange,
                    label = "Target",
                    modifier = Modifier.width(140.dp)
                )
            }
            if (row.target > 0) {
                BudgetProgress(row)
            }
        }
    }
}

@Composable
private fun BudgetProgress(row: BudgetRow) {
    if (row.target <= 0 && row.actual <= 0) return
    val over = row.target > 0 && row.actual > row.target
    val color = when {
        over -> LossRed
        row.percentUsed >= 85 -> WarningAmber
        else -> ProfitGreen
    }
    Spacer(modifier = Modifier.height(10.dp))
    ProgressBar(
        progress = if (row.target > 0) (row.actual / row.target).toFloat() else if (over) 1f else 0f,
        color = color,
        height = 8
    )
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = if (row.target > 0) "${row.percentUsed.toInt()}% of target" else "No target set",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (row.target > 0) {
            Text(
                text = if (row.remaining >= 0) {
                    "${row.remaining.formatCurrency()} left"
                } else {
                    "${(-row.remaining).formatCurrency()} over"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (row.remaining >= 0) ProfitGreen else LossRed
            )
        }
    }
}

private fun trimAmount(value: Double): String {
    if (value <= 0) return ""
    return if (value == value.toLong().toDouble()) value.toLong().toString()
    else "%.2f".format(value)
}

/** Currency input that reflects external (synced) updates without disrupting typing. */
@Composable
private fun BudgetAmountField(
    value: Double,
    onCommit: (Double) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf(trimAmount(value)) }
    LaunchedEffect(value) {
        val parsed = text.toDoubleOrNull() ?: 0.0
        if (abs(parsed - value) > 0.001) {
            text = trimAmount(value)
        }
    }
    CurrencyTextField(
        value = text,
        onValueChange = {
            text = it
            onCommit(it.toDoubleOrNull() ?: 0.0)
        },
        label = label,
        modifier = modifier
    )
}
