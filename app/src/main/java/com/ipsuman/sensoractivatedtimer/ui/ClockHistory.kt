package com.ipsuman.sensoractivatedtimer.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class ClockHistoryEntry(val timestamp: Long, val kind: String, val reading: String) {
    fun dateLabel(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
    fun timeLabel(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

internal fun loadClockHistory(context: Context): List<ClockHistoryEntry> {
    val raw = context.getSharedPreferences("clock_history", Context.MODE_PRIVATE).getString("entries", "[]") ?: "[]"
    return try {
        val json = JSONArray(raw)
        (0 until json.length()).mapNotNull { i ->
            val item = json.optJSONObject(i) ?: return@mapNotNull null
            ClockHistoryEntry(item.optLong("timestamp"), item.optString("kind"), item.optString("reading"))
        }.filter { it.timestamp > 0L && it.kind.isNotBlank() && it.reading.isNotBlank() }.sortedByDescending { it.timestamp }
    } catch (_: Exception) { emptyList() }
}

internal fun saveClockHistory(context: Context, entries: List<ClockHistoryEntry>) {
    val json = JSONArray()
    entries.take(200).forEach { entry ->
        json.put(JSONObject().put("timestamp", entry.timestamp).put("kind", entry.kind).put("reading", entry.reading))
    }
    context.getSharedPreferences("clock_history", Context.MODE_PRIVATE).edit().putString("entries", json.toString()).apply()
}

@Composable
internal fun ClockHistoryPanel(entries: List<ClockHistoryEntry>, onDeleteDate: (String) -> Unit) {
    if (entries.isEmpty()) return
    Spacer(Modifier.height(18.dp))
    Text("HISTORY", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    entries.groupBy { it.dateLabel() }.forEach { (date, dayEntries) ->
        Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(date, fontWeight = FontWeight.Bold)
                    Button(onClick = { onDeleteDate(date) }) { Text("Delete day") }
                }
                dayEntries.forEach { entry ->
                    Text("${entry.timeLabel()}  •  ${entry.reading}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
