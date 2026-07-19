package com.example.pulseledger.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthDao {
    @Upsert suspend fun upsertReadings(readings: List<BpReading>)

    @Query("DELETE FROM bp_readings WHERE source = 'csv_import'")
    suspend fun deleteCsvReadings()

    @Upsert suspend fun upsertWeights(entries: List<WeightEntry>)

    @Query("SELECT * FROM weight_entries ORDER BY epochMillis ASC")
    suspend fun allWeights(): List<WeightEntry>
    @Upsert suspend fun upsertSummaries(summaries: List<DailySummary>)

    @Query("SELECT * FROM bp_readings ORDER BY epochMillis DESC LIMIT :limit")
    fun latestReadings(limit: Int): Flow<List<BpReading>>

    @Query("SELECT * FROM bp_readings ORDER BY epochMillis DESC LIMIT :limit")
    suspend fun latestReadingsOnce(limit: Int): List<BpReading>

    @Query("SELECT * FROM daily_summary WHERE dayEpoch >= :fromDay ORDER BY dayEpoch")
    fun summariesSince(fromDay: Long): Flow<List<DailySummary>>

    @Query("SELECT * FROM daily_summary ORDER BY dayEpoch")
    suspend fun allSummaries(): List<DailySummary>

    @Query("SELECT * FROM daily_summary WHERE dayEpoch IN (:days)")
    suspend fun summariesByDays(days: List<Long>): List<DailySummary>

    @Query("SELECT COUNT(*) FROM daily_summary WHERE steps IS NOT NULL")
    suspend fun stepDaysCount(): Int

    @Query("SELECT MIN(dayEpoch) FROM daily_summary WHERE steps IS NOT NULL")
    suspend fun earliestStepDay(): Long?

    @androidx.room.Upsert suspend fun upsertLocationDays(days: List<LocationDay>)

    @Query("SELECT * FROM location_days WHERE dayEpoch = :day")
    suspend fun locationDay(day: Long): LocationDay?

    @Query("SELECT COUNT(*) FROM location_days")
    suspend fun locationDayCount(): Int

    @Query("SELECT * FROM location_days ORDER BY dayEpoch")
    suspend fun allLocationDays(): List<LocationDay>
}

@Database(
    entities = [
        BpReading::class, DailySummary::class,
        com.example.pulseledger.meds.Med::class,
        com.example.pulseledger.meds.DoseLog::class,
        com.example.pulseledger.meds.PrivateEntry::class,
        com.example.pulseledger.life.Place::class,
        com.example.pulseledger.life.PlaceVisit::class,
        com.example.pulseledger.life.TogetherSession::class,
        com.example.pulseledger.env.EnvSample::class,
        LocationDay::class,
        WeightEntry::class,
    ],
    version = 5,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): HealthDao
}
