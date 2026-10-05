package com.example.fintrack.service

import android.content.Context
import com.example.fintrack.ai.Cat
import com.example.fintrack.ai.HybridCategorizer
import com.example.fintrack.data.AppDb
import com.example.fintrack.data.Tx
import com.example.fintrack.parser.Parsers
import com.example.fintrack.util.Prefs
import com.example.fintrack.util.sha

object Ingest {
    /** Only parsed fields + a hash are stored; raw notification text is never persisted. */
    suspend fun fromNotification(c: Context, pkg: String, label: String, title: String, text: String, ts: Long) {
        if (pkg == c.packageName) return
        val p = Parsers.parse(label, title, text, ts) ?: return
        val d = AppDb.get(c).dao()
        val h = sha(pkg + title + text).take(24)
        if (d.hashDup(h, ts - 86_400_000L) > 0) return
        val k = "${p.type}|${p.amount}"
        if (d.amtDup(k, p.ts - 120_000, p.ts + 120_000) > 0) return
        val prefs = Prefs(c)
        val cat = if (p.type == "INCOME") Cat("Others", 1f)
        else HybridCategorizer(c, d).categorize(p.merchant, p.amount) ?: Cat("Others", 0f)
        val pending = cat.conf < prefs.threshold
        val accs = d.accountsNow()
        val acc = accs.firstOrNull { it.name.contains(label, true) || label.contains(it.name, true) }?.id
            ?: accs.firstOrNull { it.id == prefs.defAccount }?.id ?: accs.firstOrNull()?.id ?: 1L
        val id = d.putTx(Tx(amount = p.amount, type = p.type, merchant = p.merchant, category = cat.name, accountId = acc,
            ts = p.ts, source = "AUTO", app = label, pending = pending, conf = cat.conf, dedup = k, hash = h))
        val t = d.tx(id) ?: return
        if (pending) Notifier.pick(c, t)
        Notifier.checkBudget(c, d, t)
    }
}
