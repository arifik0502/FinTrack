package com.example.fintrack.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.navigation.NavController
import com.example.fintrack.data.Cats
import com.example.fintrack.data.Tx
import com.example.fintrack.util.fmtDate
import java.util.Calendar

@Composable
fun Transactions(vm: Vm, nav: NavController) {
    val txs by vm.txs.collectAsState()
    val accounts by vm.accounts.collectAsState()
    var q by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("All") }
    var type by remember { mutableStateOf("All") }
    val accName = accounts.associate { it.id to it.name }
    val list = txs.filter {
        (cat == "All" || it.category == cat) &&
            (type == "All" || it.type == type.uppercase()) &&
            (q.isBlank() || listOf(it.merchant, it.note, it.category, it.app, it.items).any { s -> s.contains(q, true) })
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true,
            label = { Text("Search") }, leadingIcon = { Icon(Icons.Filled.Search, null) })
        Spacer(Modifier.height(8.dp))
        Chips(listOf("All", "Income", "Expense"), type) { type = it }
        Chips(listOf("All") + Cats.all, cat) { cat = it }
        LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
            items(list, key = { it.id }) { t -> TxRow(t, accName[t.accountId] ?: "") { nav.navigate("add?id=${t.id}") } }
        }
    }
}

@Composable
fun AddScreen(vm: Vm, nav: NavController, id: Long) {
    val ctx = LocalContext.current
    val accounts by vm.accounts.collectAsState()
    var orig by remember { mutableStateOf<Tx?>(null) }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("EXPENSE") }
    var merchant by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("Food") }
    var acc by remember { mutableLongStateOf(1L) }
    var ts by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var note by remember { mutableStateOf("") }
    var confirmDel by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(id) {
        if (id > 0) vm.dao.tx(id)?.let {
            orig = it; amount = it.amount.toString(); type = it.type; merchant = it.merchant; cat = it.category
            acc = it.accountId; ts = it.ts; note = it.note
        } else focus.requestFocus()
    }
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (id > 0) "Edit transaction" else "Add transaction", style = MaterialTheme.typography.titleLarge)
            if (id > 0) IconButton(onClick = { confirmDel = true }) { Icon(Icons.Filled.Delete, "Delete") }
        }
        Chips(listOf("Expense", "Income"), type.lowercase().replaceFirstChar { it.uppercase() }) { type = it.uppercase() }
        OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth().focusRequester(focus), label = { Text("Amount (RM)") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        Chips(Cats.all, cat) { cat = it }
        OutlinedTextField(merchant, { merchant = it }, Modifier.fillMaxWidth(), label = { Text("Merchant / title") }, singleLine = true)
        Chips(accounts.map { it.name }, accounts.firstOrNull { it.id == acc }?.name) { n -> accounts.firstOrNull { it.name == n }?.let { acc = it.id } }
        OutlinedButton(onClick = {
            val c = Calendar.getInstance().apply { timeInMillis = ts }
            android.app.DatePickerDialog(ctx, { _, y, m, d ->
                ts = Calendar.getInstance().apply { timeInMillis = ts; set(y, m, d) }.timeInMillis
            }, c[Calendar.YEAR], c[Calendar.MONTH], c[Calendar.DAY_OF_MONTH]).show()
        }) { Text(fmtDate(ts)) }
        OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Note") })
        if (orig?.items?.isNotBlank() == true) Text("Items:\n${orig!!.items}", style = MaterialTheme.typography.bodySmall)
        Button(
            onClick = {
                val v = amount.toDoubleOrNull() ?: return@Button
                if (v <= 0) return@Button
                val base = orig ?: Tx(amount = v, ts = ts)
                vm.saveTx(base.copy(amount = v, type = type, merchant = merchant.trim(), category = cat, accountId = acc, ts = ts,
                    note = note, pending = false, conf = 1f), learnRule = true)
                nav.popBackStack()
            }, modifier = Modifier.fillMaxWidth()
        ) { Text("Save") }
    }
    if (confirmDel) AlertDialog(
        onDismissRequest = { confirmDel = false }, title = { Text("Delete transaction?") },
        confirmButton = { TextButton(onClick = { orig?.let { vm.deleteTx(it) }; confirmDel = false; nav.popBackStack() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDel = false }) { Text("Cancel") } }
    )
}
