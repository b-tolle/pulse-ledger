package com.example.pulseledger.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pulseledger.data.db.DailySummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

enum class Range(val label: String, val days: Long) {
    M3("3M", 90), Y1("1Y", 365), Y3("3Y", 1095), ALL("All", Long.MAX_VALUE)
}

private fun bucketed(summaries: List<DailySummary>, range: Range, pick: (DailySummary) -> Double?): List<Triple<Long, Double, DailySummary>> {
    val zone = ZoneId.systemDefault()
    // weekly buckets for short ranges, monthly for long — keeps charts readable
    val weekly = range == Range.M3 || range == Range.Y1
    val groups = LinkedHashMap<Long, MutableList<Pair<Double, DailySummary>>>()
    for (s in summaries) {
        val v = pick(s) ?: continue
        val d = Instant.ofEpochMilli(s.dayEpoch).atZone(zone).toLocalDate()
        val key = if (weekly) d.minusDays((d.dayOfWeek.value - 1).toLong()) else d.withDayOfMonth(1)
        val k = key.atStartOfDay(zone).toInstant().toEpochMilli()
        groups.getOrPut(k) { mutableListOf() }.add(v to s)
    }
    return groups.map { (k, list) ->
        val avg = list.map { it.first }.average()
        // representative day = the one closest to the bucket average (so taps land on a real day)
        val rep = list.minByOrNull { abs(it.first - avg) }!!.second
        Triple(k, avg, rep)
    }.sortedBy { it.first }
}

@Composable
fun FramedChart(
    title: String,
    summaries: List<DailySummary>,
    xMin: Long, xMax: Long,
    range: Range,
    color: Color,
    unit: String,
    fmtValue: (Double) -> String = { "%,.0f".format(it) },
    onTapDay: (DailySummary) -> Unit,
    pick: (DailySummary) -> Double?,
) {
    val points = bucketed(summaries, range, pick)
    if (points.size < 2) return
    val lo = points.minOf { it.second }
    val hi = points.maxOf { it.second }
    val latest = points.last().second
    val padL = 88f  // room for value labels

    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row {
            Text(title, color = PL.Soft, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp, modifier = Modifier.weight(1f))
            Text("${fmtValue(latest)} $unit".trim(), color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier.fillMaxWidth().height(84.dp).pointerInput(points, range) {
                detectTapGestures { tap ->
                    val w = size.width.toFloat()
                    fun px(t: Long) = padL + (w - padL - 8f) * (t - xMin).toFloat() / (xMax - xMin).toFloat()
                    val nearest = points.minByOrNull { abs(px(it.first) - tap.x) }
                    nearest?.let { onTapDay(it.third) }
                }
            }
        ) {
            val w = size.width; val h = size.height
            val plotBottom = h - 4f
            fun x(t: Long) = padL + (w - padL - 8f) * (t - xMin).toFloat() / (xMax - xMin).toFloat()
            fun y(v: Double) = 6f + (plotBottom - 6f) * (1f - ((v - lo) / (hi - lo + 1e-9)).toFloat())

            // gridlines + value labels at lo, mid, hi
            listOf(lo, (lo + hi) / 2, hi).forEach { gv ->
                val gy = y(gv)
                drawLine(PL.Line, Offset(padL, gy), Offset(w - 8f, gy), strokeWidth = 1.5f)
                drawContext.canvas.nativeCanvas.apply {
                    drawText(fmtValue(gv), 4f, gy + 8f, android.graphics.Paint().apply {
                        this.color = android.graphics.Color.parseColor("#8FA2BF")
                        textSize = 24f
                    })
                }
            }

            val path = Path()
            var prev: Long? = null
            val gapMs = if (range == Range.M3 || range == Range.Y1) 21L * 86_400_000L else 60L * 86_400_000L
            for ((t, v, _) in points) {
                if (prev == null || t - prev!! > gapMs) path.moveTo(x(t), y(v)) else path.lineTo(x(t), y(v))
                prev = t
            }
            drawPath(path, color, style = Stroke(width = 4f))
            drawCircle(color, radius = 6f, center = Offset(x(points.last().first), y(latest)))
        }
    }
}

@Composable
fun AxisLabels(xMin: Long, xMax: Long) {
    val fmt = DateTimeFormatter.ofPattern("MMM ''yy").withZone(ZoneId.systemDefault())
    Row(Modifier.padding(start = 88.dp)) {
        Text(fmt.format(Instant.ofEpochMilli(xMin)), color = PL.Dim, fontSize = 9.sp, modifier = Modifier.weight(1f))
        Text(fmt.format(Instant.ofEpochMilli((xMin + xMax) / 2)), color = PL.Dim, fontSize = 9.sp)
        Text(fmt.format(Instant.ofEpochMilli(xMax)), color = PL.Dim, fontSize = 9.sp,
            modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}
