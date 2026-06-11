package com.fiatlife.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.fiatlife.app.ui.components.SyncStatusBar
import com.fiatlife.app.ui.navigation.Screen
import com.fiatlife.app.ui.screens.bills.BillDetailScreen
import com.fiatlife.app.ui.screens.bills.BillsScreen
import com.fiatlife.app.ui.screens.bills.CompanyHistoryDetailScreen
import com.fiatlife.app.ui.screens.bills.CompanyHistoryScreen
import com.fiatlife.app.ui.screens.budget.BudgetScreen
import com.fiatlife.app.ui.screens.dashboard.DashboardScreen
import com.fiatlife.app.ui.screens.debt.DebtDetailScreen
import com.fiatlife.app.ui.screens.debt.DebtPlannerScreen
import com.fiatlife.app.ui.screens.debt.DebtScreen
import com.fiatlife.app.ui.screens.goals.GoalsScreen
import com.fiatlife.app.ui.screens.salary.SalaryScreen
import com.fiatlife.app.ui.screens.settings.SettingsScreen
import com.fiatlife.app.ui.viewmodel.MainAppViewModel
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiatLifeNavGraph(onLogout: () -> Unit = {}) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentScreen = Screen.fromRoute(currentDestination?.route)
    val hideGlobalTopBar = currentScreen == Screen.BillDetail ||
        currentScreen == Screen.DebtDetail ||
        currentScreen == Screen.DebtPlanner ||
        currentScreen == Screen.CompanyHistory ||
        currentScreen == Screen.CompanyHistoryDetail

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { MainAppSnackbarHost() },
        topBar = {
            if (!hideGlobalTopBar) {
                MainAppTopBar(
                    title = currentScreen?.title ?: "FiatLife",
                    subtitle = currentScreen?.subtitle?.takeIf { it.isNotBlank() } ?: "Your financial dashboard",
                    onGoalsClick = { navController.navigate(Screen.Goals.route) },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) }
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
        Column(modifier = Modifier.padding(innerPadding)) {
            MainAppSyncBar()
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route
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
                composable(Screen.CompanyHistory.route) {
                    CompanyHistoryScreen(navController = navController)
                }
                composable(Screen.Debt.route) {
                    DebtScreen(navController = navController)
                }
                composable(Screen.DebtPlanner.route) {
                    DebtPlannerScreen(navController = navController)
                }
                composable(Screen.Goals.route) {
                    GoalsScreen()
                }
                composable(Screen.Budget.route) {
                    BudgetScreen()
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
                composable(
                    route = Screen.CompanyHistoryDetail.route,
                    arguments = listOf(
                        navArgument("companyKey") { type = NavType.StringType },
                        navArgument("companyName") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val companyKey = android.net.Uri.decode(
                        backStackEntry.arguments?.getString("companyKey").orEmpty()
                    )
                    val companyName = android.net.Uri.decode(
                        backStackEntry.arguments?.getString("companyName").orEmpty()
                    )
                    CompanyHistoryDetailScreen(
                        navController = navController,
                        companyKey = companyKey,
                        companyName = companyName
                    )
                }
            }
        }
    }
}

@Composable
private fun MainAppTopBar(
    title: String,
    subtitle: String,
    onGoalsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val mainViewModel: MainAppViewModel = hiltViewModel()
    val mainState by mainViewModel.state.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        AppBanner(
            title = title,
            subtitle = subtitle,
            isConnected = mainState.isConnected,
            hasData = mainState.hasData,
            isSyncing = mainState.isManualSyncing,
            onSyncClick = { mainViewModel.manualSyncFromRelay() },
            actions = {
                IconButton(onClick = onGoalsClick) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = "Goals")
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        )
    }
}

@Composable
private fun MainAppSyncBar() {
    val mainViewModel: MainAppViewModel = hiltViewModel()
    val mainState by mainViewModel.state.collectAsStateWithLifecycle()

    SyncStatusBar(
        pending = mainState.pendingSync,
        failed = mainState.failedSync,
        onRetry = { mainViewModel.retryFailedSync() },
    )
}

@Composable
private fun MainAppSnackbarHost() {
    val mainViewModel: MainAppViewModel = hiltViewModel()
    val mainState by mainViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var manualSyncStarted by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var lastFailedSync by remember { mutableIntStateOf(0) }

    LaunchedEffect(mainState.isManualSyncing) {
        if (mainState.isManualSyncing) {
            manualSyncStarted = true
            Toast.makeText(context, "Syncing with relay…", Toast.LENGTH_SHORT).show()
        } else if (manualSyncStarted) {
            manualSyncStarted = false
            Toast.makeText(context, "Sync complete", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(mainState.failedSync) {
        val failed = mainState.failedSync
        if (failed > lastFailedSync) {
            val result = snackbarHostState.showSnackbar(
                message = "$failed change${if (failed == 1) "" else "s"} not synced",
                actionLabel = "Retry",
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                mainViewModel.retryFailedSync()
            }
        }
        lastFailedSync = failed
    }

    SnackbarHost(snackbarHostState)
}
