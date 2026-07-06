package com.example.pulseledger.env

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Phone-sensor odds and ends that correlate with cardio data:
 *  - Barometer (Sensor.TYPE_PRESSURE): falling fronts ↔ BP for many people
 *  - Ambient light before bed (TYPE_LIGHT): light hygiene vs sleep score
 *  - Evening screen minutes via UsageStatsManager (needs the special
 *    "Usage access" toggle in system settings, not a normal permission)
 */
@Entity(tableName = "env_samples")
data class EnvSample(
    @PrimaryKey val epochMillis: Long,
    val pressureHpa: Float?,
    val lux: Float?,
    val eveningScreenMin: Int?,   // filled once nightly
)

class EnvSampler(context: Context) {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val hasBarometer: Boolean = sm.getDefaultSensor(Sensor.TYPE_PRESSURE) != null
    val hasLight: Boolean = sm.getDefaultSensor(Sensor.TYPE_LIGHT) != null
    // Register listeners from a WorkManager job every ~30 min; one-shot read, unregister.
}
