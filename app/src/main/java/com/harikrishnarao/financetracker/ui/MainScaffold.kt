package com.harikrishnarao.financetracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harikrishnarao.financetracker.update.UpdateBanner
import com.harikrishnarao.financetracker.update.UpdateViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.harikrishnarao.financetracker.ui.analysis.AnalysisScreen
import com.harikrishnarao.financetracker.ui.bills.BillsScreen
import com.harikrishnarao.financetracker.ui.home.HomeScreen
import com.harikrishnarao.financetracker.ui.settings.SettingsScreen
import com.harikrishnarao.financetracker.ui.settings.SmsActivityScreen
import com.harikrishnarao.financetracker.ui.spends.AddTransactionDialog
import com.harikrishnarao.financetracker.ui.spends.SpendsScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Filled.Home),
    SPENDS("spends", "Spends", Icons.Filled.AccountBalanceWallet),
    ANALYSIS("analysis", "Analysis", Icons.Filled.BarChart),
    BILLS("bills", "Bills", Icons.Filled.Notifications),
}

object Routes {
    const val SETTINGS = "settings"
    const val SMS = "sms"
    const val BUDGET = "analysis"
    const val BILLS = "bills"
    fun spends(search: Boolean = false) = "spends?search=$search"
}

@Composable
fun MainScaffold() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination
    var adding by remember { mutableStateOf(false) }
    val quickAdd: QuickAddViewModel = viewModel(factory = AppViewModelFactory)
    val updates: UpdateViewModel = viewModel(factory = AppViewModelFactory)
    // Every time the app is opened or comes back to the front, look for a newer version.
    LifecycleResumeEffect(Unit) {
        updates.checkIfDue()
        onPauseOrDispose { }
    }

    fun go(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    fun selected(tab: Tab) = current?.hierarchy?.any { it.route?.substringBefore('?') == tab.route } == true
    fun open(tab: Tab) = go(if (tab == Tab.SPENDS) Routes.spends() else tab.route)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                TabItem(Tab.HOME, selected(Tab.HOME)) { open(Tab.HOME) }
                TabItem(Tab.SPENDS, selected(Tab.SPENDS)) { open(Tab.SPENDS) }
                // Fixed height: fillMaxHeight here made the bar grow over the whole screen.
                Box(Modifier.weight(1f).height(80.dp), contentAlignment = Alignment.Center) {
                    FloatingActionButton(
                        onClick = { adding = true },
                        shape = CircleShape,
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                        modifier = Modifier.size(58.dp),
                    ) { Icon(Icons.Filled.Add, contentDescription = "Add a transaction", modifier = Modifier.size(30.dp)) }
                }
                TabItem(Tab.ANALYSIS, selected(Tab.ANALYSIS)) { open(Tab.ANALYSIS) }
                TabItem(Tab.BILLS, selected(Tab.BILLS)) { open(Tab.BILLS) }
            }
        },
    ) { padding ->
      Column(Modifier.padding(padding)) {
        UpdateBanner()
        NavHost(nav, startDestination = Tab.HOME.route, modifier = Modifier.weight(1f)) {
            composable(Tab.HOME.route) {
                HomeScreen(
                    onSeeAll = { go(Routes.spends()) },
                    onSearch = { go(Routes.spends(search = true)) },
                    onOpen = { route -> go(route) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            composable(
                "spends?search={search}",
                arguments = listOf(navArgument("search") { type = NavType.BoolType; defaultValue = false }),
            ) { entry ->
                SpendsScreen(
                    startWithSearch = entry.arguments?.getBoolean("search") ?: false,
                    onOpenSmsActivity = { nav.navigate(Routes.SMS) },
                )
            }
            composable(Tab.ANALYSIS.route) { AnalysisScreen() }
            composable(Tab.BILLS.route) { BillsScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { nav.popBackStack() }, onOpenSmsActivity = { nav.navigate(Routes.SMS) })
            }
            composable(Routes.SMS) { SmsActivityScreen(onBack = { nav.popBackStack() }) }
        }
      }
    }

    if (adding) {
        AddTransactionDialog(
            onDismiss = { adding = false },
            onAdd = { amount, type, cat, note -> quickAdd.add(amount, type, cat, note); adding = false },
        )
    }
}

@Composable
private fun RowScope.TabItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.testTag("tab_${tab.route}"),
        icon = { Icon(tab.icon, contentDescription = null) },
        label = { Text(tab.label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onSurface,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
