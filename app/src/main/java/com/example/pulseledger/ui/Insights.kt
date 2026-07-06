package com.example.pulseledger.ui

import com.example.pulseledger.data.db.DailySummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt

data class Insight(val emoji: String, val title: String, val body: String)

/** Real findings mined from the imported archive — not just charts. */
fun mineInsights(s: List<DailySummary>): List<Insight> {
    val out = ArrayList<Insight>()
    val zone = ZoneId.systemDefault()
    val fmt = DateTimeFormatter.ofPattern("MMMM d, yyyy").withZone(zone)

    // Record step day
    s.filter { (it.steps ?: 0) > 0 }.maxByOrNull { it.steps!! }?.let {
        out += Insight("🏆", "Biggest day ever",
            "%,d steps on %s. What were you doing?".format(it.steps, fmt.format(Instant.ofEpochMilli(it.dayEpoch))))
    }

    // Weekend vs weekday steps
    val byDow = s.filter { (it.steps ?: 0) > 0 }.groupBy {
        Instant.ofEpochMilli(it.dayEpoch).atZone(zone).dayOfWeek.value >= 6
    }
    val wk = byDow[false]?.map { it.steps!!.toDouble() }?.average()
    val we = byDow[true]?.map { it.steps!!.toDouble() }?.average()
    if (wk != null && we != null) {
        val diff = ((we - wk) / wk * 100).toInt()
        out += if (diff >= 0)
            Insight("🌤", "Weekend walker", "You move $diff% more on weekends (%,.0f vs %,.0f steps).".format(we, wk))
        else
            Insight("💼", "Weekday mover", "You move ${-diff}% more on weekdays (%,.0f vs %,.0f steps).".format(wk, we))
    }

    // Stress ↔ sleep correlation (same-day)
    val pairs = s.mapNotNull { d ->
        val st = d.stressAvg ?: return@mapNotNull null
        val sl = d.sleepMinutes?.toDouble() ?: return@mapNotNull null
        st to sl
    }
    if (pairs.size >= 20) {
        val r = pearson(pairs)
        if (r != null && r < -0.15)
            out += Insight("😮‍💨", "Sleep fights stress",
                "Across ${pairs.size} tracked days, more sleep lines up with lower stress scores (r=%.2f).".format(r))
        else if (r != null && r > 0.15)
            out += Insight("🤔", "Odd pattern",
                "Your higher-stress days actually show more sleep (r=%.2f) — worth watching.".format(r))
    }

    // Resting HR then vs now
    val rhr = s.filter { it.restingHr != null }
    if (rhr.size >= 30) {
        val first = rhr.take(rhr.size / 3).map { it.restingHr!!.toDouble() }.average()
        val last = rhr.takeLast(rhr.size / 3).map { it.restingHr!!.toDouble() }.average()
        val d = (last - first).toInt()
        if (d <= -2) out += Insight("💪", "Heart getting stronger",
            "Your resting HR dropped from %.0f to %.0f bpm across your tracked history.".format(first, last))
        else if (d >= 3) out += Insight("📈", "Resting HR creeping up",
            "From %.0f to %.0f bpm over your tracked history — one to keep an eye on.".format(first, last))
    }

    // Sleep consistency
    val sleeps = s.mapNotNull { it.sleepMinutes?.toDouble() }
    if (sleeps.size >= 30) {
        val mean = sleeps.average()
        val sd = sqrt(sleeps.sumOf { (it - mean) * (it - mean) } / sleeps.size)
        out += Insight("🛏", "Sleep rhythm",
            "Average night: %dh %02dm, swinging ±%d min. Consistency matters as much as duration.".format(
                (mean / 60).toInt(), (mean % 60).toInt(), sd.toInt()))
    }

    return out
}

private fun pearson(pairs: List<Pair<Double, Double>>): Double? {
    val mx = pairs.map { it.first }.average()
    val my = pairs.map { it.second }.average()
    val cov = pairs.sumOf { (x, y) -> (x - mx) * (y - my) }
    val sx = sqrt(pairs.sumOf { (x, _) -> (x - mx) * (x - mx) })
    val sy = sqrt(pairs.sumOf { (_, y) -> (y - my) * (y - my) })
    return if (sx == 0.0 || sy == 0.0) null else cov / (sx * sy)
}
