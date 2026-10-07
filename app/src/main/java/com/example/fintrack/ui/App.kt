package com.example.fintrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.fintrack.data.Cats
import com.example.fintrack.util.money

@Composable
fun App(vm: Vm, scan: Int) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val txs by vm.txs.collectAsState()
    val tabs = listOf(
        Triple("home", "Home", Icons.Filled.Home),
        Triple("tx", "Activity", Icons.AutoMirrored.Filled.List),
        Triple("reports", "Reports", Icons.Filled.PieChart),
        Triple("budget", "Budget", Icons.Filled.AccountBalanceWallet),
        Triple("settings", "Settings", Icons.Filled.Settings)
    )
    LaunchedEffect(scan) { if (scan > 0) nav.navigate("receipt?auto=true") { launchSingleTop = true } }

    Scaffold(
        bottomBar = {
            if (route in tabs.map { it.first }) NavigationBar {
                tabs.forEach { (r, l, i) ->
                    NavigationBarItem(selected = route == r, icon = { Icon(i, l) }, label = { Text(l) }, onClick = {
                        nav.navigate(r) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true }
                    })
                }
            }
        },
        floatingActionButton = {
            if (route == "home" || route == "tx") Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                SmallFloatingActionButton(onClick = { nav.navigate("receipt?auto=true") }) { Icon(Icons.Filled.CameraAlt, "Scan receipt") }
                androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                FloatingActionButton(onClick = { nav.navigate("add?id=0") }) { Icon(Icons.Filled.Add, "Add") }
            }
        }
    ) { pad ->
        NavHost(nav, "home", Modifier.padding(pad)) {
            composable("home") { Dashboard(vm, nav) }
            composable("tx") { Transactions(vm, nav) }
            composable("reports") { ReportsScreen(vm) }
            composable("budget") { BudgetScreen(vm) }
            composable("settings") { SettingsScreen(vm) }
            composable("add?id={id}", listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L })) {
                AddScreen(vm, nav, it.arguments?.getLong("id") ?: 0L)
            }
            composable("receipt?auto={auto}", listOf(navArgument("auto") { type = NavType.BoolType; defaultValue = false })) {
                ReceiptScreen(vm, nav, it.arguments?.getBoolean("auto") ?: false)
            }
        }
    }

    txs.firstOrNull { it.pending }?.let { t ->
        AlertDialog(
            onDismissRequest = { vm.skipPending(t) },
            title = { Text("Choose category") },
            text = {
                Column {
                    Text("${t.merchant.ifBlank { t.app }} • ${money(t.amount)}")
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                    Chips(Cats.all, null) { vm.setCategory(t, it) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { vm.skipPending(t) }) { Text("Skip") } }
        )
    }
}
