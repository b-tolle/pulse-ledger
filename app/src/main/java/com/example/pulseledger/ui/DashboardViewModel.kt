package com.example.pulseledger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulseledger.data.HealthConnectManager
import com.example.pulseledger.data.db.BpReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    data class Ui(
        val loading: Boolean = false,
        val readings: List<BpReading> = emptyList(),   // newest first
        val stepsToday: Long? = null,
        val steps7dAvg: Long? = null,
        val restingHr: Long? = null,
        val error: String? = null,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui

    fun load() {
        if (_ui.value.loading) return
        _ui.value = _ui.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val hc = HealthConnectManager(getApplication())
                val now = Instant.now()
                val from = now.minus(Duration.ofDays(30))

                val bp = hc.readBloodPressure(from, now).map {
                    BpReading(
                        epochMillis = it.time.toEpochMilli(),
                        systolic = it.systolic.inMillimetersOfMercury.toInt(),
                        diastolic = it.diastolic.inMillimetersOfMercury.toInt(),
                        pulse = null,
                        source = "health_connect",
                    )
                }.sortedByDescending { it.epochMillis }

                val steps = hc.readSteps(from, now)
                val dayMs = 86_400_000L
                val todayStart = now.toEpochMilli() - now.toEpochMilli() % dayMs
                val byDay = HashMap<Long, Long>()
                for (s in steps) {
                    val d = s.startTime.toEpochMilli() - s.startTime.toEpochMilli() % dayMs
                    byDay[d] = (byDay[d] ?: 0L) + s.count
                }
                val today = byDay[todayStart]
                val last7 = byDay.filterKeys { it in (todayStart - 7 * dayMs) until todayStart }.values
                val avg7 = if (last7.isEmpty()) null else last7.sum() / last7.size

                val rhr = hc.readRestingHeartRate(from, now)
                    .maxByOrNull { it.time }?.beatsPerMinute

                _ui.value = Ui(
                    loading = false,
                    readings = bp,
                    stepsToday = today,
                    steps7dAvg = avg7,
                    restingHr = rhr,
                )
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(loading = false, error = t.message ?: "read failed")
            }
        }
    }
}
