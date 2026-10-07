package com.example.fintrack.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.fintrack.data.AppDb
import com.example.fintrack.data.Tx
import com.example.fintrack.service.Notifier
import com.example.fintrack.util.money
import java.util.Calendar

class RecurringWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result { runRecurring(applicationContext); return Result.success() }
}

suspend fun runRecurring(c: Context) {
    val d = AppDb.get(c).dao()
    val cal = Calendar.getInstance()
    val ym = "%04d-%02d".format(cal[Calendar.YEAR], cal[Calendar.MONTH] + 1)
    val dom = cal[Calendar.DAY_OF_MONTH]
    val last = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    for (r in d.recsNow()) {
        if (!r.active || r.lastRun == ym || dom < minOf(r.day, last)) continue
        val t = Tx(amount = r.amount, type = r.type, merchant = r.name, category = r.category,
            accountId = r.accountId, ts = System.currentTimeMillis(), source = "RECURRING")
        val id = d.putTx(t)
        d.putRec(r.copy(lastRun = ym))
        Notifier.show(c, 3000 + (r.id % 1000).toInt(), "Recurring added", "${r.name} ${money(r.amount)}")
        Notifier.checkBudget(c, d, t.copy(id = id))
    }
}
