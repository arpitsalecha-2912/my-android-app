package com.arpit.expense

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arpit.expense.data.SmsImporter
import com.arpit.expense.data.Store
import com.arpit.expense.model.Txn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

data class CatSlice(val name: String, val amount: Double, val color: Long, val icon: String, val pct: Float)

data class MonthSummary(
    val spent: Double = 0.0,
    val received: Double = 0.0,
    val count: Int = 0,
    val slices: List<CatSlice> = emptyList(),
    val topMerchants: List<Pair<String, Double>> = emptyList(),
    val dailySpend: List<Float> = emptyList(),   // per day of month
    val daysInMonth: Int = 30,
)

data class MonthBar(val label: String, val spent: Double, val received: Double)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store.get(app)
    val txns: StateFlow<List<Txn>> = store.txns

    var importing by mutableStateOf(false)
        private set
    var lastImportMsg by mutableStateOf<String?>(null)
        private set

    // The month the dashboard is viewing
    var viewMonth by mutableStateOf(Calendar.getInstance())
        private set

    fun shiftMonth(delta: Int) {
        val c = viewMonth.clone() as Calendar
        c.add(Calendar.MONTH, delta)
        viewMonth = c
    }

    fun importSms() {
        if (importing) return
        importing = true
        lastImportMsg = null
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) { SmsImporter.importInbox(getApplication(), store) }
            lastImportMsg = if (n > 0) "Imported $n new transactions from SMS" else "No new transactions found in SMS"
            importing = false
        }
    }

    fun addManual(amount: Double, type: String, category: String, merchant: String, note: String, timestamp: Long) {
        store.add(
            Txn(
                id = "man_" + System.nanoTime().toString(),
                amount = amount, type = type, category = category,
                merchant = merchant.ifBlank { category }, note = note,
                timestamp = timestamp, source = "manual",
            )
        )
    }

    fun delete(id: String) = store.delete(id)
    fun recategorize(txn: Txn, cat: String) = store.update(txn.copy(category = cat))

    // ---------- derived summaries ----------

    private fun inMonth(t: Txn, cal: Calendar): Boolean {
        val c = Calendar.getInstance().apply { timeInMillis = t.timestamp }
        return c.get(Calendar.YEAR) == cal.get(Calendar.YEAR) && c.get(Calendar.MONTH) == cal.get(Calendar.MONTH)
    }

    fun summaryFor(cal: Calendar, all: List<Txn>): MonthSummary {
        val month = all.filter { inMonth(it, cal) }
        val spent = month.filter { it.isDebit }.sumOf { it.amount }
        val received = month.filter { !it.isDebit }.sumOf { it.amount }

        val byCat = month.filter { it.isDebit }
            .groupBy { it.category }
            .map { (c, list) -> c to list.sumOf { it.amount } }
            .sortedByDescending { it.second }
        val slices = byCat.map { (c, amt) ->
            val cat = com.arpit.expense.data.Categories.of(c)
            CatSlice(c, amt, cat.color, cat.icon, if (spent > 0) (amt / spent).toFloat() else 0f)
        }

        val topM = month.filter { it.isDebit }
            .groupBy { it.merchant }
            .map { (m, l) -> m to l.sumOf { it.amount } }
            .sortedByDescending { it.second }
            .take(5)

        val dim = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val daily = FloatArray(dim)
        for (t in month.filter { it.isDebit }) {
            val d = Calendar.getInstance().apply { timeInMillis = t.timestamp }.get(Calendar.DAY_OF_MONTH) - 1
            if (d in 0 until dim) daily[d] += t.amount.toFloat()
        }

        return MonthSummary(spent, received, month.size, slices, topM, daily.toList(), dim)
    }

    fun last6Months(all: List<Txn>): List<MonthBar> {
        val out = ArrayList<MonthBar>()
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_MONTH, 1)
        for (i in 5 downTo 0) {
            val m = c.clone() as Calendar
            m.add(Calendar.MONTH, -i)
            val month = all.filter { inMonth(it, m) }
            out.add(
                MonthBar(
                    com.arpit.expense.data.Money.shortMonth(m),
                    month.filter { it.isDebit }.sumOf { it.amount },
                    month.filter { !it.isDebit }.sumOf { it.amount },
                )
            )
        }
        return out
    }
}
