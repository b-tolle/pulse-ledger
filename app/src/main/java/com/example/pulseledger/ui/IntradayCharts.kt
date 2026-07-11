package com.example.pulseledger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.example.pulseledger.data.HealthConnectManager
import java.time.LocalDate
import java.time.ZoneId

private fun axisPaint() = android.graphics.Paint().apply {
    color = android.graphics.Color.parseColor("#5B6D8A"); textSize = 24f
}

/** The pretty 24-hour HR curve: gradient area, gaps preserved, midnight→now. */
@Composable
fun IntradayHrChart(samples: List<Pair<Long, Long>>, heightDp: Int = 170) {
    if (samples.size < 2) return
    val zone = ZoneId.systemDefault()
    val dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
    val nowMs = System.currentTimeMillis()
    val pts = samples.filter { it.second >= dayStart }
    if (pts.size < 2) return
    val lo = pts.minOf { it.first }.coerceAtMost(50)
    val hi = pts.maxOf { it.first }.coerceAtLeast(100)

    Canvas(Modifier.fillMaxWidth().height(heightDp.dp)) {
        val padL = 64f; val padB = 26f; val padT = 8f
        val w = size.width; val h = size.height
        fun x(t: Long) = padL + (w - padL - 8f) * (t - dayStart).toFloat() / (nowMs - dayStart).toFloat()
        fun y(v: Long) = padT + (h - padB - padT) * (1f - (v - lo).toFloat() / (hi - lo).toFloat())

        // gridlines at lo / mid / hi
        listOf(lo, (lo + hi) / 2, hi).forEach { g ->
            val gy = y(g)
            drawLine(PL.Line, Offset(padL, gy), Offset(w - 8f, gy), 1.2f)
            drawContext.canvas.nativeCanvas.drawText("$g", 14f, gy + 8f, axisPaint())
        }
        // hour labels
        val hourMs = 3_600_000L
        listOf(0, 6, 12, 18).forEach { hr ->
            val t = dayStart + hr * hourMs
            if (t < nowMs) {
                val label = when (hr) { 0 -> "12a"; 12 -> "12p"; else -> if (hr < 12) "${hr}a" else "${hr - 12}p" }
                drawContext.canvas.nativeCanvas.drawText(label, x(t) - 12f, h - 4f, axisPaint())
            }
        }

        val line = Path(); val fill = Path()
        var prev: Long? = null
        var segStartX = 0f
        pts.forEach { (bpm, t) ->
            val px = x(t); val py = y(bpm)
            if (prev == null || t - prev!! > 10 * 60_000L) {
                // close previous fill segment
                if (prev != null) { fill.lineTo(x(prev!!), h - padB); fill.close() }
                line.moveTo(px, py); fill.moveTo(px, h - padB); fill.lineTo(px, py); segStartX = px
            } else { line.lineTo(px, py); fill.lineTo(px, py) }
            prev = t
        }
        if (prev != null) { fill.lineTo(x(prev!!), h - padB); fill.close() }
        drawPath(fill, Brush.verticalGradient(listOf(PL.Sys.copy(alpha = 0.30f), Color.Transparent)))
        drawPath(line, PL.Sys, style = Stroke(width = 3.5f))
        drawCircle(PL.Sys, 6f, Offset(x(pts.last().second), y(pts.last().first)))
    }
}

/** Sleep-stage hypnogram, pro-style: connected corners are SQUARE so stems
 *  grow flush out of flat block faces (one continuous shape per transition);
 *  free corners stay rounded. Stems gradient from stage color to stage color. */
@Composable
fun Hypnogram(night: HealthConnectManager.SleepNight, heightDp: Int = 150) {
    if (night.stages.isEmpty()) return
    fun row(type: Int): Int? = when (type) {
        1, 3, 7 -> 0; 6 -> 1; 2, 4, 0 -> 2; 5 -> 3; else -> 2
    }
    val rowColor = listOf(PL.Drain, PL.Sleep, Color(0xFF2BB3A3), PL.Dia)
    val labels = listOf("Awake", "REM", "Light", "Deep")

    Canvas(Modifier.fillMaxWidth().height(heightDp.dp)) {
        val padL = 84f; val padB = 24f; val padT = 6f
        val w = size.width; val h = size.height
        val rowH = (h - padB - padT) / 4f
        fun x(t: Long) = padL + (w - padL - 8f) * (t - night.start).toFloat() / (night.end - night.start).toFloat()
        fun blockTop(r: Int) = padT + rowH * r + rowH * 0.20f
        fun blockBot(r: Int) = blockTop(r) + rowH * 0.60f

        val axisPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#5B6D8A"); textSize = 24f
        }
        labels.forEachIndexed { i, lab ->
            drawContext.canvas.nativeCanvas.drawText(lab, 8f, padT + rowH * i + rowH / 2 + 8f, axisPaint)
        }

        // Resolve spans -> renderable blocks with connection flags
        data class Blk(val r: Int, val x0: Float, val x1: Float, val cS: Boolean, val cE: Boolean)
        val spans = night.stages.mapNotNull { sp -> row(sp.type)?.let { Triple(it, sp.start, sp.end) } }
        val blks = spans.mapIndexed { i, (r, st, en) ->
            val cS = i > 0 && spans[i - 1].first != r
            val cE = i < spans.lastIndex && spans[i + 1].first != r
            Blk(r, x(st), maxOf(x(en), x(st) + 8f), cS, cE)
        }

        // Stems first: flat face of block A -> flat face of block B, 2px tucked in
        for (i in 1 until spans.size) {
            val r = spans[i].first; val pr = spans[i - 1].first
            if (pr == r) continue
            val cx = x(spans[i].second)
            val down = r > pr
            val yFrom = if (down) blockBot(pr) - 2f else blockTop(pr) + 2f
            val yTo = if (down) blockTop(r) + 2f else blockBot(r) - 2f
            val yMin = minOf(yFrom, yTo); val yMax = maxOf(yFrom, yTo)
            val topC = if (yFrom < yTo) rowColor[pr] else rowColor[r]
            val botC = if (yFrom < yTo) rowColor[r] else rowColor[pr]
            drawLine(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(topC, botC), startY = yMin, endY = yMax),
                start = Offset(cx, yFrom), end = Offset(cx, yTo),
                strokeWidth = 5f,
            )
        }

        // Blocks on top: square corners where connected, rounded where free
        val rad = CornerRadius(12f, 12f)
        val none = CornerRadius.Zero
        blks.forEach { b ->
            val top = blockTop(b.r); val bot = blockBot(b.r)
            val path = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        b.x0, top, b.x1, bot,
                        topLeftCornerRadius = if (b.cS) none else rad,
                        topRightCornerRadius = if (b.cE) none else rad,
                        bottomRightCornerRadius = if (b.cE) none else rad,
                        bottomLeftCornerRadius = if (b.cS) none else rad,
                    )
                )
            }
            drawPath(path, rowColor[b.r])
        }

        val fmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)
        drawContext.canvas.nativeCanvas.drawText(fmt.format(night.start), padL, h - 4f, axisPaint)
        val endLabel = fmt.format(night.end)
        drawContext.canvas.nativeCanvas.drawText(endLabel, w - 8f - endLabel.length * 12f, h - 4f, axisPaint)
    }
}
