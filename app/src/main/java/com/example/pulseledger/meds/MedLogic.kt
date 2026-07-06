package com.example.pulseledger.meds

import com.example.pulseledger.data.db.BpReading
import java.time.Duration
import java.time.Instant

object MedLogic {

    /** Days of supply remaining and the date a refill should be ordered. */
    data class RefillForecast(val daysLeft: Int, val orderBy: Instant)

    fun refillForecast(med: Med, leadTimeDays: Int = 7, now: Instant = Instant.now()): RefillForecast {
        val daysSinceRefill = Duration.between(Instant.ofEpochMilli(med.lastRefillEpoch), now).toDays()
        val used = daysSinceRefill * med.unitsPerDay
        val left = ((med.quantityOnHand - used) / med.unitsPerDay).toInt().coerceAtLeast(0)
        return RefillForecast(left, now.plus(Duration.ofDays((left - leadTimeDays).coerceAtLeast(0).toLong())))
    }

    /**
     * Doctor-conversation summary: compares BP before vs after a med start/change.
     * The app never suggests changing a dose — it prepares the evidence so the
     * user can have an informed conversation with their prescriber.
     */
    data class EffectSummary(
        val beforeSys: Int, val beforeDia: Int,
        val afterSys: Int, val afterDia: Int,
        val nBefore: Int, val nAfter: Int,
    )

    fun effectSince(med: Med, readings: List<BpReading>, windowDays: Long = 30): EffectSummary? {
        val start = med.startedEpoch
        val before = readings.filter { it.epochMillis in (start - windowDays * 86_400_000) until start }
        val after = readings.filter { it.epochMillis >= start }
        if (before.size < 5 || after.size < 5) return null
        fun avg(l: List<BpReading>, f: (BpReading) -> Int) = l.map(f).average().toInt()
        return EffectSummary(
            avg(before) { it.systolic }, avg(before) { it.diastolic },
            avg(after) { it.systolic }, avg(after) { it.diastolic },
            before.size, after.size,
        )
    }

    /** Tagged-vs-untagged comparison used for the private log too (n-of-1). */
    fun taggedDelta(taggedDays: Set<Long>, byDay: Map<Long, Double>): Double? {
        val tagged = byDay.filterKeys { it in taggedDays }.values
        val rest = byDay.filterKeys { it !in taggedDays }.values
        if (tagged.size < 3 || rest.size < 5) return null
        return tagged.average() - rest.average()
    }
}
