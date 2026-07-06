package com.example.pulseledger.domain

import com.example.pulseledger.data.db.BpReading
import com.example.pulseledger.data.db.DailySummary
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** The extra math that neither stock app gives you. */
object Calculations {

    fun meanArterialPressure(sys: Int, dia: Int): Int = (dia + (sys - dia) / 3.0).toInt()

    fun pulsePressure(sys: Int, dia: Int): Int = sys - dia

    enum class BpCategory { NORMAL, ELEVATED, STAGE_1, STAGE_2 }

    /** ACC/AHA 2017 categories. Informational only — not a diagnosis. */
    fun category(sys: Int, dia: Int): BpCategory = when {
        sys >= 140 || dia >= 90 -> BpCategory.STAGE_2
        sys >= 130 || dia >= 80 -> BpCategory.STAGE_1
        sys >= 120 -> BpCategory.ELEVATED
        else -> BpCategory.NORMAL
    }

    data class Averages(val sys: Int, val dia: Int, val n: Int)

    /** 7-day morning (before noon) and evening averages, the way clinicians like it. */
    fun weeklyAmPm(readings: List<BpReading>, zone: ZoneId = ZoneId.systemDefault()):
            Pair<Averages?, Averages?> {
        val (am, pm) = readings.partition {
            Instant.ofEpochMilli(it.epochMillis).atZone(zone).toLocalTime() < LocalTime.NOON
        }
        fun avg(list: List<BpReading>) = list.takeIf { it.isNotEmpty() }?.let { l ->
            Averages(
                sys = l.map { it.systolic }.average().toInt(),
                dia = l.map { it.diastolic }.average().toInt(),
                n = l.size,
            )
        }
        return avg(am) to avg(pm)
    }

    /** HRV baseline = trailing 14-day mean; returns today's deviation in ms. */
    fun hrvDeviation(summaries: List<DailySummary>): Double? {
        val vals = summaries.mapNotNull { it.hrvRmssd }
        if (vals.size < 5) return null
        val baseline = vals.dropLast(1).average()
        return vals.last() - baseline
    }

    /**
     * Pearson correlation between sleep duration and NEXT morning's systolic.
     * A meaningfully negative r suggests short sleep tracks with higher pressure.
     */
    fun sleepVsMorningSystolic(summaries: List<DailySummary>): Double? {
        val pairs = summaries.zipWithNext().mapNotNull { (night, morning) ->
            val s = night.sleepMinutes?.toDouble() ?: return@mapNotNull null
            val y = morning.amSystolic?.toDouble() ?: return@mapNotNull null
            s to y
        }
        if (pairs.size < 7) return null
        val mx = pairs.map { it.first }.average()
        val my = pairs.map { it.second }.average()
        val cov = pairs.sumOf { (x, y) -> (x - mx) * (y - my) }
        val sx = kotlin.math.sqrt(pairs.sumOf { (x, _) -> (x - mx) * (x - mx) })
        val sy = kotlin.math.sqrt(pairs.sumOf { (_, y) -> (y - my) * (y - my) })
        return if (sx == 0.0 || sy == 0.0) null else cov / (sx * sy)
    }
}
