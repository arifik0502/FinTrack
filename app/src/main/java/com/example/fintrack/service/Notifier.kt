package com.example.fintrack.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.fintrack.MainActivity
import com.example.fintrack.data.AppDao
import com.example.fintrack.data.Tx
import com.example.fintrack.util.Prefs
import com.example.fintrack.util.money
import com.example.fintrack.util.monthRange

object Notifier {
    private const val CH = "alerts"

    @SuppressLint("MissingPermission")
    fun show(c: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (Build.VERSION.SDK_INT >= 26)
            c.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CH, "Alerts", NotificationManager.IMPORTANCE_DEFAULT))
        val pi = PendingIntent.getActivity(
            c, id, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(c, CH).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(text).setContentIntent(pi).setAutoCancel(true).build()
        NotificationManagerCompat.from(c).notify(id, n)
    }

    fun pick(c: Context, t: Tx) =
        show(c, 1000 + (t.id % 1000).toInt(), "Choose a category", "${t.merchant.ifBlank { t.app }} ${money(t.amount)}")

    suspend fun checkBudget(c: Context, d: AppDao, t: Tx) {
        if (t.type != "EXPENSE") return
        val (a, b) = monthRange(t.ts)
        val warn = Prefs(c).warnPct / 100.0
        for (key in listOf("ALL", t.category)) {
            val cap = d.budgetNow(key)?.cap ?: continue
            if (cap <= 0) continue
            val now = d.spent(a, b, key)
            val before = now - t.amount
            val label = if (key == "ALL") "Monthly" else key
            val id = 2000 + (key.hashCode() and 0xfff)
            when {
                now >= cap && before < cap -> show(c, id, "$label budget exceeded", "${money(now)} of ${money(cap)}")
                now >= cap * warn && before < cap * warn -> show(c, id, "$label budget ${(now / cap * 100).toInt()}% used", "${money(now)} of ${money(cap)}")
            }
        }
    }
}
