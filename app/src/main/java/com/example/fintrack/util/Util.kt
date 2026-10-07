package com.example.fintrack.util

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

fun f2(d: Double): String = String.format(Locale.US, "%.2f", d)
fun money(d: Double): String = "RM " + String.format(Locale.US, "%,.2f", d)
fun fmtDate(ts: Long): String = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ts))
fun sha(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

fun dayStart(ts: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ts
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun monthRange(ts: Long): Pair<Long, Long> {
    val c = Calendar.getInstance().apply { timeInMillis = dayStart(ts); set(Calendar.DAY_OF_MONTH, 1) }
    val a = c.timeInMillis
    c.add(Calendar.MONTH, 1)
    return a to (c.timeInMillis - 1)
}
