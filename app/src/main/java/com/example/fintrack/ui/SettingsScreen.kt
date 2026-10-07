package com.example.fintrack.ui

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.fintrack.data.Account
import com.example.fintrack.export.Exporter
import com.example.fintrack.util.money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(vm: Vm) {
    val ctx = LocalContext.current
    val act = ctx as FragmentActivity
    val scope = rememberCoroutineScope()
    val txs by vm.txs.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val p = vm.prefs
    val accName = accounts.associate { it.id to it.name }
    var listenerOn by remember { mutableStateOf(false) }
    var overlayOn by remember { mutableStateOf(false) }
    var lockOn by remember { mutableStateOf(p.lockOn) }
    var bioOn by remember { mutableStateOf(p.bioOn) }
    var thr by remember { mutableFloatStateOf(p.threshold) }
    var warn by remember { mutableFloatStateOf(p.warnPct.toFloat()) }
    var key by remember { mutableStateOf(p.userKey) }
    var model by remember { mutableStateOf(p.geminiModel) }
    var pinDlg by remember { mutableStateOf(false) }
    var accDlg by remember { mutableStateOf(false) }
    var restoreDlg by remember { mutableStateOf<android.net.Uri?>(null) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) overlayOn = Settings.canDrawOverlays(ctx)
            if (e == Lifecycle.Event.ON_RESUME) listenerOn = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    fun toast(s: String) { scope.launch(Dispatchers.Main) { Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show() } }
    fun export(name: String, f: (android.net.Uri) -> Unit): (android.net.Uri?) -> Unit = { u ->
        if (u != null) scope.launch(Dispatchers.IO) { try { f(u); toast("Saved $name") } catch (e: Exception) { toast("Export failed") } }
    }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv"), export("CSV") { Exporter.csv(ctx, it, txs, accName) })
    val xls = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), export("Excel") { Exporter.xlsx(ctx, it, txs, accName) })
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf"), export("PDF") { Exporter.pdf(ctx, it, txs, accName) })
    val bak = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
        if (u != null) scope.launch { try { Exporter.backup(ctx, u); toast("Backup saved") } catch (e: Exception) { toast("Backup failed") } }
    }
    val res = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) restoreDlg = u }

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Auto-detect", style = MaterialTheme.typography.titleMedium)
        Text(if (listenerOn) "Notification access: ON" else "Notification access: OFF (needed to detect payments)")
        Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Open notification access") }
        Text(if (overlayOn) "Popup over other apps: ON" else "Popup over other apps: OFF (needed for the automatic category popup)")
        Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${ctx.packageName}"))) }) { Text("Allow popup over other apps") }
        Text("Ask category when confidence below ${(thr * 100).toInt()}%")
        Slider(thr, { thr = it }, valueRange = 0.5f..0.95f, onValueChangeFinished = { p.threshold = thr })
        Text("Budget warning at ${warn.toInt()}%")
        Slider(warn, { warn = it }, valueRange = 50f..100f, onValueChangeFinished = { p.warnPct = warn.toInt() })

        OutlinedButton(onClick = { scope.launch(Dispatchers.IO) { vm.dao.clearRules() }; toast("Learned categories cleared") }) { Text("Reset learned categories") }
        HorizontalDivider()
        Text("AI (optional, Gemini free tier)", style = MaterialTheme.typography.titleMedium)
        Text("Only merchant name + amount (or OCR text if parsing fails) is sent. Local rules run first.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(key, { key = it; p.userKey = it }, Modifier.fillMaxWidth(), label = { Text("API key override (optional)") }, singleLine = true)
        OutlinedTextField(model, { model = it; p.geminiModel = it }, Modifier.fillMaxWidth(), label = { Text("Model") }, singleLine = true)

        HorizontalDivider()
        Text("Accounts", style = MaterialTheme.typography.titleMedium)
        accounts.forEach { a ->
            val bal = a.initial + txs.filter { it.accountId == a.id }.sumOf { if (it.type == "INCOME") it.amount else -it.amount }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${a.name} (${a.kind.lowercase()})"); Text(money(bal)) }
        }
        OutlinedButton(onClick = { accDlg = true }) { Text("Add account") }

        HorizontalDivider()
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        Chips(listOf("System", "Light", "Dark"), listOf("System", "Light", "Dark")[vm.themeMode]) { vm.changeTheme(listOf("System", "Light", "Dark").indexOf(it)) }

        HorizontalDivider()
        Text("Security", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PIN lock", Modifier.weight(1f))
            Switch(lockOn, { on -> if (on) pinDlg = true else { lockOn = false; p.lockOn = false; bioOn = false; p.bioOn = false } })
        }
        if (lockOn && canBio(act)) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Biometric unlock", Modifier.weight(1f)); Switch(bioOn, { bioOn = it; p.bioOn = it })
        }

        HorizontalDivider()
        Text("Export & backup", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { csv.launch("fintrack.csv") }) { Text("CSV") }
            OutlinedButton(onClick = { xls.launch("fintrack.xlsx") }) { Text("Excel") }
            OutlinedButton(onClick = { pdf.launch("fintrack.pdf") }) { Text("PDF") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { bak.launch("fintrack-backup.json") }) { Text("Backup") }
            OutlinedButton(onClick = { res.launch(arrayOf("application/json", "*/*")) }) { Text("Restore") }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (pinDlg) FormDialog("Set PIN (4-12 digits)", listOf("PIN", "Confirm PIN"), setOf(0, 1), onOk = {
        if (it[0].length in 4..12 && it[0] == it[1] && it[0].all(Char::isDigit)) {
            p.setPin(it[0]); p.lockOn = true; lockOn = true; pinDlg = false
        } else toast("PINs must match (4-12 digits)")
    }, onDismiss = { pinDlg = false })
    if (accDlg) FormDialog("New account", listOf("Name", "Type: bank / ewallet / cash", "Opening balance"), setOf(2), listOf("", "bank", "0"), onOk = {
        if (it[0].isNotBlank()) vm.putAcc(Account(name = it[0].trim(), kind = it[1].trim().uppercase().let { k -> if (k in listOf("BANK", "EWALLET", "CASH")) k else "BANK" }, initial = it[2].toDoubleOrNull() ?: 0.0))
        accDlg = false
    }, onDismiss = { accDlg = false })
    restoreDlg?.let { u ->
        AlertDialog(onDismissRequest = { restoreDlg = null }, title = { Text("Restore backup?") }, text = { Text("This replaces all current data.") },
            confirmButton = { TextButton(onClick = { restoreDlg = null; scope.launch { toast(if (Exporter.restore(ctx, u)) "Restored" else "Restore failed") } }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { restoreDlg = null }) { Text("Cancel") } })
    }
}
