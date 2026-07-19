package com.example.pulseledger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pulseledger.data.db.WeightEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun WeightTab(ui: DashboardViewModel.Ui, vm: DashboardViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    if (showAdd) AddWeightDialog(onDismiss = { showAdd = false }, onSave = { showAdd = false; vm.addWeight(it) })
    val w = ui.weights
    val last = w.lastOrNull()?.lbs
    val weekAgo = System.currentTimeMillis() - 7 * 86_400_000L
    val monthAgo = System.currentTimeMillis() - 30 * 86_400_000L
    fun changeSince(cut: Long): Double? {
        val base = w.firstOrNull { it.epochMillis >= cut } ?: return null
        return last?.minus(base.lbs)
    }
    val wk = changeSince(weekAgo); val mo = changeSince(monthAgo)

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp),
    ) {
        item { AppHeader(ui, vm) }
        item {
            OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Log weight", color = PL.Gold)
            }
        }
        item {
            StatHeader("CURRENT", last?.let { "%.1f".format(it) }, "lb", PL.Gold,
                listOf(
                    "7-day" to (wk?.let { "%+.1f".format(it) } ?: ""),
                    "30-day" to (mo?.let { "%+.1f".format(it) } ?: ""),
                    "Goal" to (Profile.goalLbs?.let { "%.0f".format(it) } ?: ""),
                ))
        }
        item { GlpTracker(ui, vm) }
        item { StepsGoalCard(ui) }
        if (w.size >= 2) item {
            Card {
                SectionLabel("TREND · ${w.size} ENTRIES")
                Spacer(Modifier.height(10.dp))
                WeightChart(w, Profile.goalLbs)
            }
        }
        item { BodyCard(last) }
        item { PeerCard(weightRanges(ui)) }
        if (w.isEmpty()) item {
            Card {
                Text("Log your first weight above — trends, BMI, and body-shape metrics build from there. Entries also write to Health Connect so Google Health stays in sync.",
                    color = PL.Soft, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun WeightChart(entries: List<WeightEntry>, goal: Double?) {
    val pts = entries.takeLast(180)
    val t0 = pts.first().epochMillis
    val t1 = pts.last().epochMillis.coerceAtLeast(t0 + 1)
    Canvas(Modifier.fillMaxWidth().height(190.dp)) {
        val padL = 66f; val padB = 28f; val padT = 8f
        val w = size.width; val h = size.height
        val vals = pts.map { it.lbs }
        val lo = (minOf(vals.min(), goal ?: vals.min()) - 3.0)
        val hi = (maxOf(vals.max(), goal ?: vals.max()) + 3.0)
        fun x(t: Long) = padL + (w - padL - 10f) * (t - t0).toFloat() / (t1 - t0).toFloat()
        fun y(v: Double) = padT + (h - padB - padT) * (1f - ((v - lo) / (hi - lo)).toFloat())
        val axis = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#5B6D8A"); textSize = 24f
        }
        listOf(lo + 3, (lo + hi) / 2, hi - 3).forEach { g ->
            val gy = y(g)
            drawLine(PL.Line, Offset(padL, gy), Offset(w - 10f, gy), 1f)
            drawContext.canvas.nativeCanvas.drawText("%.0f".format(g), 10f, gy + 8f, axis)
        }
        goal?.let {
            val gy = y(it)
            drawLine(PL.Gold.copy(alpha = 0.8f), Offset(padL, gy), Offset(w - 10f, gy),
                strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f)))
        }
        // raw line
        val line = Path()
        pts.forEachIndexed { i, e -> if (i == 0) line.moveTo(x(e.epochMillis), y(e.lbs)) else line.lineTo(x(e.epochMillis), y(e.lbs)) }
        drawPath(line, PL.Gold.copy(alpha = 0.45f), style = Stroke(width = 3f))
        // 7-entry moving average
        if (pts.size >= 4) {
            val avg = Path()
            pts.indices.forEach { i ->
                val win = pts.subList(maxOf(0, i - 6), i + 1)
                val v = win.map { it.lbs }.average()
                if (i == 0) avg.moveTo(x(pts[i].epochMillis), y(v)) else avg.lineTo(x(pts[i].epochMillis), y(v))
            }
            drawPath(avg, PL.Gold, style = Stroke(width = 4.5f))
        }
        drawCircle(PL.Gold, 6f, Offset(x(pts.last().epochMillis), y(pts.last().lbs)))
        val fmt = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault())
        drawContext.canvas.nativeCanvas.drawText(fmt.format(Instant.ofEpochMilli(t0)), padL, h - 4f, axis)
        val lastLab = fmt.format(Instant.ofEpochMilli(t1))
        drawContext.canvas.nativeCanvas.drawText(lastLab, w - 10f - lastLab.length * 12f, h - 4f, axis)
    }
    Row {
        Text("— 7-entry average", color = PL.Gold, fontSize = 10.5.sp, modifier = Modifier.padding(end = 12.dp))
        Profile.goalLbs?.let { Text("- - goal %.0f lb".format(it), color = PL.Gold.copy(alpha = 0.7f), fontSize = 10.5.sp) }
    }
}

/** BMI + Body Roundness Index with an honest shape-outline: BRI literally
 *  models the torso as an ellipse from height + waist, so we draw exactly
 *  that — your ellipse vs. the typical-range ellipse. */
@Composable
private fun BodyCard(lastLbs: Double?) {
    Card {
        SectionLabel("BODY METRICS")
        Spacer(Modifier.height(8.dp))
        val h = Profile.heightIn
        if (h == null || lastLbs == null) {
            Text(
                if (lastLbs == null) "Log a weight to compute BMI."
                else "Add your height in Profile (top of screen) to compute BMI — add waist too for the Body Roundness Index and shape view.",
                color = PL.Soft, fontSize = 13.sp, lineHeight = 19.sp)
            return@Card
        }
        val bmi = 703.0 * lastLbs / (h * h)
        val bmiClass = when {
            bmi < 18.5 -> "Underweight"; bmi < 25 -> "Healthy range"
            bmi < 30 -> "Overweight"; else -> "Obese range"
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
            Text("%.1f".format(bmi), color = PL.Txt, fontSize = 34.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(8.dp))
            Text("BMI · $bmiClass", color = PL.Soft, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        val waist = Profile.waistIn
        if (waist == null) {
            Spacer(Modifier.height(6.dp))
            Text("Add waist in Profile to unlock BRI — the newer shape-based metric.",
                color = PL.Dim, fontSize = 11.5.sp)
        } else {
            val briVal = bri(waist, h.toDouble())
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                Text("%.1f".format(briVal), color = PL.Sleep, fontSize = 34.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(8.dp))
                Text("BRI · Body Roundness Index", color = PL.Soft, fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 6.dp))
            }
            Spacer(Modifier.height(8.dp))
            ShapeOutline(waist, h.toDouble())
            Text("Figure drawn from your waist-to-height · dashed marks = mid-typical waist width · color = your BRI zone.",
                color = PL.Dim, fontSize = 10.5.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(8.dp))
            Text("What these mean: BMI is weight relative to height — simple, but blind to where weight sits; muscle and belly score the same. BRI (2024) models your torso as an ellipse from height and waist, so it tracks shape — a better signal for visceral fat and the health risks that come with it. Watching BRI fall while weight drops means the loss is coming from the middle.",
                color = PL.Soft, fontSize = 11.5.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun ShapeOutline(waistIn: Double, heightIn: Double) {
    val ratio = (waistIn / heightIn)
    val briVal = bri(waistIn, heightIn)
    val (zoneColor, zoneLabel) = when {
        briVal < 3.41 -> PL.Dia to "Lean shape"
        briVal < 5.46 -> Color(0xFF57C97B) to "Typical shape"
        briVal < 6.91 -> PL.Drain to "Elevated roundness"
        else -> PL.Sys to "High roundness"
    }
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(210.dp)) {
            val h = size.height - 10f
            val cx = size.width / 2f
            fun Y(f: Float) = 5f + h * f
            // waist half-width from waist-to-height ratio (0.35→slim, 0.65→wide)
            fun waistHalf(r: Double) = (h * (0.10f + ((r - 0.35).toFloat() / 0.30f) * 0.20f))
                .coerceIn(h * 0.07f, h * 0.32f)
            val W = waistHalf(ratio)
            val female = Profile.female
            val shoulder = h * (if (female) 0.185f else 0.215f)
            val bust = h * 0.175f
            val hip = if (female) maxOf(W * 1.18f, h * 0.175f) else maxOf(W * 1.04f, h * 0.15f)
            val thigh = hip * 0.58f
            val knee = h * 0.085f
            val ankle = h * 0.055f

            // one half-outline, mirrored
            fun sidePath(sign: Int): Path = Path().apply {
                moveTo(cx + sign * h * 0.055f, Y(0.175f))              // neck
                quadraticBezierTo(cx + sign * shoulder * 1.05f, Y(0.20f),
                    cx + sign * shoulder, Y(0.26f))                     // shoulder
                quadraticBezierTo(cx + sign * bust * 1.08f, Y(0.33f),
                    cx + sign * bust, Y(0.37f))                         // chest
                quadraticBezierTo(cx + sign * W * 0.96f, Y(0.43f),
                    cx + sign * W, Y(0.475f))                           // waist
                quadraticBezierTo(cx + sign * hip * 1.06f, Y(0.55f),
                    cx + sign * hip, Y(0.60f))                          // hip
                quadraticBezierTo(cx + sign * thigh * 1.02f, Y(0.70f),
                    cx + sign * thigh * 0.82f, Y(0.78f))                // thigh
                quadraticBezierTo(cx + sign * knee, Y(0.86f),
                    cx + sign * ankle, Y(0.985f))                       // calf→ankle
            }
            val body = Path().apply {
                addPath(sidePath(1))
                lineTo(cx + ankle * 0.2f, Y(0.985f))
                addPath(sidePath(-1))
            }
            // fill + stroke in zone color
            drawPath(body, zoneColor.copy(alpha = 0.16f))
            drawPath(sidePath(1), zoneColor, style = Stroke(width = 4f))
            drawPath(sidePath(-1), zoneColor, style = Stroke(width = 4f))
            // head
            drawCircle(zoneColor, radius = h * 0.075f, center = Offset(cx, Y(0.085f)),
                style = Stroke(width = 4f))
            drawCircle(zoneColor.copy(alpha = 0.16f), radius = h * 0.075f, center = Offset(cx, Y(0.085f)))

            // typical waist markers: dashed verticals at the mid-typical width (WHtR 0.45)
            val typicalHalf = waistHalf(0.45)
            val dash = PathEffect.dashPathEffect(floatArrayOf(9f, 8f))
            listOf(-1, 1).forEach { sgn ->
                drawLine(Color(0xFF8CA0BE), 
                    Offset(cx + sgn * typicalHalf, Y(0.41f)),
                    Offset(cx + sgn * typicalHalf, Y(0.545f)),
                    strokeWidth = 2.5f, pathEffect = dash)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(zoneLabel, color = zoneColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AddWeightDialog(onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var lbs by remember { mutableStateOf("") }
    val v = lbs.toDoubleOrNull()
    val valid = v != null && v in 60.0..700.0
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = PL.CardUp,
        title = { Text("Log weight", color = PL.Txt) },
        text = {
            OutlinedTextField(lbs, { lbs = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                label = { Text("Pounds") }, singleLine = true)
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(v!!) }) {
                Text("Save", color = if (valid) PL.Charge else PL.Soft)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = PL.Soft) } },
    )
}


@Composable
private fun StepsGoalCard(ui: DashboardViewModel.Ui) {
    Card {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            SectionLabel("STEPS TODAY", Modifier.weight(1f))
            val today = ui.stepsToday ?: 0L
            Text("%,d / %,d".format(today, Profile.stepGoal), color = PL.Txt,
                fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(10.dp))
        val pct = ((ui.stepsToday ?: 0L).toFloat() / Profile.stepGoal).coerceIn(0f, 1f)
        Canvas(Modifier.fillMaxWidth().height(16.dp)) {
            drawRoundRect(PL.CardUp, cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f))
            if (pct > 0f) drawRoundRect(
                if (pct >= 1f) PL.Charge else PL.Charge.copy(alpha = 0.85f),
                size = androidx.compose.ui.geometry.Size(size.width * pct, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (pct >= 1f) "Goal hit — nice." else "%.0f%% of your daily goal".format(pct * 100),
            color = if (pct >= 1f) PL.Charge else PL.Soft, fontSize = 11.5.sp,
        )
        if (ui.stepWeekLive.any { it != null }) {
            Spacer(Modifier.height(10.dp))
            WeekBars(ui.stepWeekLive, PL.Charge, 52, labels = ui.weekLabels)
        }
    }
}
