package com.example.pulseledger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp

/** Weekly bars with the last (today) highlighted. Empty values render as faint stubs.
 *  Pass [labels] (e.g. day letters) to caption each bar. */
@Composable
fun WeekBars(values: List<Double?>, accent: Color, heightDp: Int = 44, labels: List<String>? = null) {
    Canvas(Modifier.fillMaxWidth().height(heightDp.dp)) {
        val labelPad = if (labels != null) 26f else 0f
        val n = values.size.coerceAtLeast(1)
        val gap = 6f
        val bw = (size.width - gap * (n - 1)) / n
        val plotH = size.height - labelPad
        val maxV = values.filterNotNull().maxOrNull() ?: 1.0
        values.forEachIndexed { i, v ->
            val x = i * (bw + gap)
            val h = if (v != null && maxV > 0) (plotH * (v / maxV)).toFloat().coerceAtLeast(3f) else 3f
            val isLast = i == values.lastIndex
            drawRoundRect(
                color = if (v == null) PL.Line else if (isLast) accent else accent.copy(alpha = 0.45f),
                topLeft = Offset(x, plotH - h),
                size = androidx.compose.ui.geometry.Size(bw, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
            )
            if (labels != null) {
                val label = labels.getOrNull(i) ?: ""
                drawContext.canvas.nativeCanvas.drawText(
                    label, x + bw / 2 - label.length * 6f, size.height - 4f,
                    android.graphics.Paint().apply {
                        color = if (isLast) android.graphics.Color.parseColor("#EAF0F9")
                        else android.graphics.Color.parseColor("#5B6D8A")
                        textSize = 22f
                    })
            }
        }
    }
}

/** Area sparkline with gradient fill (the pretty red HR look). */
@Composable
fun AreaSpark(values: List<Double>, accent: Color, heightDp: Int = 40) {
    if (values.size < 2) return
    Canvas(Modifier.fillMaxWidth().height(heightDp.dp)) {
        val lo = values.min(); val hi = values.max()
        val w = size.width; val h = size.height
        fun x(i: Int) = w * i / (values.size - 1)
        fun y(v: Double) = (h - 4f) * (1f - ((v - lo) / (hi - lo + 1e-9)).toFloat()) + 2f
        val line = Path().apply {
            values.forEachIndexed { i, v -> if (i == 0) moveTo(x(0), y(v)) else lineTo(x(i), y(v)) }
        }
        val fill = Path().apply {
            addPath(line); lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, accent, style = Stroke(width = 3f))
    }
}
