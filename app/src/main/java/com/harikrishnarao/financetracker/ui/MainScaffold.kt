package com.harikrishnarao.financetracker.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.harikrishnarao.financetracker.R
import com.harikrishnarao.financetracker.ui.bills.BillsScreen
import com.harikrishnarao.financetracker.ui.budget.BudgetScreen
import com.harikrishnarao.financetracker.ui.home.HomeScreen
import com.harikrishnarao.financetracker.ui.settings.SettingsScreen
import com.harikrishnarao.financetracker.ui.spends.SpendsScreen

private enum class Tab(val route: String, val label: String) {
    HOME("home", "Home"),
    SPENDS("spends", "Spends"),
    BUDGET("budget", "Budget"),
    BILLS("bills", "Bills"),
    SETTINGS("settings", "Settings"),
}

@Composable
private fun Tab.icon(): ImageVector = when (this) {
    Tab.HOME -> Icons.Filled.Home
    Tab.SPENDS -> Icons.AutoMirrored.Filled.List
    Tab.BUDGET -> ImageVector.vectorResource(R.drawable.ic_budget)
    Tab.BILLS -> Icons.Filled.Notifications
    Tab.SETTINGS -> Icons.Filled.Settings
}

@Composable
fun MainScaffold() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination

    fun go(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = { go(tab.route) },
                        icon = { Icon(tab.icon(), contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.HOME.route, modifier = Modifier.padding(padding)) {
            composable(Tab.HOME.route) {
                HomeScreen(onSeeAll = { go(Tab.SPENDS.route) }, onOpenBudget = { go(Tab.BUDGET.route) })
            }
            composable(Tab.SPENDS.route) { SpendsScreen() }
            composable(Tab.BUDGET.route) { BudgetScreen() }
            composable(Tab.BILLS.route) { BillsScreen() }
            composable(Tab.SETTINGS.route) { SettingsScreen() }
        }
    }
}
