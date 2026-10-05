package com.example.fintrack.util

import android.content.Context
import com.example.fintrack.BuildConfig
import java.util.UUID

class Prefs(c: Context) {
    private val p = c.applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    var theme: Int get() = p.getInt("theme", 0); set(v) = p.edit().putInt("theme", v).apply() // 0 sys,1 light,2 dark
    var threshold: Float get() = p.getFloat("thr", 0.75f); set(v) = p.edit().putFloat("thr", v).apply()
    var warnPct: Int get() = p.getInt("warn", 80); set(v) = p.edit().putInt("warn", v).apply()
    var userKey: String get() = p.getString("gkey", "") ?: ""; set(v) = p.edit().putString("gkey", v.trim()).apply()
    val geminiKey: String get() = userKey.ifBlank { BuildConfig.GEMINI_API_KEY }
    var geminiModel: String get() = p.getString("gmodel", "gemini-2.5-flash") ?: "gemini-2.5-flash"; set(v) = p.edit().putString("gmodel", v.trim()).apply()
    var lockOn: Boolean get() = p.getBoolean("lock", false); set(v) = p.edit().putBoolean("lock", v).apply()
    var bioOn: Boolean get() = p.getBoolean("bio", false); set(v) = p.edit().putBoolean("bio", v).apply()
    var defAccount: Long get() = p.getLong("defacc", 2L); set(v) = p.edit().putLong("defacc", v).apply()

    fun hasPin() = !p.getString("pin", "").isNullOrEmpty()
    fun setPin(pin: String) {
        val salt = UUID.randomUUID().toString().take(8)
        p.edit().putString("pin", "$salt:${sha(salt + pin)}").apply()
    }
    fun checkPin(pin: String): Boolean {
        val s = p.getString("pin", "") ?: return false
        val (salt, h) = s.split(":").let { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
        return sha(salt + pin) == h
    }
}
