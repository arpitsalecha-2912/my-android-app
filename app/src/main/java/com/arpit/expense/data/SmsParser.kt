package com.arpit.expense.data

import com.arpit.expense.model.Txn
import com.arpit.expense.model.TxnType
import java.security.MessageDigest

/** Turns a bank/transaction SMS into a Txn. Returns null for anything that isn't a real debit/credit. */
object SmsParser {

    private val AMOUNT = Regex(
        """(?:rs|inr|₹|mrp)\.?\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    private val DEBIT_WORDS = listOf("debited", "debit", "spent", "paid", "withdrawn", "purchase", "deducted", "sent", "charged")
    private val CREDIT_WORDS = listOf("credited", "credit", "received", "deposited", "refund", "added")
    private val SKIP_WORDS = listOf(
        "otp", "o.t.p", "one time password", "will be debited", "requested", "request", "failed",
        "declined", "balance is", "avbl bal", "available balance", "min bal", "e-mandate",
        "due", "overdue", "statement", "offer", "cashback of", "earn ", "win ", "reward points",
        "has been generated", "auto pay", "autopay scheduled",
    )
    private val ACCOUNT = Regex("""(?:a/c|ac|acct|account|card)\s*(?:no\.?)?\s*[xX*]+\s*([0-9]{3,6})""", RegexOption.IGNORE_CASE)
    private val MERCHANT_AT = Regex("""(?:at|to|towards|VPA|for)\s+([A-Za-z0-9&._@\- ]{3,40})""", RegexOption.IGNORE_CASE)

    data class Parsed(
        val amount: Double,
        val type: String,
        val merchant: String,
        val account: String?,
    )

    fun looksFinancial(body: String): Boolean {
        val b = body.lowercase()
        if (!AMOUNT.containsMatchIn(body)) return false
        val hasVerb = DEBIT_WORDS.any { b.contains(it) } || CREDIT_WORDS.any { b.contains(it) }
        return hasVerb
    }

    fun parse(body: String): Parsed? {
        val b = body.lowercase()
        if (SKIP_WORDS.any { b.contains(it) }) return null

        val m = AMOUNT.find(body) ?: return null
        val amount = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0) return null

        val isDebit = DEBIT_WORDS.any { b.contains(it) }
        val isCredit = CREDIT_WORDS.any { b.contains(it) }
        val type = when {
            isDebit && !isCredit -> TxnType.DEBIT
            isCredit && !isDebit -> TxnType.CREDIT
            isDebit -> TxnType.DEBIT
            isCredit -> TxnType.CREDIT
            else -> return null
        }

        val account = ACCOUNT.find(body)?.groupValues?.get(1)
        val merchant = extractMerchant(body) ?: (if (type == TxnType.CREDIT) "Received" else "Payment")

        return Parsed(amount, type, merchant, account)
    }

    private fun extractMerchant(body: String): String? {
        val raw = MERCHANT_AT.find(body)?.groupValues?.get(1)?.trim() ?: return null
        // Trim trailing noise like "on 12-11-26", "Ref ...", "UPI"
        var s = raw
        val cut = Regex("""\s+(on|ref|upi|txn|dated|info|via)\b.*$""", RegexOption.IGNORE_CASE)
        s = cut.replace(s, "").trim()
        s = s.trim('.', '-', ' ', '/')
        if (s.length < 2) return null
        // Title-case a shouty merchant
        return s.split(" ").filter { it.isNotBlank() }.joinToString(" ") { w ->
            if (w.length > 2 && w == w.uppercase()) w.lowercase().replaceFirstChar { it.uppercase() } else w
        }.take(40)
    }

    fun toTxn(body: String, timestampMs: Long, sender: String): Txn? {
        val p = parse(body) ?: return null
        val category = Categories.guess("${p.merchant} $body", p.type == TxnType.CREDIT)
        return Txn(
            id = idFor(sender, body, timestampMs),
            amount = p.amount,
            type = p.type,
            category = category,
            merchant = p.merchant,
            timestamp = timestampMs,
            source = "sms",
            account = p.account,
        )
    }

    fun idFor(sender: String, body: String, ts: Long): String {
        val md = MessageDigest.getInstance("MD5")
        val d = md.digest("$sender|$body|$ts".toByteArray())
        return "sms_" + d.joinToString("") { "%02x".format(it) }.take(16)
    }
}
