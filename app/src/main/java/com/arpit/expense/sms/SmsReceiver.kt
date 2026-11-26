package com.arpit.expense.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.arpit.expense.data.SmsParser
import com.arpit.expense.data.Store

/** Catches new bank SMS as they arrive and logs any transaction found. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        try {
            val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
            val bodyBuf = StringBuilder()
            var sender = ""
            for (m in msgs) { bodyBuf.append(m.messageBody ?: ""); sender = m.originatingAddress ?: sender }
            val body = bodyBuf.toString()
            if (!SmsParser.looksFinancial(body)) return
            val txn = SmsParser.toTxn(body, System.currentTimeMillis(), sender) ?: return
            Store.get(context).add(txn)
        } catch (_: Exception) { }
    }
}
