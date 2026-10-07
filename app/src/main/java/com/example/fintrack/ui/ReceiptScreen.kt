package com.example.fintrack.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.example.fintrack.ai.GeminiClient
import com.example.fintrack.ai.HybridCategorizer
import com.example.fintrack.data.Cats
import com.example.fintrack.data.Tx
import com.example.fintrack.ocr.Ocr
import com.example.fintrack.ocr.ReceiptParser
import com.example.fintrack.util.f2
import com.example.fintrack.util.fmtDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ReceiptScreen(vm: Vm, nav: NavController, auto: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val accounts by vm.accounts.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var camUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var started by rememberSaveable { mutableStateOf(false) }
    var ready by rememberSaveable { mutableStateOf(false) }
    var merchant by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf("Others") }
    var conf by rememberSaveable { mutableStateOf(1f) }
    var ts by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var items by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var acc by rememberSaveable { mutableStateOf(1L) }

    fun process(uri: Uri) {
        busy = true; err = ""
        scope.launch {
            val text = withContext(Dispatchers.IO) { Ocr.read(ctx, uri) }
            var r = ReceiptParser.parse(text)
            val key = vm.prefs.geminiKey
            if (key.isNotBlank() && text.isNotBlank() && (r.total <= 0 || r.merchant.isBlank()))
                ReceiptParser.ai(GeminiClient(key, vm.prefs.geminiModel), text)?.let { r = it }
            val c = HybridCategorizer(ctx, vm.dao).categorize(r.merchant, r.total)
            merchant = r.merchant; amount = if (r.total > 0) f2(r.total) else ""; ts = r.ts; items = r.items
            note = listOfNotNull(r.pay.takeIf { it.isNotBlank() }?.let { "Paid: $it" }, r.tax.takeIf { it > 0 }?.let { "Tax/charges: ${f2(it)}" }).joinToString(" • ")
            cat = c?.name ?: "Others"; conf = c?.conf ?: 0f
            acc = accounts.firstOrNull { it.kind == "CASH" }?.id ?: 1L
            ready = true; busy = false
            if (text.isBlank()) err = "No text found. Fill in manually or rescan."
            if (uri.authority?.endsWith(".files") == true) runCatching { ctx.contentResolver.delete(uri, null, null) }
        }
    }

    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) camUri?.let { process(it) } }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> if (u != null) process(u) }
    fun shoot() {
        val dir = File(ctx.cacheDir, "img").apply { mkdirs() }
        val u = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(dir, "r${System.currentTimeMillis()}.jpg"))
        camUri = u; cam.launch(u)
    }
    LaunchedEffect(auto) { if (auto && !started) { started = true; shoot() } }

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Receipt scanner", style = MaterialTheme.typography.titleLarge)
        if (busy) { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Text("Reading receipt…") } }
        if (!busy && !ready) {
            Button(onClick = { shoot() }, Modifier.fillMaxWidth()) { Icon(Icons.Filled.CameraAlt, null); Spacer(Modifier.width(8.dp)); Text("Scan with camera") }
            OutlinedButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text("Import from gallery")
            }
        }
        if (err.isNotBlank()) Text(err, color = MaterialTheme.colorScheme.error)
        if (ready && !busy) {
            OutlinedTextField(merchant, { merchant = it }, Modifier.fillMaxWidth(), label = { Text("Merchant") }, singleLine = true)
            OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text("Total (RM)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            if (conf < vm.prefs.threshold) Text("Low confidence – please confirm the category", color = MaterialTheme.colorScheme.error)
            Chips(Cats.all, cat) { cat = it; conf = 1f }
            Chips(accounts.map { it.name }, accounts.firstOrNull { it.id == acc }?.name) { n -> accounts.firstOrNull { it.name == n }?.let { acc = it.id } }
            Text(fmtDate(ts), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(items, { items = it }, Modifier.fillMaxWidth(), label = { Text("Items") }, minLines = 2)
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Note") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val v = amount.toDoubleOrNull() ?: return@Button
                    vm.saveTx(Tx(amount = v, merchant = merchant.trim(), category = cat, accountId = acc, ts = ts, note = note,
                        source = "RECEIPT", items = items, conf = conf), learnRule = true)
                    nav.navigate("home") { popUpTo("home") }
                }, Modifier.weight(1f)) { Text("Save") }
                OutlinedButton(onClick = { ready = false; amount = ""; merchant = ""; items = "" }, Modifier.weight(1f)) { Text("Rescan") }
            }
        }
    }
}
