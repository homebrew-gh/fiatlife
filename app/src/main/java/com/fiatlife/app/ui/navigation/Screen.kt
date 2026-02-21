package com.fiatlife.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Dashboard : Screen(
        route = "dashboard",
        title = "Dashboard",
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard
    )

    data object Salary : Screen(
        route = "salary",
        title = "Paycheck",
        selectedIcon = Icons.Filled.AttachMoney,
        unselectedIcon = Icons.Outlined.AttachMoney
    )

    data object Bills : Screen(
        route = "bills",
        title = "Bills",
        selectedIcon = Icons.Filled.Receipt,
        unselectedIcon = Icons.Outlined.Receipt
    )

    data object Goals : Screen(
        route = "goals",
        title = "Goals",
        selectedIcon = Icons.Filled.Flag,
        unselectedIcon = Icons.Outlined.Flag
    )

    data object Settings : Screen(
        route = "settings",
        title = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )

    data object BillDetail : Screen(
        route = "bill_detail/{billId}",
        title = "Bill",
        selectedIcon = Icons.Filled.Receipt,
        unselectedIcon = Icons.Outlined.Receipt
    ) {
        fun routeWithId(billId: String) = "bill_detail/$billId"
    }

    companion object {
        val bottomNavItems = listOf(Dashboard, Salary, Bills, Goals, Settings)
    }
}
