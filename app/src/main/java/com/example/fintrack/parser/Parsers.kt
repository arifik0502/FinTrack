package com.example.fintrack.parser

data class Parsed(val amount: Double, val merchant: String, val type: String, val ts: Long, val app: String)

/** Add new parsers to [Parsers.list] (before GenericParser) to support app-specific formats. */
interface NotifParser {
    fun parse(app: String, title: String, text: String, ts: Long): Parsed?
}

object Parsers {
    // Best-effort package -> label. GenericParser works for any app regardless of this map.
    val appNames = mapOf(
        "my.com.tngdigital.ewallet" to "TNG eWallet",
        "com.grabtaxi.passenger" to "Grab",
        "com.shopee.my" to "ShopeePay",
        "my.com.myboost" to "Boost",
        "com.maybank2u.life" to "MAE"
    )
    val list = mutableListOf<NotifParser>(GenericParser)
    fun parse(app: String, title: String, text: String, ts: Long): Parsed? =
        list.firstNotNullOfOrNull { it.parse(app, title, text, ts) }
}

object GenericParser : NotifParser {
    private val amt = Regex("""(?i)(?:RM|MYR)\s?(\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)""")
    private val deny = Regex("""(?i)\b(otp|tac|one[- ]time|promo|voucher|win|discount|coupon|get rm|earn rm|limited time|apply now|loan|offer|reminder)\b|% ?off""")
    private val allow = Regex("""(?i)\b(paid|payment|purchase|spent|debit(?:ed)?|credit(?:ed)?|transfer(?:red)?|sent|received|top[- ]?up|reload|withdraw(?:al)?|transaction|txn|successful|refund|duitnow|dibayar|bayaran|pembayaran|diterima)\b""")
    private val income = Regex("""(?i)\b(received|credited|refund|salary|money in|diterima)\b""")
    private val merchantRe = Regex(
        """(?i)\b(?:paid to|payment to|payment at|purchase at|spent at|transfer(?:red)? to|sent to|to|at|from|@)\s+([A-Za-z0-9][A-Za-z0-9&'.\-/ ]{1,40}?)(?=\s+(?:on|for|via|using|with|has|was|is|at|ref|reference|\d)|\s*[.,;(]|\s*(?:RM|MYR)|$)"""
    )
    private val badMerchant = Regex("""(?i)^(your|you|account|a/c|the|my|this|us|me)\b""")

    override fun parse(app: String, title: String, text: String, ts: Long): Parsed? {
        val body = "$title. $text"
        if (deny.containsMatchIn(body) || !allow.containsMatchIn(body)) return null
        val amount = amt.findAll(body).firstOrNull { m ->
            val pre = body.substring(maxOf(0, m.range.first - 25), m.range.first).lowercase()
            !(pre.contains("balance") || pre.contains("bal:") || pre.contains("available"))
        }?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: return null
        if (amount <= 0) return null
        val type = if (income.containsMatchIn(body)) "INCOME" else "EXPENSE"
        val merchant = merchantRe.findAll(body).map { it.groupValues[1].trim().trimEnd('.', ',', '-') }
            .firstOrNull { it.length >= 2 && !badMerchant.containsMatchIn(it) }
            ?: title.takeIf { it.length in 2..30 && !allow.containsMatchIn(it) }
            ?: app
        return Parsed(amount, merchant, type, ts, app)
    }
}
