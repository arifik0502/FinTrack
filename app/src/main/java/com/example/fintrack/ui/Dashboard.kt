package com.example.fintrack.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.fintrack.util.money
import com.example.fintrack.util.monthRange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun Dashboard(vm: Vm, nav: NavController) {
    val txs by vm.txs.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val goals by vm.goals.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val now = System.currentTimeMillis()
    val (a, b) = remember(now / 60000) { monthRange(now) }
    val month = txs.filter { it.ts in a..b }
    val spent = month.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val income = month.filter { it.type == "INCOME" }.sumOf { it.amount }
    val cap = budgets.firstOrNull { it.cat == "ALL" }?.cap
    val byCat = month.filter { it.type == "EXPENSE" }.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
    val accName = accounts.associate { it.id to it.name }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(now)), style = MaterialTheme.typography.headlineSmall) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Spent", money(spent), MaterialTheme.colorScheme.error)
                StatCard("Income", money(income), Color(0xFF2E7D32))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Budget left", cap?.let { money(it - spent) } ?: "Not set")
                StatCard("Savings", money(goals.sumOf { it.saved }))
            }
        }
        if (cap != null) item { LinearProgressIndicator(progress = { (spent / cap).toFloat().coerceIn(0f, 1f) }, Modifier.fillMaxWidth()) }
        item { Text("By category", style = MaterialTheme.typography.titleMedium) }
        item { CategoryBars(byCat) }
        item { Text("Recent", style = MaterialTheme.typography.titleMedium) }
        items(txs.take(5).size) { i -> val t = txs[i]; TxRow(t, accName[t.accountId] ?: "") { nav.navigate("add?id=${t.id}") } }
        item { Spacer(Modifier.height(72.dp)) }
    }
}
