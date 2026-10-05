package com.example.fintrack

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.fintrack.ui.App
import com.example.fintrack.ui.AppTheme
import com.example.fintrack.ui.LockScreen
import com.example.fintrack.ui.Vm
import com.example.fintrack.work.RecurringWorker
import com.example.fintrack.work.runRecurring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : FragmentActivity() {
    companion object { const val SCAN = "com.example.fintrack.SCAN" }

    private var scan by mutableIntStateOf(0)
    private var unlocked by mutableStateOf(false)
    private val vm: Vm by viewModels()
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (isScan(intent)) scan++
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "recurring", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<RecurringWorker>(1, TimeUnit.DAYS).build()
        )
        lifecycleScope.launch(Dispatchers.IO) { runRecurring(applicationContext) }
        setContent {
            AppTheme(vm.themeMode) {
                Surface(Modifier.fillMaxSize()) {
                    if (!vm.prefs.lockOn || unlocked) App(vm, scan)
                    else LockScreen(this@MainActivity, vm.prefs) { unlocked = true }
                }
            }
        }
    }

    private fun isScan(i: Intent?) = i?.action == SCAN || i?.getBooleanExtra("scan", false) == true

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isScan(intent)) scan++
    }

    override fun onStop() {
        super.onStop()
        if (vm.prefs.lockOn) unlocked = false
    }
}
