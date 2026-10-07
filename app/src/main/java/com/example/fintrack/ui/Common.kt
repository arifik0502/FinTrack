package com.example.fintrack.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.fintrack.data.Cats
import com.example.fintrack.data.Tx
import com.example.fintrack.util.f2
import com.example.fintrack.util.fmtDate
import com.example.fintrack.util.money

fun catColor(c: String): Color = palette[Cats.all.indexOf(c).coerceAtLeast(0) % palette.size]

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Chips(opts: List<String>, sel: String?, onSel: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opts.forEach { o -> FilterChip(selected = o == sel, onClick = { onSel(o) }, label = { Text(o) }) }
    }
}

@Composable
fun RowScope.StatCard(title: String, value: String, color: Color = Color.Unspecified) {
    Card(Modifier.weight(1f)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium, color = color)
        }
    }
}

@Composable
fun TxRow(t: Tx, acc: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        overlineContent = if (t.pending) ({ Text("Needs category") }) else null,
        headlineContent = { Text(t.merchant.ifBlank { t.category }) },
        supportingContent = { Text("${t.category} • $acc • ${fmtDate(t.ts)}") },
        trailingContent = {
            val inc = t.type == "INCOME"
            Text((if (inc) "+" else "-") + f2(t.amount), color = if (inc) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
        }
    )
}

@Composable
fun CategoryBars(data: Map<String, Double>) {
    val tot = data.values.sum()
    if (tot <= 0) { Text("No spending yet"); return }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        data.entries.sortedByDescending { it.value }.forEach { (k, v) ->
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(k); Text(money(v)) }
                LinearProgressIndicator(progress = { (v / tot).toFloat() }, color = catColor(k), modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun Pie(data: Map<String, Double>, modifier: Modifier = Modifier) {
    val tot = data.values.sum()
    if (tot <= 0) return
    Canvas(modifier.size(140.dp)) {
        var start = -90f
        data.forEach { (k, v) ->
            val sw = (v / tot * 360).toFloat()
            drawArc(catColor(k), start, sw, true)
            start += sw
        }
    }
}

@Composable
fun BarChart(values: List<Double>, labels: List<String>, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme.primary
    val tc = MaterialTheme.colorScheme.onSurface.toArgb()
    Canvas(modifier.fillMaxWidth().height(170.dp)) {
        val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
        val w = size.width / values.size.coerceAtLeast(1)
        val paint = android.graphics.Paint().apply { color = tc; textSize = 26f; isAntiAlias = true }
        values.forEachIndexed { i, v ->
            val h = (v / max * (size.height - 40)).toFloat()
            drawRect(c, Offset(i * w + w * 0.2f, size.height - 28 - h), Size(w * 0.6f, h))
            drawContext.canvas.nativeCanvas.drawText(labels[i], i * w + w * 0.2f, size.height - 4, paint)
        }
    }
}

@Composable
fun FormDialog(
    title: String, labels: List<String>, numeric: Set<Int> = emptySet(), initial: List<String> = emptyList(),
    onOk: (List<String>) -> Unit, onDismiss: () -> Unit, extra: @Composable () -> Unit = {}
) {
    val vals = remember { mutableStateListOf(*labels.indices.map { initial.getOrElse(it) { "" } }.toTypedArray()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEachIndexed { i, l ->
                    OutlinedTextField(
                        value = vals[i], onValueChange = { vals[i] = it }, label = { Text(l) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = if (i in numeric) KeyboardType.Decimal else KeyboardType.Text)
                    )
                }
                extra()
            }
        },
        confirmButton = { TextButton(onClick = { onOk(vals.toList()) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
