package com.example.pulseledger.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
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
