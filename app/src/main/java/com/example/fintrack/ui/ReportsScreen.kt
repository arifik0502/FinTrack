package com.example.fintrack.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fintrack.util.dayStart
import com.example.fintrack.util.money
import com.example.fintrack.util.monthRange
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ReportsScreen(vm: Vm) {
    val txs by vm.txs.collectAsState()
    var mode by remember { mutableIntStateOf(0) }
    val day = 86_400_000L
    val s = dayStart(System.currentTimeMillis())
    // buckets: start, endExclusive, label
    val buckets: List<Triple<Long, Long, String>> = when (mode) {
        0 -> (6 downTo 0).map { val st = s - it * day; Triple(st, st + day, SimpleDateFormat("EEE", Locale.getDefault()).format(Date(st))) }
        1 -> (3 downTo 0).map { val st = s - (it * 7 + 6) * day; Triple(st, st + 7 * day, SimpleDateFormat("d/M", Locale.getDefault()).format(Date(st))) }
        else -> (5 downTo 0).map {
            val (a, b) = monthRange(Calendar.getInstance().apply { add(Calendar.MONTH, -it) }.timeInMillis)
            Triple(a, b + 1, SimpleDateFormat("MMM", Locale.getDefault()).format(Date(a)))
        }
    }
    val from = buckets.first().first
    val range = txs.filter { it.ts >= from }
    val exp = range.filter { it.type == "EXPENSE" }
    val vals = buckets.map { (a, b, _) -> exp.filter { it.ts in a until b }.sumOf { it.amount } }
    val income = range.filter { it.type == "INCOME" }.sumOf { it.amount }
    val byCat = exp.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
    val label = listOf("Daily (7 days)", "Weekly (4 weeks)", "Monthly (6 months)")

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Chips(label, label[mode]) { mode = label.indexOf(it) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Spent", money(vals.sum())); StatCard("Income", money(income))
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { StatCard("Net", money(income - vals.sum())); StatCard("Avg / period", money(vals.average())) } }
        item { BarChart(vals, buckets.map { it.third }) }
        item { Text("By category", style = MaterialTheme.typography.titleMedium) }
        item { Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) { Pie(byCat) } }
        item { CategoryBars(byCat) }
        item {
            Text("Top merchants", style = MaterialTheme.typography.titleMedium)
        }
        item {
            val top = exp.filter { it.merchant.isNotBlank() }.groupBy { it.merchant }.mapValues { e -> e.value.sumOf { it.amount } }
                .entries.sortedByDescending { it.value }.take(5)
            Column { top.forEach { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(it.key); Text(money(it.value)) } } }
        }
    }
}
