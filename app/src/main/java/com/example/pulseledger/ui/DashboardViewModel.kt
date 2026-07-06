package com.example.pulseledger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.example.pulseledger.data.CsvImporter
import com.example.pulseledger.data.HealthConnectManager
import com.example.pulseledger.data.db.BpReading
import com.example.pulseledger.data.db.Db
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
        val notice: String? = null,
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
                }

                val local = Db.get(getApplication()).dao().latestReadingsOnce(5000)
                val merged = (bp + local)
                    .distinctBy { it.epochMillis / 60000 }   // dedupe to the minute
                    .sortedByDescending { it.epochMillis }

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

                _ui.value = _ui.value.copy(
                    loading = false,
                    readings = merged,
                    stepsToday = today,
                    steps7dAvg = avg7,
                    restingHr = rhr,
                )
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(loading = false, error = t.message ?: "read failed")
            }
        }
    }

    fun addManual(sys: Int, dia: Int, pulse: Int?) {
        viewModelScope.launch {
            Db.get(getApplication()).dao().upsertReadings(listOf(
                BpReading(System.currentTimeMillis(), sys, dia, pulse, "manual")
            ))
            _ui.value = _ui.value.copy(notice = "Saved $sys/$dia")
            load()
        }
    }

    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            try {
                val res = getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                    CsvImporter.parse(it)
                }
                if (res == null || res.readings.isEmpty()) {
                    _ui.value = _ui.value.copy(notice = "No readings found in that file")
                } else {
                    Db.get(getApplication()).dao().upsertReadings(res.readings)
                    _ui.value = _ui.value.copy(notice = "Imported ${res.readings.size} readings" +
                        if (res.skipped > 0) " (${res.skipped} rows skipped)" else "")
                    load()
                }
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(notice = "Import failed: ${t.message}")
            }
        }
    }
}
