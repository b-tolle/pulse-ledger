package com.example.pulseledger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pulseledger.data.db.DailySummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Monthly aggregation of one field across the whole imported history. */
private fun monthly(
    summaries: List<DailySummary>,
    pick: (DailySummary) -> Double?,
): List<Pair<Long, Double>> {
    val zone = ZoneId.systemDefault()
    val byMonth = LinkedHashMap<Long, MutableList<Double>>()
    for (s in summaries) {
        val v = pick(s) ?: continue
        val d = Instant.ofEpochMilli(s.dayEpoch).atZone(zone).toLocalDate()
        val key = d.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        byMonth.getOrPut(key) { mutableListOf() }.add(v)
    }
    return byMonth.map { (k, v) -> k to v.average() }.sortedBy { it.first }
}

@Composable
fun HistoryChart(
    title: String,
    summaries: List<DailySummary>,
    color: Color,
    unit: String,
    pick: (DailySummary) -> Double?,
) {
    val points = monthly(summaries, pick)
    if (points.size < 2) return
    val lo = points.minOf { it.second }
    val hi = points.maxOf { it.second }
    val latest = points.last().second
    val fmt = DateTimeFormatter.ofPattern("MMM yy").withZone(ZoneId.systemDefault())

    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row {
            Text(title, color = Color(0xFF8CA0BE), fontSize = 11.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
            Text("%,.0f %s".format(latest, unit), color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            val w = size.width; val h = size.height
            val n = points.size
            fun x(i: Int) = w * i / (n - 1)
            fun y(v: Double) = (h - 6f) * (1f - ((v - lo) / (hi - lo + 1e-9)).toFloat()) + 3f
            val path = Path()
            points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(0), y(p.second)) else path.lineTo(x(i), y(p.second)) }
            drawPath(path, color, style = Stroke(width = 4f))
            drawCircle(color, radius = 6f, center = Offset(w, y(latest)))
        }
        Row {
            Text(fmt.format(Instant.ofEpochMilli(points.first().first)), color = Color(0xFF5B6D8A), fontSize = 9.sp, modifier = Modifier.weight(1f))
            Text(fmt.format(Instant.ofEpochMilli(points.last().first)), color = Color(0xFF5B6D8A), fontSize = 9.sp)
        }
    }
}
