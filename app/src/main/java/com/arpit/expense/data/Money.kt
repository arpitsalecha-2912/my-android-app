package com.arpit.expense.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Indian-rupee formatting and date helpers. */
object Money {
    private val inr = Locale("en", "IN")

    fun rupees(v: Double): String {
        val n = String.format(inr, "%,.0f", kotlin.math.abs(v))
        return "₹$n"
    }

    fun rupees2(v: Double): String {
        val n = String.format(inr, "%,.2f", kotlin.math.abs(v))
        return "₹$n"
    }

    fun monthLabel(cal: Calendar): String =
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)

    fun shortMonth(cal: Calendar): String =
        SimpleDateFormat("MMM", Locale.getDefault()).format(cal.time)

    fun dayLabel(ts: Long): String =
        SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(ts))

    fun dateOnly(ts: Long): String =
        SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date(ts))
}
