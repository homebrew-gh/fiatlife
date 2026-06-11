package com.fiatlife.app.domain.model

import kotlinx.serialization.Serializable
import java.util.Calendar

/**
 * Monthly budget model — the Android counterpart of the web `BudgetConfig`.
 *
 * A budget is a single per-user config record (d_tag `fiatlife/budget`), like
 * salary. It holds a monthly target per spending category plus, for categories
 * that aren't backed by recurring bills, a manually-entered "spent so far this
 * month" figure.
 *
 * Actuals are hybrid:
 *  - [BudgetCategoryKind.BILL] categories pull their actual from existing bill
 *    data (sum of monthly-equivalent amounts per [BillGeneralCategory]).
 *  - [BudgetCategoryKind.VARIABLE] categories use the user-entered [CategoryBudget.manualSpent].
 *
 * `manualSpent` resets at the start of each calendar month (see
 * [rollBudgetPeriod]); targets persist month to month.
 */

@Serializable
enum class BudgetCategoryKind { BILL, VARIABLE }

@Serializable
data class CategoryBudget(
    /** [BillGeneralCategory] name for BILL kind; a variable category key otherwise. */
    val key: String = "",
    val kind: BudgetCategoryKind = BudgetCategoryKind.VARIABLE,
    /** Monthly budget target in dollars. */
    val target: Double = 0.0,
    /** Manually-entered spend so far this month (variable categories only). */
    val manualSpent: Double = 0.0
)

@Serializable
data class BudgetConfig(
    val id: String = "",
    /** Calendar month the [CategoryBudget.manualSpent] figures apply to, as "YYYY-MM". */
    val periodMonth: String = "",
    val categoryBudgets: List<CategoryBudget> = emptyList(),
    val updatedAt: Long = 0L
)

/** A variable spending category — the "general purchases" that aren't bills. */
data class VariableBudgetCategory(val key: String, val label: String)

val VARIABLE_BUDGET_CATEGORIES: List<VariableBudgetCategory> = listOf(
    VariableBudgetCategory("GROCERIES", "Groceries"),
    VariableBudgetCategory("DINING", "Dining Out"),
    VariableBudgetCategory("TRANSPORTATION", "Transportation/Fuel"),
    VariableBudgetCategory("ENTERTAINMENT", "Entertainment"),
    VariableBudgetCategory("SHOPPING", "Shopping"),
    VariableBudgetCategory("PERSONAL_CARE", "Personal Care"),
    VariableBudgetCategory("MISC", "Miscellaneous")
)

private val VARIABLE_KEYS: Set<String> = VARIABLE_BUDGET_CATEGORIES.map { it.key }.toSet()
private val BILL_KEYS: Set<String> = BillGeneralCategory.entries.map { it.name }.toSet()

fun variableBudgetCategoryLabel(key: String): String =
    VARIABLE_BUDGET_CATEGORIES.firstOrNull { it.key == key }?.label ?: key

/** Current calendar month as "YYYY-MM" in local time. */
fun currentBudgetPeriodMonth(now: Long = System.currentTimeMillis()): String {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH) + 1
    return "%04d-%02d".format(year, month)
}

fun defaultBudgetConfig(now: Long = System.currentTimeMillis()): BudgetConfig =
    BudgetConfig(periodMonth = currentBudgetPeriodMonth(now))

/**
 * Roll the budget into the current month if stale: targets carry over, manual
 * spend resets to 0. Returns the same object when no change is needed.
 */
fun rollBudgetPeriod(config: BudgetConfig, now: Long = System.currentTimeMillis()): BudgetConfig {
    val period = currentBudgetPeriodMonth(now)
    if (config.periodMonth == period) return config
    return config.copy(
        periodMonth = period,
        categoryBudgets = config.categoryBudgets.map { it.copy(manualSpent = 0.0) }
    )
}

fun getCategoryBudget(config: BudgetConfig, key: String): CategoryBudget? =
    config.categoryBudgets.firstOrNull { it.key == key }

private fun sanitizeAmount(value: Double): Double = if (value.isFinite() && value > 0) value else 0.0

/** Immutably upsert a single category's target/spent. */
fun setCategoryBudget(
    config: BudgetConfig,
    key: String,
    kind: BudgetCategoryKind,
    target: Double? = null,
    manualSpent: Double? = null
): BudgetConfig {
    val existing = getCategoryBudget(config, key)
    val next = CategoryBudget(
        key = key,
        kind = kind,
        target = sanitizeAmount(target ?: existing?.target ?: 0.0),
        manualSpent = if (kind == BudgetCategoryKind.VARIABLE) {
            sanitizeAmount(manualSpent ?: existing?.manualSpent ?: 0.0)
        } else 0.0
    )
    val others = config.categoryBudgets.filterNot { it.key == key }
    return config.copy(categoryBudgets = others + next)
}

/** Last-write-wins merge that preserves the stable id when the incoming copy lacks one. */
fun mergeBudgetConfigPreserveId(incoming: BudgetConfig, existing: BudgetConfig?): BudgetConfig {
    if (existing == null) return incoming
    return if (incoming.id.isEmpty() && existing.id.isNotEmpty()) {
        incoming.copy(id = existing.id)
    } else incoming
}

data class BudgetRow(
    val key: String,
    val label: String,
    val kind: BudgetCategoryKind,
    val target: Double,
    /** BILL: derived from bills. VARIABLE: manualSpent. */
    val actual: Double
) {
    val remaining: Double get() = target - actual
    /** 0–100+, where >100 means over budget. */
    val percentUsed: Double
        get() = when {
            target > 0 -> actual / target * 100.0
            actual > 0 -> 100.0
            else -> 0.0
        }
}

data class BudgetSummary(
    val billRows: List<BudgetRow> = emptyList(),
    val variableRows: List<BudgetRow> = emptyList(),
    val totalTarget: Double = 0.0,
    val totalActual: Double = 0.0,
    val totalBillActual: Double = 0.0,
    val totalVariableActual: Double = 0.0,
    val takeHome: Double = 0.0
) {
    /** take-home minus everything budgeted (targets). */
    val unbudgeted: Double get() = takeHome - totalTarget
    /** take-home minus everything actually spent/committed. */
    val remaining: Double get() = takeHome - totalActual
}

/**
 * Build the displayed budget. Bill rows are shown for every general category
 * that has a target set or has bills; variable rows are always shown.
 */
fun computeBudgetSummary(
    config: BudgetConfig,
    billCategoryTotals: Map<BillGeneralCategory, Double>,
    takeHome: Double
): BudgetSummary {
    val billRows = BillGeneralCategory.entries.mapNotNull { cat ->
        val billActual = billCategoryTotals[cat] ?: 0.0
        val target = getCategoryBudget(config, cat.name)?.target ?: 0.0
        if (billActual <= 0.0 && target <= 0.0) return@mapNotNull null
        BudgetRow(
            key = cat.name,
            label = cat.displayName,
            kind = BudgetCategoryKind.BILL,
            target = target,
            actual = billActual
        )
    }

    val variableRows = VARIABLE_BUDGET_CATEGORIES.map { (key, label) ->
        val entry = getCategoryBudget(config, key)
        BudgetRow(
            key = key,
            label = label,
            kind = BudgetCategoryKind.VARIABLE,
            target = entry?.target ?: 0.0,
            actual = entry?.manualSpent ?: 0.0
        )
    }

    val totalBillActual = billRows.sumOf { it.actual }
    val totalVariableActual = variableRows.sumOf { it.actual }
    val totalTarget = billRows.sumOf { it.target } + variableRows.sumOf { it.target }

    return BudgetSummary(
        billRows = billRows,
        variableRows = variableRows,
        totalTarget = totalTarget,
        totalActual = totalBillActual + totalVariableActual,
        totalBillActual = totalBillActual,
        totalVariableActual = totalVariableActual,
        takeHome = takeHome
    )
}
