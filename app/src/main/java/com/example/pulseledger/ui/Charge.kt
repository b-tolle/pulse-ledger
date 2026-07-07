package com.example.pulseledger.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pulseledger.data.db.DailySummary

data class ChargeResult(val value: Int, val contributors: List<Pair<String, Int>>)

/**
 * Charge without a wearable yet: last night's sleep charges, today's
 * steps/exercise/stress drain. Gets smarter when the Fitbit Air adds HRV.
 */
fun computeCharge(
    summaries: List<DailySummary>,
    stepsToday: Long?,
    meetings: Int? = null,
    busyMinutes: Int? = null,
): ChargeResult {
    val now = System.currentTimeMillis()
    val recent = summaries.filter { it.dayEpoch >= now - 3 * 86_400_000L }
    val sleepMin = recent.lastOrNull { it.sleepMinutes != null }?.sleepMinutes
    val stress = recent.lastOrNull { it.stressAvg != null }?.stressAvg
    val exercise = recent.lastOrNull { it.dayEpoch >= now - 86_400_000L }?.exerciseMin

    val contributors = ArrayList<Pair<String, Int>>()
    var v = 25
    sleepMin?.let {
        val gain = (it * 55 / 480).coerceAtMost(60)
        v += gain; contributors += "Sleep · ${it / 60}h ${it % 60}m" to gain
    } ?: run { v += 25; contributors += "Sleep · no data yet" to 25 }
    stepsToday?.let {
        val drain = (it / 400).toInt().coerceAtMost(30)
        if (drain > 0) { v -= drain; contributors += "Activity · %,d steps".format(it) to -drain }
    }
    exercise?.let {
        val drain = (it / 5).coerceAtMost(20)
        if (drain > 0) { v -= drain; contributors += "Workout · ${it} min" to -drain }
    }
    stress?.let {
        val drain = (it / 4).toInt().coerceAtMost(15)
        if (drain > 0) { v -= drain; contributors += "Stress · avg %.0f".format(it) to -drain }
    }
    if (meetings != null && meetings > 0) {
        val drain = (meetings * 2 + (busyMinutes ?: 0) / 60).coerceAtMost(18)
        v -= drain
        val hrs = (busyMinutes ?: 0) / 60
        contributors += "Calendar · $meetings event${if (meetings>1) "s" else ""}, ${hrs}h booked" to -drain
    }
    return ChargeResult(v.coerceIn(5, 100), contributors)
}

@Composable
fun ChargeGauge(value: Int) {
    val progress by animateFloatAsState(
        targetValue = (value - 5) / 95f,
        animationSpec = tween(1200), label = "charge",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(190.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 26f
            val pad = stroke
            val arcSize = Size(size.width - 2 * pad, size.height - 2 * pad)
            val topLeft = Offset(pad, pad)
            drawArc(PL.Line, startAngle = 130f, sweepAngle = 280f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(
                brush = Brush.sweepGradient(0f to PL.Drain, 0.5f to PL.Charge, 1f to PL.Charge),
                startAngle = 130f, sweepAngle = 280f * progress, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", color = PL.Txt, fontSize = 52.sp,
                fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
            Text("CHARGE", color = PL.Soft, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
            Text(
                when {
                    value >= 75 -> "Plenty in the tank"
                    value >= 45 -> "Cruising"
                    value >= 25 -> "Running low"
                    else -> "Recharge tonight"
                },
                color = if (value >= 45) PL.Charge else PL.Drain, fontSize = 11.sp,
            )
        }
    }
}
