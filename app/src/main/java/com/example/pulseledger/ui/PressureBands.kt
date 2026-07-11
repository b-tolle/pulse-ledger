package com.example.pulseledger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.example.pulseledger.data.db.BpReading
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Time-scaled BP bands. X axis is real calendar time (gaps show as gaps).
 * Dashed stage thresholds for BOTH numbers — systolic S1/S2 at 130/140,
 * diastolic S1/S2 at 80/90 — so each dot is judged against its own line.
 * Dot colors use the same graded severity scale as the reading list.
 */
@Composable
fun PressureBandChart(readings: List<BpReading>, windowDays: Long? = null) {
    if (readings.size < 2) return
    val cutoff = windowDays?.let { System.currentTimeMillis() - it * 86_400_000L } ?: 0L
    val pts = readings.filter { it.epochMillis >= cutoff }
        .sortedBy { it.epochMillis }.takeLast(120)
    if (pts.size < 2) return
    val t0 = pts.first().epochMillis
    val t1 = pts.last().epochMillis.coerceAtLeast(t0 + 1)
    val appeared = remember { Animatable(0f) }
    LaunchedEffect(pts.size) { appeared.snapTo(0f); appeared.animateTo(1f, tween(700)) }

    Canvas(Modifier.fillMaxWidth().height(210.dp)) {
        val padL = 58f; val padR = 96f; val padB = 30f; val padT = 8f
        val lo = 55f; val hi = 165f
        val w = size.width; val h = size.height
        fun y(v: Float) = padT + (h - padB - padT) * (1f - (v - lo) / (hi - lo))
        fun x(t: Long) = padL + (w - padL - padR) * (t - t0).toFloat() / (t1 - t0).toFloat()

        val axis = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#5B6D8A"); textSize = 24f
        }

        // baseline gridlines
        listOf(60, 100, 120, 160).forEach { g ->
            val gy = y(g.toFloat())
            drawLine(PL.Line, Offset(padL, gy), Offset(w - padR, gy), 1f)
            drawContext.canvas.nativeCanvas.drawText("$g", 10f, gy + 8f, axis)
        }

        // stage thresholds for both numbers, dashed, labeled at right
        val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
        val orange = Color(0xFFFF8A5D); val red = Color(0xFFFF5D73)
        data class Thr(val v: Int, val label: String, val c: Color)
        listOf(
            Thr(140, "SYS·S2", red), Thr(130, "SYS·S1", orange),
            Thr(90, "DIA·S2", red), Thr(80, "DIA·S1", orange),
        ).forEach { t ->
            val ty = y(t.v.toFloat())
            drawLine(t.c.copy(alpha = 0.75f), Offset(padL, ty), Offset(w - padR, ty),
                strokeWidth = 2f, pathEffect = dash)
            drawContext.canvas.nativeCanvas.drawText(
                "${t.label} ${t.v}", w - padR + 8f, ty + 8f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(230,
                        (t.c.red * 255).toInt(), (t.c.green * 255).toInt(), (t.c.blue * 255).toInt())
                    textSize = 22f
                })
        }

        // bands + severity dots, positioned on real time
        val bw = ((w - padL - padR) / pts.size * 0.45f).coerceIn(3f, 14f)
        pts.forEachIndexed { i, r ->
            val grow = (appeared.value * pts.size - i).coerceIn(0f, 1f)
            if (grow <= 0f) return@forEachIndexed
            val cx = x(r.epochMillis)
            val sysY = y(r.systolic.toFloat()); val diaY = y(r.diastolic.toFloat())
            val midY = (sysY + diaY) / 2
            val topY = midY + (sysY - midY) * grow
            val botY = midY + (diaY - midY) * grow
            drawLine(Color(0xFF2A3A57), Offset(cx, topY), Offset(cx, botY),
                strokeWidth = bw, cap = StrokeCap.Round)
            if (grow > 0.9f) {
                val sev = bpSeverityColor(r.systolic, r.diastolic)
                drawCircle(sev, bw * 0.55f, Offset(cx, sysY))
                drawCircle(sev, bw * 0.55f, Offset(cx, diaY))
                val mapY = y(r.diastolic + (r.systolic - r.diastolic) / 3f)
                drawCircle(PL.Txt, 2.2f, Offset(cx, mapY))
            }
        }

        // real date labels: first / middle / last
        val fmt = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault())
        drawContext.canvas.nativeCanvas.drawText(fmt.format(Instant.ofEpochMilli(t0)), padL, h - 4f, axis)
        val mid = fmt.format(Instant.ofEpochMilli((t0 + t1) / 2))
        drawContext.canvas.nativeCanvas.drawText(mid, (padL + w - padR) / 2 - mid.length * 6f, h - 4f, axis)
        val last = fmt.format(Instant.ofEpochMilli(t1))
        drawContext.canvas.nativeCanvas.drawText(last, w - padR - last.length * 12f, h - 4f, axis)
    }
}
