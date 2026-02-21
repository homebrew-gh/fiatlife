package com.fiatlife.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val subtitle: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Dashboard : Screen(
        route = "dashboard",
        title = "FiatLife",
        subtitle = "Your financial dashboard",
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard
    )

    data object Salary : Screen(
        route = "salary",
        title = "Paycheck",
        subtitle = "Salary and tax calculator",
        selectedIcon = Icons.Filled.AttachMoney,
        unselectedIcon = Icons.Outlined.AttachMoney
    )

    data object Bills : Screen(
        route = "bills",
        title = "Bills",
        subtitle = "Track bills and subscriptions",
        selectedIcon = Icons.Filled.Receipt,
        unselectedIcon = Icons.Outlined.Receipt
    )

    data object Debt : Screen(
        route = "debt",
        title = "Debt",
        subtitle = "Credit & loans",
        selectedIcon = Icons.Filled.AccountBalance,
        unselectedIcon = Icons.Outlined.AccountBalance
    )

    data object Goals : Screen(
        route = "goals",
        title = "Goals",
        subtitle = "Financial goals and savings",
        selectedIcon = Icons.Filled.Flag,
        unselectedIcon = Icons.Outlined.Flag
    )

    data object Settings : Screen(
        route = "settings",
        title = "Settings",
        subtitle = "App preferences and account",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )

    data object BillDetail : Screen(
        route = "bill_detail/{billId}",
        title = "Bill",
        subtitle = "Bill details",
        selectedIcon = Icons.Filled.Receipt,
        unselectedIcon = Icons.Outlined.Receipt
    ) {
        fun routeWithId(billId: String) = "bill_detail/$billId"
    }

    data object DebtDetail : Screen(
        route = "debt_detail/{accountId}",
        title = "Account",
        subtitle = "Credit or loan details",
        selectedIcon = Icons.Filled.AccountBalance,
        unselectedIcon = Icons.Outlined.AccountBalance
    ) {
        fun routeWithId(accountId: String) = "debt_detail/$accountId"
    }

    companion object {
        /** Bottom tab items (Settings is in top bar) */
        val bottomNavItems = listOf(Dashboard, Salary, Bills, Debt, Goals)

        fun fromRoute(route: String?): Screen? = when {
            route == Dashboard.route -> Dashboard
            route == Salary.route -> Salary
            route == Bills.route -> Bills
            route == Debt.route -> Debt
            route == Goals.route -> Goals
            route == Settings.route -> Settings
            route?.startsWith("bill_detail") == true -> BillDetail
            route?.startsWith("debt_detail") == true -> DebtDetail
            else -> null
        }
    }
}
