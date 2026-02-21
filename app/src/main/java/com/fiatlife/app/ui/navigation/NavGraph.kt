package com.fiatlife.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.fiatlife.app.ui.components.AppBanner
import com.fiatlife.app.ui.navigation.Screen
import com.fiatlife.app.ui.screens.bills.BillDetailScreen
import com.fiatlife.app.ui.screens.bills.BillsScreen
import com.fiatlife.app.ui.screens.dashboard.DashboardScreen
import com.fiatlife.app.ui.screens.debt.DebtDetailScreen
import com.fiatlife.app.ui.screens.debt.DebtScreen
import com.fiatlife.app.ui.screens.goals.GoalsScreen
import com.fiatlife.app.ui.screens.salary.SalaryScreen
import com.fiatlife.app.ui.screens.settings.SettingsScreen
import com.fiatlife.app.ui.viewmodel.MainAppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiatLifeNavGraph(onLogout: () -> Unit = {}) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val mainViewModel: MainAppViewModel = hiltViewModel()
    val mainState by mainViewModel.state.collectAsStateWithLifecycle()
    val currentScreen = Screen.fromRoute(currentDestination?.route)

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                AppBanner(
                    title = currentScreen?.title ?: "FiatLife",
                    subtitle = currentScreen?.subtitle?.takeIf { it.isNotBlank() } ?: "Your financial dashboard",
                    isConnected = mainState.isConnected,
                    hasData = mainState.hasData,
                    actions = {
                        IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Screen.bottomNavItems.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == screen.route
                    } == true

                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.title
                            )
                        },
                        label = { Text(screen.title) },
                        selected = selected,
                        onClick = {
                            // If we're on Settings (or a detail screen), pop first so we're not stuck
                            val isOnNonTabScreen = currentDestination?.route != null &&
                                currentDestination?.route != screen.route &&
                                !Screen.bottomNavItems.any { it.route == currentDestination?.route }
                            if (isOnNonTabScreen) {
                                navController.popBackStack()
                            }
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) }
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(navController = navController)
            }
            composable(Screen.Salary.route) {
                SalaryScreen()
            }
            composable(Screen.Bills.route) {
                BillsScreen(navController = navController)
            }
            composable(Screen.Debt.route) {
                DebtScreen(navController = navController)
            }
            composable(Screen.Goals.route) {
                GoalsScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(onLogout = onLogout)
            }
            composable(
                route = Screen.BillDetail.route,
                arguments = listOf(navArgument("billId") { type = NavType.StringType })
            ) {
                BillDetailScreen(navController = navController)
            }
            composable(
                route = Screen.DebtDetail.route,
                arguments = listOf(navArgument("accountId") { type = NavType.StringType })
            ) {
                DebtDetailScreen(navController = navController)
            }
        }
    }
}
