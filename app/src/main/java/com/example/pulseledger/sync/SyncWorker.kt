package com.example.pulseledger.sync

import android.content.Context
import androidx.work.*
import com.example.pulseledger.data.HealthConnectManager
import com.example.pulseledger.data.db.AppDatabase
import com.example.pulseledger.data.db.BpReading
import androidx.room.Room
import java.time.Duration
import java.time.Instant

/** Periodically pulls the last 30 days from Health Connect into Room. */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val hc = HealthConnectManager(applicationContext)
        if (!hc.isAvailable() || !hc.hasAllPermissions()) return Result.retry()

        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "pulse.db").build()
        val to = Instant.now()
        val from = to.minus(Duration.ofDays(30))

        val bp = hc.readBloodPressure(from, to).map {
            BpReading(
                epochMillis = it.time.toEpochMilli(),
                systolic = it.systolic.inMillimetersOfMercury.toInt(),
                diastolic = it.diastolic.inMillimetersOfMercury.toInt(),
                pulse = null,
                source = "health_connect",
            )
        }
        db.dao().upsertReadings(bp)
        // TODO: build DailySummary rows from sleep/RHR/HRV/steps reads here.
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(Duration.ofHours(4)).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "hc_sync", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
