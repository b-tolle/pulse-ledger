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
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
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
        val dayMs = 86_400_000L
        val records = readAll(StepsRecord::class, from, to)
        val hasSegments = HashSet<Long>()
        for (r in records) {
            val st = r.startTime.toEpochMilli(); val en = r.endTime.toEpochMilli()
            if (en - st < 23 * 3_600_000L) hasSegments += st - st % dayMs
        }
        val out = HashMap<Long, Long>()
        for (r in records) {
            val st = r.startTime.toEpochMilli(); val en = r.endTime.toEpochMilli()
            val day = st - st % dayMs
            val isRollup = en - st >= 23 * 3_600_000L
            if (isRollup && day in hasSegments) continue
            out[day] = (out[day] ?: 0L) + r.count
        }
        return out
    }

    suspend fun restingHrLatest(from: Instant, to: Instant): Long? =
        readRestingHeartRate(from, to).maxByOrNull { it.time }?.beatsPerMinute

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
