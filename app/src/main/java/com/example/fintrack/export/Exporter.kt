package com.example.fintrack.export

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.example.fintrack.data.*
import com.example.fintrack.util.f2
import com.example.fintrack.util.fmtDate
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class Backup(
    val v: Int = 1, val tx: List<Tx>, val accounts: List<Account>, val budgets: List<Budget>,
    val goals: List<Goal>, val recs: List<Recurring>, val rules: List<Rule>
)

object Exporter {
    private val head = listOf("Date", "Type", "Amount", "Category", "Merchant", "Account", "Note", "Source")
    private fun rows(t: List<Tx>, acc: Map<Long, String>) = t.map {
        listOf(fmtDate(it.ts), it.type, f2(it.amount), it.category, it.merchant, acc[it.accountId] ?: "", it.note, it.source)
    }
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun csv(c: Context, u: Uri, t: List<Tx>, acc: Map<Long, String>) {
        c.contentResolver.openOutputStream(u)?.bufferedWriter()?.use { w ->
            (listOf(head) + rows(t, acc)).forEach { r ->
                w.write(r.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" }); w.newLine()
            }
        }
    }

    fun xlsx(c: Context, u: Uri, t: List<Tx>, acc: Map<Long, String>) {
        val sb = StringBuilder("""<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        (listOf(head) + rows(t, acc)).forEachIndexed { ri, r ->
            sb.append("<row>")
            r.forEachIndexed { ci, v ->
                if (ri > 0 && ci == 2) sb.append("<c><v>$v</v></c>") else sb.append("<c t=\"inlineStr\"><is><t>${esc(v)}</t></is></c>")
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        val ns = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        val parts = linkedMapOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$ns/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="$ns"><sheets><sheet name="Transactions" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$ns/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/worksheets/sheet1.xml" to sb.toString()
        )
        ZipOutputStream(c.contentResolver.openOutputStream(u) ?: return).use { z ->
            parts.forEach { (n, s) -> z.putNextEntry(ZipEntry(n)); z.write(s.toByteArray()); z.closeEntry() }
        }
    }

    fun pdf(c: Context, u: Uri, t: List<Tx>, acc: Map<Long, String>) {
        val doc = PdfDocument()
        val p = Paint().apply { textSize = 9f }
        val b = Paint().apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD }
        var page: PdfDocument.Page? = null
        var y = 0f
        var n = 0
        fun newPage() {
            page?.let { doc.finishPage(it) }
            n++
            page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, n).create())
            page!!.canvas.drawText("FinTrack transactions", 30f, 40f, b)
            y = 62f
        }
        newPage()
        for (x in t) {
            if (y > 810f) newPage()
            val cv = page!!.canvas
            val sign = if (x.type == "INCOME") "+" else "-"
            cv.drawText(fmtDate(x.ts), 30f, y, p)
            cv.drawText(x.merchant.take(24), 140f, y, p)
            cv.drawText(x.category, 285f, y, p)
            cv.drawText((acc[x.accountId] ?: "").take(12), 355f, y, p)
            cv.drawText("$sign${f2(x.amount)}", 450f, y, p)
            y += 14f
        }
        val inc = t.filter { it.type == "INCOME" }.sumOf { it.amount }
        val exp = t.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        if (y > 790f) newPage()
        page!!.canvas.drawText("Income RM ${f2(inc)}   Expenses RM ${f2(exp)}   Net RM ${f2(inc - exp)}", 30f, y + 14f, b)
        doc.finishPage(page!!)
        c.contentResolver.openOutputStream(u)?.use { doc.writeTo(it) }
        doc.close()
    }

    suspend fun backup(c: Context, u: Uri) = withContext(Dispatchers.IO) {
        val d = AppDb.get(c).dao()
        val json = Gson().toJson(Backup(1, d.txsNow(), d.accountsNow(), d.budgetsNow(), d.goalsNow(), d.recsNow(), d.rulesNow()))
        c.contentResolver.openOutputStream(u)?.use { it.write(json.toByteArray()) }
    }

    suspend fun restore(c: Context, u: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val s = c.contentResolver.openInputStream(u)?.bufferedReader()?.readText() ?: return@withContext false
            val b = Gson().fromJson(s, Backup::class.java) ?: return@withContext false
            val db = AppDb.get(c)
            db.clearAllTables()
            val d = db.dao()
            b.accounts.forEach { d.putAcc(it) }
            b.tx.forEach { d.putTx(it) }
            b.budgets.forEach { d.putBudget(it) }
            b.goals.forEach { d.putGoal(it) }
            b.recs.forEach { d.putRec(it) }
            b.rules.forEach { d.putRule(it) }
            true
        } catch (e: Exception) { false }
    }
}
