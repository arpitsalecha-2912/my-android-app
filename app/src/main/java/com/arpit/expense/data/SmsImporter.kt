package com.arpit.expense.data

import android.content.Context
import android.provider.Telephony

/** Reads the SMS inbox once and turns bank messages into transactions. */
object SmsImporter {

    /** Returns the number of new transactions imported. Call on an IO thread. */
    fun importInbox(context: Context, store: Store, sinceDays: Int = 180): Int {
        val found = ArrayList<com.arpit.expense.model.Txn>()
        val cutoff = System.currentTimeMillis() - sinceDays * 24L * 60 * 60 * 1000
        val cols = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI, cols,
                "${Telephony.Sms.DATE} >= ?", arrayOf(cutoff.toString()),
                "${Telephony.Sms.DATE} DESC"
            )?.use { c ->
                val iA = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val iB = c.getColumnIndex(Telephony.Sms.BODY)
                val iD = c.getColumnIndex(Telephony.Sms.DATE)
                while (c.moveToNext()) {
                    val body = c.getString(iB) ?: continue
                    if (!SmsParser.looksFinancial(body)) continue
                    val sender = c.getString(iA) ?: ""
                    val date = c.getLong(iD)
                    SmsParser.toTxn(body, date, sender)?.let { found.add(it) }
                }
            }
        } catch (_: Exception) { }
        return store.addAll(found)
    }
}
