package com.example.fintrack.ai

import android.content.Context
import com.example.fintrack.data.Cats
import com.example.fintrack.data.AppDao
import com.example.fintrack.data.Rule
import com.example.fintrack.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Cat(val name: String, val conf: Float)

/** Swap AI providers by implementing this interface. */
interface Categorizer {
    suspend fun categorize(merchant: String, amount: Double): Cat?
}

fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

suspend fun learn(d: AppDao, merchant: String, cat: String) {
    val n = norm(merchant)
    if (n.length >= 2) d.putRule(Rule(n, cat))
}

suspend fun learnTx(d: AppDao, t: com.example.fintrack.data.Tx, cat: String) {
    if (t.merchant.isNotBlank() && !t.merchant.equals(t.app, true)) learn(d, t.merchant, cat)
}

class LocalCategorizer(private val d: AppDao) : Categorizer {
    private val kw = linkedMapOf(
        "Food" to listOf("kfc", "mcdonald", "starbucks", "restoran", "restaurant", "cafe", "kopi", "mamak", "nasi", "grabfood", "foodpanda", "shopeefood", "pizza", "domino", "subway", "texas chicken", "secret recipe", "zus", "tealive", "gong cha", "chagee", "mydin", "tesco", "lotus", "aeon big", "giant", "jaya grocer", "village grocer", "99 speedmart", "bakery", "burger", "sushi", "warung", "kedai makan", "bubble tea"),
        "Health" to listOf("pharmacy", "farmasi", "watsons", "guardian", "klinik", "clinic", "hospital", "dental", "caring", "kpj", "pantai", "medic"),
        "Education" to listOf("universiti", "university", "sekolah", "school", "tuition", "kolej", "college", "udemy", "coursera", "yuran", "tadika"),
        "Entertainment" to listOf("netflix", "spotify", "disney", "tgv", "gsc", "cinema", "steam", "youtube", "playstation", "hbo", "viu", "karaoke", "bowling"),
        "Bills" to listOf("tnb", "tenaga", "syabas", "air selangor", "indah water", "unifi", "maxis", "celcom", "digi", "u mobile", "astro", "cukai", "insurance", "takaful", "great eastern", "prudential", "allianz", "jompay", "telekom", "sewa"),
        "Shopping" to listOf("shopee", "lazada", "zalora", "uniqlo", "h m", "ikea", "mr diy", "popular", "tiktok shop", "decathlon", "miniso", "padini", "amazon"),
        "Transport" to listOf("grab", "petronas", "shell", "petron", "caltex", "bhp", "touch n go", "toll", "rapidkl", "mrt", "lrt", "ktm", "parking", "mybas", "maxim", "indrive", "airasia", "ktmb", "taxi")
    )

    override suspend fun categorize(merchant: String, amount: Double): Cat? {
        val n = norm(merchant)
        if (n.isBlank()) return null
        d.rulesNow().firstOrNull { it.merchant == n || (it.merchant.length >= 4 && (n.contains(it.merchant) || it.merchant.contains(n))) }
            ?.let { return Cat(it.category, 0.97f) }
        for ((c, words) in kw) if (words.any { w -> Regex("\\b" + Regex.escape(w)).containsMatchIn(n) }) return Cat(c, 0.92f)
        return null
    }
}

class GeminiClient(private val key: String, private val model: String) {
    suspend fun ask(prompt: String): String? = withContext(Dispatchers.IO) {
        try {
            val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.connectTimeout = 8000; c.readTimeout = 20000; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("x-goog-api-key", key)
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("temperature", 0))
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode != 200) return@withContext null
            JSONObject(c.inputStream.bufferedReader().readText())
                .getJSONArray("candidates").getJSONObject(0).getJSONObject("content")
                .getJSONArray("parts").getJSONObject(0).getString("text")
        } catch (e: Exception) { null }
    }
}

class GeminiCategorizer(private val g: GeminiClient) : Categorizer {
    override suspend fun categorize(merchant: String, amount: Double): Cat? {
        val out = g.ask(
            "Categorize this Malaysian purchase into exactly one of: ${Cats.all.joinToString()}. " +
                "Merchant: \"$merchant\", amount RM$amount. Reply JSON: {\"category\":string,\"confidence\":number between 0 and 1}. Use low confidence if unsure."
        ) ?: return null
        return try {
            val j = JSONObject(out.trim().removePrefix("```json").removeSuffix("```").trim())
            val c = Cats.all.firstOrNull { it.equals(j.getString("category"), true) } ?: return null
            Cat(c, j.optDouble("confidence", 0.0).toFloat().coerceIn(0f, 1f))
        } catch (e: Exception) { null }
    }
}

class HybridCategorizer(ctx: Context, d: AppDao) : Categorizer {
    private val prefs = Prefs(ctx)
    private val local = LocalCategorizer(d)
    override suspend fun categorize(merchant: String, amount: Double): Cat? {
        val l = local.categorize(merchant, amount)
        if (l != null && l.conf >= 0.85f) return l
        val ai = if (prefs.geminiKey.isNotBlank() && merchant.isNotBlank())
            GeminiCategorizer(GeminiClient(prefs.geminiKey, prefs.geminiModel)).categorize(merchant, amount) else null
        return ai ?: l ?: Cat("Others", 0f)
    }
}
