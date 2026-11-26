package com.arpit.expense.data

import android.content.Context
import com.arpit.expense.model.Txn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/** Single on-device store of all transactions, saved as a JSON file in app storage. */
class Store(context: Context) {

    private val file = File(context.filesDir, "transactions.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _txns = MutableStateFlow<List<Txn>>(emptyList())
    val txns: StateFlow<List<Txn>> = _txns.asStateFlow()

    private val ids = HashSet<String>()

    init { load() }

    private fun load() {
        try {
            if (file.exists()) {
                val list = json.decodeFromString<List<Txn>>(file.readText())
                _txns.value = list.sortedByDescending { it.timestamp }
                ids.addAll(list.map { it.id })
            }
        } catch (_: Exception) { }
    }

    private fun persist() {
        try { file.writeText(json.encodeToString(_txns.value)) } catch (_: Exception) { }
    }

    fun has(id: String) = ids.contains(id)

    @Synchronized
    fun add(txn: Txn): Boolean {
        if (ids.contains(txn.id)) return false
        ids.add(txn.id)
        _txns.value = (_txns.value + txn).sortedByDescending { it.timestamp }
        persist()
        return true
    }

    /** Add many at once (used by the SMS importer). Returns how many were new. */
    @Synchronized
    fun addAll(list: List<Txn>): Int {
        var n = 0
        val merged = _txns.value.toMutableList()
        for (t in list) if (!ids.contains(t.id)) { ids.add(t.id); merged.add(t); n++ }
        if (n > 0) {
            _txns.value = merged.sortedByDescending { it.timestamp }
            persist()
        }
        return n
    }

    @Synchronized
    fun update(txn: Txn) {
        _txns.value = _txns.value.map { if (it.id == txn.id) txn else it }.sortedByDescending { it.timestamp }
        persist()
    }

    @Synchronized
    fun delete(id: String) {
        ids.remove(id)
        _txns.value = _txns.value.filter { it.id != id }
        persist()
    }

    companion object {
        @Volatile private var INSTANCE: Store? = null
        fun get(context: Context): Store =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Store(context.applicationContext).also { INSTANCE = it }
            }
    }
}
