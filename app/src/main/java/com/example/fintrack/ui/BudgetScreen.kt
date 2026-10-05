package com.example.fintrack.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fintrack.data.Cats
import com.example.fintrack.data.Goal
import com.example.fintrack.data.Recurring
import com.example.fintrack.util.money
import com.example.fintrack.util.monthRange

@Composable
fun BudgetScreen(vm: Vm) {
    var tab by remember { mutableIntStateOf(0) }
    Column {
        TabRow(selectedTabIndex = tab) {
            listOf("Budgets", "Goals", "Bills").forEachIndexed { i, s -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(s) }) }
        }
        when (tab) { 0 -> Budgets(vm); 1 -> Goals(vm); else -> Bills(vm) }
    }
}

@Composable
private fun Budgets(vm: Vm) {
    val txs by vm.txs.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val (a, b) = remember { monthRange(System.currentTimeMillis()) }
    val month = txs.filter { it.type == "EXPENSE" && it.ts in a..b }
    var edit by remember { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Monthly budget & category limits. You get a warning at ${vm.prefs.warnPct}% and when exceeded.", style = MaterialTheme.typography.bodySmall) }
        items(listOf("ALL") + Cats.all) { k ->
            val cap = budgets.firstOrNull { it.cat == k }?.cap
            val spent = if (k == "ALL") month.sumOf { it.amount } else month.filter { it.category == k }.sumOf { it.amount }
            Card(onClick = { edit = k }) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (k == "ALL") "Overall" else k, style = MaterialTheme.typography.titleSmall)
                        Text(if (cap != null) "${money(spent)} / ${money(cap)}" else "${money(spent)} • no limit")
                    }
                    if (cap != null) {
                        val f = (spent / cap).toFloat()
                        LinearProgressIndicator(progress = { f.coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(top = 8.dp),
                            color = if (f >= 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    edit?.let { k ->
        FormDialog("Limit: ${if (k == "ALL") "Overall" else k} (0 clears)", listOf("Monthly limit (RM)"), setOf(0),
            listOf(budgets.firstOrNull { it.cat == k }?.cap?.toString() ?: ""),
            onOk = { vm.setBudget(k, it[0].toDoubleOrNull() ?: 0.0); edit = null }, onDismiss = { edit = null })
    }
}

@Composable
private fun Goals(vm: Vm) {
    val goals by vm.goals.collectAsState()
    var add by remember { mutableStateOf(false) }
    var topUp by remember { mutableStateOf<Goal?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Button(onClick = { add = true }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("New goal") } }
        items(goals, key = { it.id }) { g ->
            Card(onClick = { topUp = g }) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(g.name, style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = { vm.delGoal(g) }) { Icon(Icons.Filled.Delete, "Delete") }
                    }
                    Text("${money(g.saved)} / ${money(g.target)}")
                    LinearProgressIndicator(progress = { (g.saved / g.target).toFloat().coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
        }
    }
    if (add) FormDialog("New goal", listOf("Name", "Target (RM)"), setOf(1), onOk = {
        val t = it[1].toDoubleOrNull(); if (it[0].isNotBlank() && t != null && t > 0) vm.putGoal(Goal(name = it[0].trim(), target = t)); add = false
    }, onDismiss = { add = false })
    topUp?.let { g ->
        FormDialog("Add to ${g.name} (negative to withdraw)", listOf("Amount (RM)"), setOf(0), onOk = {
            it[0].toDoubleOrNull()?.let { v -> vm.putGoal(g.copy(saved = (g.saved + v).coerceAtLeast(0.0))) }; topUp = null
        }, onDismiss = { topUp = null })
    }
}

@Composable
private fun Bills(vm: Vm) {
    val recs by vm.recs.collectAsState()
    val accounts by vm.accounts.collectAsState()
    var add by remember { mutableStateOf(false) }
    var cat by remember { mutableStateOf("Bills") }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Button(onClick = { add = true }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("New recurring bill") } }
        items(recs, key = { it.id }) { r ->
            Card {
                ListItem(
                    headlineContent = { Text(r.name) },
                    supportingContent = { Text("${money(r.amount)} • day ${r.day} • ${r.category} • ${accounts.firstOrNull { it.id == r.accountId }?.name ?: ""}") },
                    trailingContent = {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Switch(r.active, { vm.putRec(r.copy(active = it)) })
                            IconButton(onClick = { vm.delRec(r) }) { Icon(Icons.Filled.Delete, "Delete") }
                        }
                    }
                )
            }
        }
    }
    if (add) FormDialog("New recurring bill", listOf("Name", "Amount (RM)", "Day of month (1-31)"), setOf(1, 2), listOf("", "", "1"),
        onOk = {
            val amt = it[1].toDoubleOrNull(); val day = it[2].toIntOrNull()?.coerceIn(1, 31) ?: 1
            if (it[0].isNotBlank() && amt != null && amt > 0) vm.putRec(Recurring(name = it[0].trim(), amount = amt, category = cat, accountId = vm.prefs.defAccount, day = day))
            add = false
        }, onDismiss = { add = false }, extra = { Chips(Cats.all, cat) { cat = it } })
}
