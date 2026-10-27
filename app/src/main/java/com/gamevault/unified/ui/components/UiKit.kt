package com.gamevault.unified.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

fun fmtDateTime(iso: String): String {
    if (iso.isBlank()) return "—"
    val t = iso.take(16).replace("T", " ")
    return t.ifBlank { "—" }
}

fun fmtDurationSecs(totalSeconds: Long): String {
    if (totalSeconds <= 0) return "Never played"
    val minutes = totalSeconds / 60
    return when {
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}

fun fmtBytes(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return "—"
    val gb = bytes.toDouble() / (1024 * 1024 * 1024)
    return if (gb >= 1) "%.1f GB".format(gb) else "%d MB".format(bytes / (1024 * 1024))
}

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))
    content()
    Spacer(Modifier.height(12.dp))
}

@Composable
fun InfoRow(label: String, value: String) {
    if (value.isBlank() || value == "—") return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(items: List<String>) {
    if (items.isEmpty()) return
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items.forEach { item ->
            AssistChip(onClick = {}, label = { Text(item) })
        }
    }
}

@Composable
fun LabeledChips(label: String, items: List<String>) {
    if (items.isEmpty()) return
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        ChipRow(items)
        Spacer(Modifier.height(8.dp))
    }
}
