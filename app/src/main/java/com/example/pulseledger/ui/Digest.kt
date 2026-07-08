package com.example.pulseledger.ui

import com.example.pulseledger.data.db.DailySummary
import java.time.Instant
import java.time.ZoneId

data class WeekDigest(
    val steps: Long, val stepsVsPrev: Int,
    val avgSleepMin: Int?, val avgStress: Double?,
    val exerciseMin: Int, val headline: String,
)

fun weeklyDigest(summaries: List<DailySummary>): WeekDigest? {
    if (summaries.isEmpty()) return null
    val now = System.currentTimeMillis()
    val week = summaries.filter { it.dayEpoch >= now - 7 * 86_400_000L }
    val prev = summaries.filter { it.dayEpoch in (now - 14 * 86_400_000L) until (now - 7 * 86_400_000L) }
    if (week.isEmpty()) return null

    val steps = week.sumOf { (it.steps ?: 0).toLong() }
    val prevSteps = prev.sumOf { (it.steps ?: 0).toLong() }
    val vsPrev = if (prevSteps > 0) (((steps - prevSteps).toDouble() / prevSteps) * 100).toInt() else 0
    val sleepVals = week.mapNotNull { it.sleepMinutes }
    val stressVals = week.mapNotNull { it.stressAvg }
    val exercise = week.sumOf { it.exerciseMin ?: 0 }

    val headline = when {
        vsPrev >= 15 -> "Big week — you moved $vsPrev% more than last week 🔥"
        vsPrev <= -15 -> "Quieter week, ${-vsPrev}% fewer steps than last week"
        exercise >= 150 -> "Strong training week — ${exercise} active minutes 💪"
        (sleepVals.average().takeIf { sleepVals.isNotEmpty() } ?: 0.0) >= 450 -> "Well rested — you're averaging good sleep 😴"
        else -> "Steady week. Consistency is the win."
    }
    return WeekDigest(
        steps, vsPrev,
        sleepVals.average().takeIf { sleepVals.isNotEmpty() }?.toInt(),
        stressVals.average().takeIf { stressVals.isNotEmpty() },
        exercise, headline,
    )
}

data class Record(val emoji: String, val label: String, val value: String, val dayEpoch: Long?)

fun records(summaries: List<DailySummary>): List<Record> {
    val out = ArrayList<Record>()
    summaries.filter { (it.steps ?: 0) > 0 }.maxByOrNull { it.steps!! }?.let {
        out += Record("🏆", "Most steps", "%,d".format(it.steps), it.dayEpoch)
    }
    summaries.filter { it.restingHr != null }.minByOrNull { it.restingHr!! }?.let {
        out += Record("💚", "Lowest RHR", "${it.restingHr} bpm", it.dayEpoch)
    }
    summaries.filter { it.sleepMinutes != null }.maxByOrNull { it.sleepMinutes!! }?.let {
        out += Record("🛌", "Longest sleep", "%dh %02dm".format(it.sleepMinutes!! / 60, it.sleepMinutes!! % 60), it.dayEpoch)
    }
    summaries.filter { it.exerciseMin != null }.maxByOrNull { it.exerciseMin!! }?.let {
        out += Record("🔥", "Biggest workout", "${it.exerciseMin} min", it.dayEpoch)
    }
    return out
}
