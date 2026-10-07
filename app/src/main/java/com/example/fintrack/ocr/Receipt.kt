package com.example.fintrack.ocr

import android.content.Context
import android.net.Uri
import com.example.fintrack.ai.GeminiClient
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.util.Calendar
import kotlin.coroutines.resume

/** Swap OCR engines by replacing [Ocr.read]. */
object Ocr {
    private val rec by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    suspend fun read(c: Context, uri: Uri): String = suspendCancellableCoroutine { cont ->
        try {
            rec.process(InputImage.fromFilePath(c, uri))
                .addOnSuccessListener { if (cont.isActive) cont.resume(it.text) }
                .addOnFailureListener { if (cont.isActive) cont.resume("") }
        } catch (e: Exception) { if (cont.isActive) cont.resume("") }
    }
}

data class Receipt(val merchant: String, val total: Double, val ts: Long, val items: String, val tax: Double, val pay: String)

object ReceiptParser {
    private val money = Regex("""\d{1,3}(?:,\d{3})*\.\d{2}|\d+\.\d{2}""")
    private val totalKw = Regex("""(?i)\b(grand\s*total|net\s*total|total|amount\s*due|jumlah)\b""")
    private val skipTotal = Regex("""(?i)(sub\s*-?\s*total|total\s*(qty|item|quantity|discount|saving|tax|sst|gst)|before|excl)""")
    private val taxKw = Regex("""(?i)\b(sst|gst|tax|service\s*charge|svc|cukai)\b""")
    private val iso = Regex("""(\d{4})[/\-.](\d{1,2})[/\-.](\d{1,2})""")
    private val dmy = Regex("""(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{2,4})""")
    private val named = Regex("""(?i)(\d{1,2})\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?,?\s+(\d{2,4})""")
    private val timeRe = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)(?::[0-5]\d)?\s?(AM|PM|am|pm)?""")
    private val itemRe = Regex("""^(?:(\d{1,2})\s*[xX]?\s+)?(.{2,}?)\s+(?:RM\s?)?(\d{1,4}\.\d{2})$""")
    private val itemSkip = Regex("""(?i)(total|tax|sst|gst|cash|change|round|tender|visa|master|debit|credit|balance|discount|amount|service|qty|invoice|receipt|tel|phone|reg|date|time|card|payment)""")
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val pays = listOf("cash" to "Cash", "visa" to "Visa", "master" to "Mastercard", "debit" to "Debit card", "touch" to "Touch 'n Go", "tng" to "Touch 'n Go",
        "grabpay" to "GrabPay", "boost" to "Boost", "duitnow" to "DuitNow", "shopeepay" to "ShopeePay", "qr" to "QR")
    private fun num(s: String) = s.replace(",", "").toDouble()

    fun parse(text: String): Receipt {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val merchant = lines.take(6).firstOrNull {
            it.count { ch -> ch.isLetter() } >= 3 && !Regex("""(?i)^(tax invoice|simplified|invoice|receipt|official|welcome|thank)""").containsMatchIn(it)
        }?.replace(Regex("""\s*\(?\d{5,}[-\s]?[A-Z]?\)?.*$"""), "")?.trim() ?: ""
        val cands = mutableListOf<Double>()
        lines.forEachIndexed { i, l ->
            if (totalKw.containsMatchIn(l) && !skipTotal.containsMatchIn(l)) {
                val a = money.findAll(l).map { num(it.value) }.toList()
                if (a.isNotEmpty()) cands += a.last()
                else lines.getOrNull(i + 1)?.let { n -> money.find(n)?.let { cands += num(it.value) } }
            }
        }
        val total = cands.maxOrNull() ?: money.findAll(text).map { num(it.value) }.maxOrNull() ?: 0.0
        val tax = lines.filter { taxKw.containsMatchIn(it) && !totalKw.containsMatchIn(it) }
            .sumOf { l -> money.findAll(l).lastOrNull()?.let { num(it.value) } ?: 0.0 }
        val items = lines.mapNotNull { l ->
            if (itemSkip.containsMatchIn(l)) return@mapNotNull null
            val m = itemRe.find(l) ?: return@mapNotNull null
            val name = m.groupValues[2].trim()
            if (name.count { it.isLetter() } < 2) null else "${m.groupValues[1].ifEmpty { "1" }}x $name ${m.groupValues[3]}"
        }.joinToString("\n")
        val low = text.lowercase()
        val pay = pays.firstOrNull { low.contains(it.first) }?.second ?: ""
        return Receipt(merchant, total, parseTs(text), items, tax, pay)
    }

    private fun parseTs(text: String): Long {
        val now = Calendar.getInstance()
        var y = 0; var mo = 0; var d = 0
        iso.find(text)?.let { y = it.groupValues[1].toInt(); mo = it.groupValues[2].toInt(); d = it.groupValues[3].toInt() }
        if (y == 0) dmy.find(text)?.let {
            d = it.groupValues[1].toInt(); mo = it.groupValues[2].toInt(); y = it.groupValues[3].toInt().let { v -> if (v < 100) v + 2000 else v }
        }
        if (y == 0) named.find(text)?.let {
            d = it.groupValues[1].toInt(); mo = months.indexOf(it.groupValues[2].lowercase()) + 1; y = it.groupValues[3].toInt().let { v -> if (v < 100) v + 2000 else v }
        }
        if (y !in 2000..2100 || mo !in 1..12 || d !in 1..31) return now.timeInMillis
        var h = 12; var mi = 0
        timeRe.find(text)?.let {
            h = it.groupValues[1].toInt(); mi = it.groupValues[2].toInt()
            val ap = it.groupValues[3].lowercase()
            if (ap == "pm" && h < 12) h += 12; if (ap == "am" && h == 12) h = 0
        }
        val ts = Calendar.getInstance().apply { set(y, mo - 1, d, h, mi, 0) }.timeInMillis
        return if (ts > System.currentTimeMillis()) now.timeInMillis else ts
    }

    /** Cloud fallback; only OCR text (no image) is sent, and only if a key is configured. */
    suspend fun ai(g: GeminiClient, text: String): Receipt? {
        val out = g.ask(
            "Extract from this Malaysian receipt OCR text. Reply JSON: {\"merchant\":string,\"total\":number,\"date\":\"yyyy-MM-dd\" or \"\",\"time\":\"HH:mm\" or \"\"," +
                "\"items\":[{\"name\":string,\"qty\":number,\"price\":number}],\"tax\":number,\"payment\":string}.\n\n" + text.take(4000)
        ) ?: return null
        return try {
            val j = JSONObject(out.trim().removePrefix("```json").removeSuffix("```").trim())
            val base = parse(text)
            val ts = if (j.optString("date").isNotBlank()) parseTs(j.optString("date") + " " + j.optString("time")) else base.ts
            val arr = j.optJSONArray("items")
            val items = (0 until (arr?.length() ?: 0)).joinToString("\n") {
                val o = arr!!.getJSONObject(it); "${o.optInt("qty", 1)}x ${o.optString("name")} ${"%.2f".format(java.util.Locale.US, o.optDouble("price"))}"
            }
            Receipt(j.optString("merchant"), j.optDouble("total", 0.0), ts, items.ifBlank { base.items }, j.optDouble("tax", 0.0), j.optString("payment"))
        } catch (e: Exception) { null }
    }
}
