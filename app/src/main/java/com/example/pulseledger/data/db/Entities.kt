package com.example.pulseledger.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bp_readings")
data class BpReading(
    @PrimaryKey val epochMillis: Long,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?,
    val source: String,          // "health_connect" | "csv_import" | "manual"
)

/** One row per day of derived metrics — this is what the dashboards read. */
@Entity(tableName = "daily_summary")
data class DailySummary(
    @PrimaryKey val dayEpoch: Long,     // start-of-day millis, local tz
    val amSystolic: Int?, val amDiastolic: Int?,
    val pmSystolic: Int?, val pmDiastolic: Int?,
    val restingHr: Int?,
    val hrvRmssd: Double?,
    val sleepMinutes: Int?,
    val steps: Int?,
    val stressAvg: Double? = null,
    val exerciseMin: Int? = null,
    val weightKg: Double? = null,
    val ecgCount: Int? = null,
)


@androidx.room.Entity(tableName = "location_days")
data class LocationDay(
    @androidx.room.PrimaryKey val dayEpoch: Long,
    val distanceMeters: Double,      // total distance moved that day
    val placesJson: String,          // [{lat,lng,label,minutes}] clustered stays
    val pointCount: Int,
)
