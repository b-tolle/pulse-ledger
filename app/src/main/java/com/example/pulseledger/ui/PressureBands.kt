package com.example.pulseledger.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.example.pulseledger.data.db.BpReading
import java.time.Instant
import java.time.ZoneId

@Composable
fun PressureBandChart(readings: List<BpReading>) {
    if (readings.size < 2) return
    val days = readings.sortedBy { it.epochMillis }.takeLast(21)
    val anim by animateFloatAsState(1f, tween(900), label = "bands")
    // capture as state so recompositions don't restart; simple appearance
    val appeared = remember { Animatable(0f) }
    LaunchedEffect(days.size) { appeared.snapTo(0f); appeared.animateTo(1f, tween(700)) }

    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        val w = size.width; val h = size.height
        val padL = 64f; val padB = 22f; val padT = 8f
        val lo = 55f; val hi = 155f
        fun y(v: Float) = padT + (h - padB - padT) * (1f - (v - lo) / (hi - lo))
        val plotW = w - padL - 10f
        val bw = plotW / days.size

        // AHA zones
        drawRect(androidx.compose.ui.graphics.Color(0x14F5A623), Offset(padL, y(130f)), Size(plotW, y(120f) - y(130f)))
        drawRect(androidx.compose.ui.graphics.Color(0x14FF5D73), Offset(padL, y(155f)), Size(plotW, y(130f) - y(155f)))

        // gridlines + labels
        listOf(60, 80, 100, 120, 140).forEach { g ->
            val gy = y(g.toFloat())
            drawLine(PL.Line, Offset(padL, gy), Offset(w - 10f, gy), 1.2f)
            drawContext.canvas.nativeCanvas.drawText("$g", 12f, gy + 8f,
                android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#5B6D8A"); textSize = 24f })
        }

        days.forEachIndexed { i, r ->
            val x = padL + i * bw + bw / 2
            val sysY = y(r.systolic.toFloat())
            val diaY = y(r.diastolic.toFloat())
            val mapY = y((r.diastolic + (r.systolic - r.diastolic) / 3f))
            val grow = (appeared.value * days.size - i).coerceIn(0f, 1f)
            val midY = (sysY + diaY) / 2
            val topY = midY + (sysY - midY) * grow
            val botY = midY + (diaY - midY) * grow

            drawLine(androidx.compose.ui.graphics.Color(0xFF2A3A57),
                Offset(x, topY), Offset(x, botY), strokeWidth = bw * 0.42f, cap = StrokeCap.Round)
            if (grow > 0.9f) {
                drawCircle(PL.Sys, bw * 0.22f, Offset(x, sysY))
                drawCircle(PL.Dia, bw * 0.22f, Offset(x, diaY))
                drawCircle(PL.Txt, 2.2f, Offset(x, mapY))
            }
        }
    }
}
