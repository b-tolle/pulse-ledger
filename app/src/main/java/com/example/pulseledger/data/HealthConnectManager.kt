package com.example.pulseledger.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

/**
 * Thin wrapper over the Health Connect client.
 * Fitbit Air data arrives here via the Google Health app; the Omron app
 * writes BloodPressureRecord if its Health Connect integration is enabled.
 */
class HealthConnectManager(private val context: Context) {

    val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    val permissions = setOf(
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getWritePermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
    )

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasAllPermissions(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(permissions)

    suspend fun readBloodPressure(from: Instant, to: Instant): List<BloodPressureRecord> =
        readAll(BloodPressureRecord::class, from, to)

    suspend fun readRestingHeartRate(from: Instant, to: Instant): List<RestingHeartRateRecord> =
        readAll(RestingHeartRateRecord::class, from, to)

    suspend fun readHrv(from: Instant, to: Instant): List<HeartRateVariabilityRmssdRecord> =
        readAll(HeartRateVariabilityRmssdRecord::class, from, to)

    /** Most recent instantaneous HR sample: value, time, and source app. */
    suspend fun latestHeartRate(from: Instant, to: Instant): Triple<Long, Instant, String>? {
        val recs = readAll(HeartRateRecord::class, from, to)
        var best: Triple<Long, Instant, String>? = null
        for (r in recs) for (s in r.samples) {
            if (best == null || s.time.isAfter(best!!.second))
                best = Triple(s.beatsPerMinute, s.time, r.metadata.dataOrigin.packageName)
        }
        return best
    }

    /** Count of HR samples in range + set of source packages (diagnostic). */
    suspend fun heartRateSources(from: Instant, to: Instant): Pair<Int, Set<String>> {
        val recs = readAll(HeartRateRecord::class, from, to)
        val srcs = recs.map { it.metadata.dataOrigin.packageName }.toSet()
        val count = recs.sumOf { it.samples.size }
        return count to srcs
    }

    suspend fun readSleep(from: Instant, to: Instant): List<SleepSessionRecord> =
        readAll(SleepSessionRecord::class, from, to)

    suspend fun readSteps(from: Instant, to: Instant): List<StepsRecord> =
        readAll(StepsRecord::class, from, to)

    /**
     * Correct daily step totals: Health Connect's aggregate() dedupes
     * overlapping records by app priority, so the phone pedometer's segments
     * and Samsung Health's daily rollup don't get double-counted.
     * Returns map of start-of-day-millis -> steps (local time zone).
     */
    /**
     * Daily step totals, de-duplicated. Health Connect often holds BOTH
     * granular pedometer segments AND a redundant full-day rollup
     * (~00:00–23:59) from Samsung Health covering the same hours. Aggregate
     * would sum them (double count), so we read raw records and, per day,
     * keep the timestamped segments and drop the overlapping rollup. A rollup
     * is used only for days that have no segments (older history).
     */
    suspend fun dailySteps(from: Instant, to: Instant): Map<Long, Long> {
        // Use Health Connect's own aggregation. It merges ALL step sources
        // (phone, Samsung Health, Fitbit/Google Health) using the user's
        // Data-sources-and-priority order and de-duplicates overlaps — the
        // correct way to avoid the multi-source double-counting we saw.
        val zone = ZoneId.systemDefault()
        val out = HashMap<Long, Long>()
        val response = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                // Buckets slice from the FILTER START — snap it to local midnight
                // or "today" gets keyed to yesterday's partial bucket.
                timeRangeFilter = TimeRangeFilter.between(
                    LocalDateTime.ofInstant(from, zone).toLocalDate().atStartOfDay(),
                    LocalDateTime.ofInstant(to, zone),
                ),
                timeRangeSlicer = Period.ofDays(1),
            )
        )
        for (bucket in response) {
            val steps = bucket.result[StepsRecord.COUNT_TOTAL] ?: continue
            // Key by LOCAL midnight (UTC flooring shifted "today" after 7 PM CDT)
            val dayKey = bucket.startTime.atZone(zone).toLocalDate()
                .atStartOfDay(zone).toInstant().toEpochMilli()
            out[dayKey] = steps
        }
        return out
    }

    suspend fun restingHrLatest(from: Instant, to: Instant): Long? =
        readRestingHeartRate(from, to).maxByOrNull { it.time }?.beatsPerMinute


    /** All HR samples in range as (bpm, epochMs), time-sorted. */
    suspend fun hrSamples(from: Instant, to: Instant): List<Pair<Long, Long>> {
        val out = ArrayList<Pair<Long, Long>>()
        for (r in readAll(HeartRateRecord::class, from, to))
            for (smp in r.samples) out.add(smp.beatsPerMinute to smp.time.toEpochMilli())
        out.sortBy { it.second }
        return out
    }

    /** Latest HRV (RMSSD ms) sample, if any. */
    suspend fun latestHrv(from: Instant, to: Instant): Double? =
        readAll(HeartRateVariabilityRmssdRecord::class, from, to)
            .maxByOrNull { it.time }?.heartRateVariabilityMillis

    /** Per-local-day average HRV, for weekly trends. */
    suspend fun dailyHrv(from: Instant, to: Instant): Map<Long, Double> {
        val zone = ZoneId.systemDefault()
        return readAll(HeartRateVariabilityRmssdRecord::class, from, to)
            .groupBy {
                it.time.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            }
            .mapValues { (_, v) -> v.map { it.heartRateVariabilityMillis }.average() }
    }

    /** All weight records as (epochMs, lbs). */
    suspend fun readWeights(from: Instant, to: Instant): List<Pair<Long, Double>> =
        readAll(WeightRecord::class, from, to)
            .map { it.time.toEpochMilli() to it.weight.inPounds }
            .sortedBy { it.first }

    suspend fun writeWeight(epochMs: Long, lbs: Double) {
        client.insertRecords(listOf(
            WeightRecord(
                time = Instant.ofEpochMilli(epochMs),
                zoneOffset = java.time.ZoneId.systemDefault().rules.getOffset(Instant.ofEpochMilli(epochMs)),
                weight = androidx.health.connect.client.units.Mass.pounds(lbs),
                metadata = androidx.health.connect.client.records.metadata.Metadata.manualEntry(),
            )
        ))
    }

    data class Workout(val title: String, val start: Long, val end: Long)

    /** Recent exercise sessions (Fitbit auto-detected or manual). */
    suspend fun recentWorkouts(from: Instant, to: Instant): List<Workout> =
        readAll(ExerciseSessionRecord::class, from, to)
            .sortedByDescending { it.endTime }
            .map {
                Workout(
                    title = it.title?.takeIf { t -> t.isNotBlank() } ?: "Workout",
                    start = it.startTime.toEpochMilli(),
                    end = it.endTime.toEpochMilli(),
                )
            }

    data class StageSpan(val type: Int, val start: Long, val end: Long)
    data class SleepNight(val start: Long, val end: Long, val stages: List<StageSpan>)

    /** Most recent sleep session (last 48h) with its stage spans, if recorded. */
    suspend fun lastSleepSession(now: Instant): SleepNight? {
        val sessions = readAll(SleepSessionRecord::class, now.minusSeconds(48 * 3600), now)
        val latest = sessions.maxByOrNull { it.endTime } ?: return null
        val stages = latest.stages.map {
            StageSpan(it.stage, it.startTime.toEpochMilli(), it.endTime.toEpochMilli())
        }.sortedBy { it.start }
        return SleepNight(latest.startTime.toEpochMilli(), latest.endTime.toEpochMilli(), stages)
    }


    /** Deletes every BP record THIS app wrote to Health Connect (any era —
     *  including the year-12-AD ones). Other apps' records are untouched. */
    suspend fun deleteMyBloodPressure() {
        client.deleteRecords(
            BloodPressureRecord::class,
            TimeRangeFilter.before(Instant.now().plusSeconds(172_800)),
        )
    }

    /** Writes BP readings into Health Connect so every app (Google Health, etc.) can see them. */
    suspend fun writeBloodPressure(readings: List<Triple<Long, Int, Int>>): Int {
        if (readings.isEmpty()) return 0
        val records = readings.map { (epochMs, sys, dia) ->
            BloodPressureRecord(
                time = Instant.ofEpochMilli(epochMs),
                zoneOffset = java.time.ZoneId.systemDefault().rules.getOffset(Instant.ofEpochMilli(epochMs)),
                systolic = androidx.health.connect.client.units.Pressure.millimetersOfMercury(sys.toDouble()),
                diastolic = androidx.health.connect.client.units.Pressure.millimetersOfMercury(dia.toDouble()),
                bodyPosition = BloodPressureRecord.BODY_POSITION_UNKNOWN,
                measurementLocation = BloodPressureRecord.MEASUREMENT_LOCATION_UNKNOWN,
                metadata = androidx.health.connect.client.records.metadata.Metadata.manualEntry(),
            )
        }
        client.insertRecords(records)
        return records.size
    }

    private suspend fun <T : Record> readAll(
        type: kotlin.reflect.KClass<T>, from: Instant, to: Instant
    ): List<T> {
        val out = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                    pageToken = pageToken,
                )
            )
            out += response.records
            pageToken = response.pageToken
        } while (pageToken != null)
        return out
    }
}
