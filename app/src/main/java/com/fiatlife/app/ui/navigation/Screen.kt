package com.fiatlife.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.outlined.ShowChart
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
        title = "Home",
        subtitle = "This month at a glance",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
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

    data object Accounts : Screen(
        route = "accounts",
        title = "Accounts",
        subtitle = "Net worth, credit & loans",
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

    data object Spending : Screen(
        route = "spending",
        title = "Spending",
        subtitle = "Monthly targets and spending",
        selectedIcon = Icons.Filled.PieChart,
        unselectedIcon = Icons.Outlined.PieChart
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

    data object CompanyHistory : Screen(
        route = "company_history",
        title = "Companies",
        subtitle = "Bills, payments, and statements",
        selectedIcon = Icons.Filled.Business,
        unselectedIcon = Icons.Outlined.Business
    )

    data object CompanyHistoryDetail : Screen(
        route = "company_history/{companyKey}/{companyName}",
        title = "Company",
        subtitle = "Paid invoices and bills",
        selectedIcon = Icons.Filled.Business,
        unselectedIcon = Icons.Outlined.Business
    ) {
        fun routeWith(companyKey: String, companyName: String): String {
            val encodedKey = android.net.Uri.encode(companyKey)
            val encodedName = android.net.Uri.encode(companyName)
            return "company_history/$encodedKey/$encodedName"
        }
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

    data object DebtPlanner : Screen(
        route = "debt_planner",
        title = "Debt Planner",
        subtitle = "Payoff strategy and debt-free date",
        selectedIcon = Icons.Filled.AccountBalance,
        unselectedIcon = Icons.Outlined.AccountBalance
    )

    data object NetWorth : Screen(
        route = "net_worth",
        title = "Net Worth",
        subtitle = "Everything you own minus everything you owe",
        selectedIcon = Icons.AutoMirrored.Filled.ShowChart,
        unselectedIcon = Icons.AutoMirrored.Outlined.ShowChart
    )

    companion object {
        /** Bottom tab items (Settings stays in the top bar). */
        val bottomNavItems = listOf(Dashboard, Spending, Bills, Accounts)

        /** Screens opened from a tab, with a back button; their parent tab stays highlighted. */
        val backButtonScreens = setOf(Salary, Goals)

        /** The bottom tab a screen belongs to, for highlighting. */
        fun parentTab(screen: Screen?): Screen? = when (screen) {
            null -> null
            Dashboard, Salary, Goals -> Dashboard
            Spending -> Spending
            Bills, BillDetail, CompanyHistory, CompanyHistoryDetail -> Bills
            Accounts, DebtDetail, DebtPlanner, NetWorth -> Accounts
            else -> null
        }

        fun fromRoute(route: String?): Screen? = when {
            route == Dashboard.route -> Dashboard
            route == Salary.route -> Salary
            route == Bills.route -> Bills
            route == Accounts.route -> Accounts
            route == Goals.route -> Goals
            route == Spending.route -> Spending
            route == Settings.route -> Settings
            route?.startsWith("bill_detail") == true -> BillDetail
            route?.startsWith("company_history/") == true -> CompanyHistoryDetail
            route == CompanyHistory.route -> CompanyHistory
            route?.startsWith("debt_detail") == true -> DebtDetail
            route == DebtPlanner.route -> DebtPlanner
            route == NetWorth.route -> NetWorth
            else -> null
        }
    }
}
