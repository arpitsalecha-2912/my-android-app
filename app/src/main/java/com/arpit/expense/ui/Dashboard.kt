package com.arpit.expense.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arpit.expense.MainViewModel
import com.arpit.expense.data.Money
import com.arpit.expense.model.Txn

@Composable
fun DashboardScreen(vm: MainViewModel, hasSms: Boolean, onGrant: () -> Unit, onRescan: () -> Unit) {
    val all by vm.txns.collectAsState()
    val month = vm.viewMonth
    val s = vm.summaryFor(month, all)
    val recent = all.filter {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = it.timestamp }
        c.get(java.util.Calendar.MONTH) == month.get(java.util.Calendar.MONTH) &&
            c.get(java.util.Calendar.YEAR) == month.get(java.util.Calendar.YEAR)
    }.take(8)

    LazyColumn(
        Modifier.widthIn(max = 640.dp).fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.shiftMonth(-1) }) { Icon(Icons.Filled.ChevronLeft, "Previous month") }
                Text(
                    Money.monthLabel(month),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { vm.shiftMonth(1) }) { Icon(Icons.Filled.ChevronRight, "Next month") }
            }
        }

        if (!hasSms) {
            item { SmsPrompt(onGrant) }
        }

        item { BalanceCard(s.spent, s.received) }

        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Spending by category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (hasSms) {
                        if (vm.importing) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else FilledTonalButton(onClick = onRescan, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                            Icon(Icons.Filled.Sms, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Sync")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (s.slices.isEmpty()) {
                    Text("No spending recorded this month yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                } else {
                    s.slices.forEach { sl ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                            CategoryDot(sl.icon, sl.color, 38)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(sl.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(Money.rupees(sl.amount), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                }
                                Spacer(Modifier.height(5.dp))
                                ShareBar(sl.pct, sl.color)
                            }
                        }
                    }
                }
                vm.lastImportMsg?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        item {
            Text("Recent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        }
        if (recent.isEmpty()) {
            item { Text("Nothing here yet. Tap + to add, or Sync your SMS.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp)) }
        } else {
            items(recent, key = { it.id }) { TxnRow(it) }
        }
    }
}

@Composable
private fun BalanceCard(spent: Double, received: Double) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("Spent this month", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
            Text(Money.rupees(spent), fontSize = 38.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(14.dp))
            Row {
                MiniStat("Received", Money.rupees(received), Modifier.weight(1f))
                MiniStat("Net", (if (received - spent >= 0) "+" else "-") + Money.rupees(received - spent), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
        Text(value, color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SmsPrompt(onGrant: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Track automatically", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(4.dp))
            Text(
                "Let the app read your bank SMS to log every debit and credit for you. Messages are read only on this phone — nothing is uploaded.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onGrant) { Text("Turn on SMS tracking") }
        }
    }
}

@Composable
fun TxnRow(t: Txn) {
    val cat = com.arpit.expense.data.Categories.of(t.category)
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryDot(cat.icon, cat.color, 42)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.merchant, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${t.category} · ${Money.dayLabel(t.timestamp)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                (if (t.isDebit) "-" else "+") + Money.rupees(t.amount),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = if (t.isDebit) MaterialTheme.colorScheme.onSurface else Color(0xFF16A34A),
            )
        }
    }
}
