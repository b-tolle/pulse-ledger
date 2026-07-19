package com.example.pulseledger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Per-device user profile, editable in the Profile sheet. Defaults = Brian. */
object Profile {
    private var prefs: android.content.SharedPreferences? = null
    val rev = androidx.compose.runtime.mutableIntStateOf(0)

    fun init(ctx: android.content.Context) {
        prefs = ctx.getSharedPreferences("profile", android.content.Context.MODE_PRIVATE)
    }
    private fun p() = prefs

    var birth: java.time.LocalDate
        get() = runCatching { java.time.LocalDate.parse(p()?.getString("birth", null) ?: "1981-04-23") }
            .getOrDefault(java.time.LocalDate.of(1981, 4, 23))
        set(v) { p()?.edit()?.putString("birth", v.toString())?.apply(); rev.intValue++ }

    var female: Boolean
        get() = p()?.getBoolean("female", false) ?: false
        set(v) { p()?.edit()?.putBoolean("female", v)?.apply(); rev.intValue++ }

    var heightIn: Int?
        get() = (p()?.getInt("heightIn", 0) ?: 0).takeIf { it > 0 }
        set(v) { p()?.edit()?.putInt("heightIn", v ?: 0)?.apply(); rev.intValue++ }

    var waistIn: Double?
        get() = (p()?.getFloat("waistIn", 0f) ?: 0f).toDouble().takeIf { it > 0 }
        set(v) { p()?.edit()?.putFloat("waistIn", (v ?: 0.0).toFloat())?.apply(); rev.intValue++ }

    var goalLbs: Double?
        get() = (p()?.getFloat("goalLbs", 0f) ?: 0f).toDouble().takeIf { it > 0 }
        set(v) { p()?.edit()?.putFloat("goalLbs", (v ?: 0.0).toFloat())?.apply(); rev.intValue++ }

    var startTab: Int
        get() = p()?.getInt("startTab", 0) ?: 0
        set(v) { p()?.edit()?.putInt("startTab", v)?.apply(); rev.intValue++ }

    var stepGoal: Int
        get() = p()?.getInt("stepGoal", 8000) ?: 8000
        set(v) { p()?.edit()?.putInt("stepGoal", v)?.apply(); rev.intValue++ }

    val age: Int get() = java.time.Period.between(birth, java.time.LocalDate.now()).years
    val maxHr: Int get() = (208 - 0.7 * age).toInt()
    val zoneLight: Int get() = (maxHr * 0.50).toInt()
    val zoneModerate: Int get() = (maxHr * 0.64).toInt()
    val zoneVigorous: Int get() = (maxHr * 0.77).toInt()
}

object Peer {
    val LABEL: String
        get() = (if (Profile.female) "WOMEN" else "MEN") +
            " ${Profile.age / 10 * 10}–${Profile.age / 10 * 10 + 9}"
}

data class RangeSpec(
    val label: String,
    val display: String,        // "62 bpm", "7h 45m"
    val value: Double,
    val trackMin: Double, val trackMax: Double,
    val lo: Double, val hi: Double,   // typical-range band
)

enum class RangeStatus { BELOW, IN, ABOVE }

fun RangeSpec.status(): RangeStatus =
    if (value < lo) RangeStatus.BELOW else if (value > hi) RangeStatus.ABOVE else RangeStatus.IN

@Composable
fun RangeBand(spec: RangeSpec) {
    val st = spec.status()
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(spec.label, color = PL.Txt, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            Text(spec.display, color = PL.Soft, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(end = 8.dp))
            StatusChip(st)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(26.dp)) {
            val w = size.width; val h = size.height
            val trackH = 18f
            val ty = (h - trackH) / 2
            fun px(v: Double) = (w * ((v - spec.trackMin) / (spec.trackMax - spec.trackMin))
                .coerceIn(0.0, 1.0)).toFloat()

            // base track
            drawRoundRect(PL.CardUp, Offset(0f, ty), Size(w, trackH), CornerRadius(9f, 9f))
            // filled portion up to value — green in range, amber out
            val fillC = if (st == RangeStatus.IN) Color(0xFF57C97B) else Color(0xFFE7A93C)
            val fillW = px(spec.value).coerceAtLeast(trackH)
            drawRoundRect(fillC, Offset(0f, ty), Size(fillW, trackH), CornerRadius(9f, 9f))
            // dashed typical-range outline
            val xLo = px(spec.lo); val xHi = px(spec.hi)
            drawRoundRect(
                color = Color(0xFFDDE6F2),
                topLeft = Offset(xLo, ty - 3f),
                size = Size((xHi - xLo).coerceAtLeast(12f), trackH + 6f),
                cornerRadius = CornerRadius(11f, 11f),
                style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 7f))),
            )
            // knob
            val kx = fillW.coerceIn(trackH / 2 + 2f, w - trackH / 2 - 2f)
            drawCircle(PL.Bg, radius = 12f, center = Offset(kx, h / 2))
            drawCircle(Color.White, radius = 9f, center = Offset(kx, h / 2))
        }
    }
}

@Composable
private fun StatusChip(st: RangeStatus) {
    val (label, bg, fg) = when (st) {
        RangeStatus.IN -> Triple("In range", Color(0xFF1E3A2A), Color(0xFF7BE495))
        RangeStatus.ABOVE -> Triple("Above range", Color(0xFF443A14), Color(0xFFF0C36D))
        RangeStatus.BELOW -> Triple("Below range", Color(0xFF443A14), Color(0xFFF0C36D))
    }
    Text(label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp))
}

@Composable
fun PeerCard(specs: List<RangeSpec>) {
    if (specs.isEmpty()) return
    Card {
        SectionLabel("HOW YOU COMPARE · ${Peer.LABEL}")
        Spacer(Modifier.height(6.dp))
        specs.forEach { RangeBand(it) }
        Text("Dashed box = typical range for your peer group (published population norms — context, not targets).",
            color = PL.Dim, fontSize = 10.5.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/* ───────── spec builders from live data (skip metrics with no data) ───────── */

fun heartRanges(ui: DashboardViewModel.Ui): List<RangeSpec> = buildList {
    ui.restingHr?.let {
        val (lo, hi) = if (Profile.female) 58.0 to 78.0 else 55.0 to 75.0
        add(RangeSpec("Resting heart rate", "$it bpm", it.toDouble(), 40.0, 110.0, lo, hi))
    }
    ui.hrvLatest?.let {
        add(RangeSpec("HRV (overnight)", "%.0f ms".format(it), it, 0.0, 100.0, 20.0, 50.0))
    }
}

fun sleepRanges(ui: DashboardViewModel.Ui): List<RangeSpec> = buildList {
    val night = ui.sleepNight
    val totalMin = night?.let { ((it.end - it.start) / 60_000).toInt() }
        ?: ui.summaries.lastOrNull { it.sleepMinutes != null }?.sleepMinutes
    totalMin?.let {
        add(RangeSpec("Sleep duration", "%dh %02dm".format(it / 60, it % 60),
            it / 60.0, 4.0, 11.0, 7.0, 9.0))
    }
    if (night != null && night.stages.isNotEmpty()) {
        val total = ((night.end - night.start) / 60_000).toDouble().coerceAtLeast(1.0)
        fun mins(types: Set<Int>) = night.stages.filter { it.type in types }
            .sumOf { (it.end - it.start) / 60_000 }.toDouble()
        val deepPct = mins(setOf(5)) / total * 100
        val remPct = mins(setOf(6)) / total * 100
        add(RangeSpec("Deep sleep", "%.0f%%".format(deepPct), deepPct, 0.0, 40.0, 10.0, 25.0))
        add(RangeSpec("REM sleep", "%.0f%%".format(remPct), remPct, 0.0, 40.0, 15.0, 25.0))
    }
}

fun pressureRanges(ui: DashboardViewModel.Ui): List<RangeSpec> = buildList {
    val recent = ui.readings.filter {
        it.epochMillis >= System.currentTimeMillis() - 7 * 86_400_000L
    }.ifEmpty { ui.readings.take(1) }
    if (recent.isEmpty()) return@buildList
    val sys = recent.map { it.systolic }.average()
    val dia = recent.map { it.diastolic }.average()
    add(RangeSpec("Systolic (7-day avg)", "%.0f mmHg".format(sys), sys, 80.0, 180.0, 90.0, 120.0))
    add(RangeSpec("Diastolic (7-day avg)", "%.0f mmHg".format(dia), dia, 40.0, 120.0, 60.0, 80.0))
    add(RangeSpec("Pulse pressure", "%.0f".format(sys - dia), sys - dia, 10.0, 90.0, 30.0, 50.0))
}

fun activityRanges(ui: DashboardViewModel.Ui): List<RangeSpec> = buildList {
    ui.steps7dAvg?.let {
        add(RangeSpec("Steps (7-day avg)", "%,d".format(it), it.toDouble(),
            0.0, 15_000.0, 7_000.0, 10_000.0))
    }
}


fun weightRanges(ui: DashboardViewModel.Ui): List<RangeSpec> = buildList {
    val lastLbs = ui.weights.lastOrNull()?.lbs ?: return@buildList
    Profile.heightIn?.let { h ->
        val bmi = 703.0 * lastLbs / (h * h)
        add(RangeSpec("BMI", "%.1f".format(bmi), bmi, 14.0, 42.0, 18.5, 24.9))
        Profile.waistIn?.let { w ->
            val whtr = w / h
            add(RangeSpec("Waist-to-height", "%.2f".format(whtr), whtr, 0.30, 0.80, 0.40, 0.49))
            val bri = bri(w, h.toDouble())
            add(RangeSpec("Body Roundness Index", "%.1f".format(bri), bri, 1.0, 12.0, 3.41, 5.46))
        }
    }
}

/** JAMA 2024 Body Roundness Index. Waist + height in the same units. */
fun bri(waist: Double, height: Double): Double {
    val term = (waist / (2 * Math.PI)) / (0.5 * height)
    return 364.2 - 365.5 * Math.sqrt((1.0 - term * term).coerceAtLeast(0.0))
}
