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

private fun monthly(summaries: List<DailySummary>, pick: (DailySummary) -> Double?): List<Pair<Long, Double>> {
    val zone = ZoneId.systemDefault()
    val byMonth = LinkedHashMap<Long, MutableList<Double>>()
    for (s in summaries) {
        val v = pick(s) ?: continue
        val d = Instant.ofEpochMilli(s.dayEpoch).atZone(zone).toLocalDate()
        byMonth.getOrPut(d.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()) { mutableListOf() }.add(v)
    }
    return byMonth.map { (k, v) -> k to v.average() }.sortedBy { it.first }
}

/**
 * One chart on a SHARED time axis: every chart in the stack spans the same
 * xMin..xMax, so 2019 is at the same horizontal position everywhere.
 * Gaps (no data for >45 days, e.g. the watchless years) are drawn as breaks.
 */
@Composable
fun HistoryChart(
    title: String,
    summaries: List<DailySummary>,
    xMin: Long, xMax: Long,
    color: Color,
    unit: String,
    pick: (DailySummary) -> Double?,
) {
    val points = monthly(summaries, pick)
    if (points.size < 2) return
    val lo = points.minOf { it.second }
    val hi = points.maxOf { it.second }
    val latest = points.last().second

    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row {
            Text(title, color = PL.Soft, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
            Text("%,.0f %s".format(latest, unit).trim(), color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            val w = size.width; val h = size.height
            fun x(t: Long) = w * (t - xMin).toFloat() / (xMax - xMin).toFloat()
            fun y(v: Double) = (h - 6f) * (1f - ((v - lo) / (hi - lo + 1e-9)).toFloat()) + 3f
            val path = Path()
            var prev: Long? = null
            for ((t, v) in points) {
                if (prev == null || t - prev!! > 45L * 86_400_000L) path.moveTo(x(t), y(v))
                else path.lineTo(x(t), y(v))
                prev = t
            }
            drawPath(path, color, style = Stroke(width = 4f))
            drawCircle(color, radius = 6f, center = Offset(x(points.last().first), y(latest)))
        }
    }
}

@Composable
fun SharedAxisLabels(xMin: Long, xMax: Long) {
    val fmt = DateTimeFormatter.ofPattern("MMM yyyy").withZone(ZoneId.systemDefault())
    Row {
        Text(fmt.format(Instant.ofEpochMilli(xMin)), color = PL.Dim, fontSize = 9.sp, modifier = Modifier.weight(1f))
        Text(fmt.format(Instant.ofEpochMilli((xMin + xMax) / 2)), color = PL.Dim, fontSize = 9.sp)
        Text(fmt.format(Instant.ofEpochMilli(xMax)), color = PL.Dim, fontSize = 9.sp,
            modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}
