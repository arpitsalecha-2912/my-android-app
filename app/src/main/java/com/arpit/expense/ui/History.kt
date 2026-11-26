package com.arpit.expense.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.arpit.expense.MainViewModel
import com.arpit.expense.data.Categories
import com.arpit.expense.data.Money
import com.arpit.expense.model.Txn

@Composable
fun HistoryScreen(vm: MainViewModel) {
    val all by vm.txns.collectAsState()
    var filter by remember { mutableStateOf("All") }
    var selected by remember { mutableStateOf<Txn?>(null) }

    val filters = listOf("All", "Spent", "Received")
    val list = when (filter) {
        "Spent" -> all.filter { it.isDebit }
        "Received" -> all.filter { !it.isDebit }
        else -> all
    }

    Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filters.forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) })
            }
        }
        if (list.isEmpty()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("No transactions yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("Add one with + or turn on SMS tracking on the Home tab.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.id }) { t ->
                    Row(Modifier.clickable { selected = t }) { TxnRow(t) }
                }
            }
        }
    }

    selected?.let { t -> TxnSheet(t, onDismiss = { selected = null },
        onDelete = { vm.delete(t.id); selected = null },
        onRecategorize = { c -> vm.recategorize(t, c); selected = null }) }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TxnSheet(t: Txn, onDismiss: () -> Unit, onDelete: () -> Unit, onRecategorize: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.merchant) },
        text = {
            Column {
                Labeled("Amount", (if (t.isDebit) "-" else "+") + Money.rupees2(t.amount))
                Labeled("Type", if (t.isDebit) "Spent" else "Received")
                Labeled("When", Money.dateOnly(t.timestamp))
                Labeled("Source", if (t.source == "sms") "From SMS" else "Added manually")
                t.account?.let { Labeled("Account", "xx$it") }
                Spacer(Modifier.height(10.dp))
                Text("Change category", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Categories.names.forEach { c ->
                        FilterChip(selected = t.category == c, onClick = { onRecategorize(c) }, label = { Text(c, style = MaterialTheme.typography.labelMedium) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, null, modifier = Modifier.height(18.dp)); Text("Delete")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
