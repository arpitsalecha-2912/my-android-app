package com.arpit.expense.model

import kotlinx.serialization.Serializable

@Serializable
data class Txn(
    val id: String,
    val amount: Double,          // always positive
    val type: String,            // "debit" (spent) or "credit" (received)
    val category: String,
    val merchant: String,
    val note: String = "",
    val timestamp: Long,
    val source: String,          // "sms" or "manual"
    val account: String? = null,
) {
    val isDebit get() = type == "debit"
}

object TxnType {
    const val DEBIT = "debit"
    const val CREDIT = "credit"
}
