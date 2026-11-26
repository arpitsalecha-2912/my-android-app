package com.arpit.expense.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arpit.expense.MainViewModel
import com.arpit.expense.data.Money

@Composable
fun ReportsScreen(vm: MainViewModel) {
    val all by vm.txns.collectAsState()
    val months = vm.last6Months(all)
    val s = vm.summaryFor(vm.viewMonth, all)
    val maxSpent = (months.maxOfOrNull { it.spent } ?: 1.0).coerceAtLeast(1.0)
    val avg = months.map { it.spent }.filter { it > 0 }.let { if (it.isEmpty()) 0.0 else it.average() }
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.surfaceVariant

    LazyColumn(
        Modifier.widthIn(max = 640.dp).fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Reports", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("6-month average", Money.rupees(avg), Modifier.weight(1f))
                StatTile("This month", Money.rupees(s.spent), Modifier.weight(1f))
            }
        }

        item {
            SectionCard {
                Text("Last 6 months", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                Canvas(Modifier.fillMaxWidth().height(170.dp)) {
                    val n = months.size
                    val gap = 14.dp.toPx()
                    val bw = (size.width - gap * (n - 1)) / n
                    val maxH = size.height - 24.dp.toPx()
                    months.forEachIndexed { i, m ->
                        val h = (m.spent / maxSpent * maxH).toFloat()
                        val x = i * (bw + gap)
                        drawRect(color = grid, topLeft = Offset(x, size.height - maxH - 0f), size = Size(bw, maxH), alpha = 0.3f)
                        drawRect(color = primary, topLeft = Offset(x, size.height - 24.dp.toPx() - h), size = Size(bw, h))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    months.forEach { m ->
                        Text(m.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }

        item {
            SectionCard {
                Text("Where it goes this month", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(14.dp))
                if (s.slices.isEmpty()) {
                    Text("No spending this month.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DonutChart(s.slices.map { it.amount.toFloat() to Color(it.color) }, Modifier.size(130.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            s.slices.take(6).forEach { sl ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).let { it }) {
                                        Canvas(Modifier.fillMaxSize()) { drawRect(Color(sl.color)) }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(sl.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${(sl.pct * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionCard {
                Text("Top places this month", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                if (s.topMerchants.isEmpty()) {
                    Text("Nothing yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    s.topMerchants.forEach { (m, amt) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Text(m, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                            Text(Money.rupees(amt), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    SectionCard(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DonutChart(parts: List<Pair<Float, Color>>, modifier: Modifier) {
    val total = parts.sumOf { it.first.toDouble() }.toFloat().coerceAtLeast(0.0001f)
    Canvas(modifier) {
        var start = -90f
        val stroke = size.minDimension * 0.22f
        val inset = stroke / 2
        parts.forEach { (v, c) ->
            val sweep = v / total * 360f
            drawArc(
                color = c, startAngle = start, sweepAngle = sweep - 1.5f, useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
            )
            start += sweep
        }
    }
}
