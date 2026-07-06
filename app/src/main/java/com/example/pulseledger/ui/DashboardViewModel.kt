package com.example.pulseledger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.example.pulseledger.data.CsvImporter
import com.example.pulseledger.data.HealthConnectManager
import com.example.pulseledger.data.db.BpReading
import com.example.pulseledger.data.db.Db
import com.example.pulseledger.backfill.SamsungImporter
import java.io.ByteArrayInputStream
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
        val historyDays: Int = 0,
        val historySince: Long? = null,
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

                val dao = Db.get(getApplication()).dao()
                val histDays = dao.stepDaysCount()
                val histSince = dao.earliestStepDay()

                _ui.value = _ui.value.copy(
                    loading = false,
                    readings = merged,
                    stepsToday = today,
                    steps7dAvg = avg7,
                    restingHr = rhr,
                    historyDays = histDays,
                    historySince = histSince,
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
                val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) { _ui.value = _ui.value.copy(notice = "Couldn't open that file"); return@launch }

                // Samsung Health export file?
                SamsungImporter.parse(ByteArrayInputStream(bytes))?.let { sam ->
                    if (sam.summaries.isNotEmpty()) {
                        val dao = Db.get(getApplication()).dao()
                        val existing = sam.summaries.map { it.dayEpoch }.chunked(900)
                            .flatMap { dao.summariesByDays(it) }.associateBy { it.dayEpoch }
                        val merged = sam.summaries.map { new ->
                            existing[new.dayEpoch]?.let { old ->
                                old.copy(
                                    amSystolic = new.amSystolic ?: old.amSystolic,
                                    amDiastolic = new.amDiastolic ?: old.amDiastolic,
                                    pmSystolic = new.pmSystolic ?: old.pmSystolic,
                                    pmDiastolic = new.pmDiastolic ?: old.pmDiastolic,
                                    restingHr = new.restingHr ?: old.restingHr,
                                    hrvRmssd = new.hrvRmssd ?: old.hrvRmssd,
                                    sleepMinutes = new.sleepMinutes ?: old.sleepMinutes,
                                    steps = new.steps ?: old.steps,
                                )
                            } ?: new
                        }
                        dao.upsertSummaries(merged)
                        _ui.value = _ui.value.copy(notice = "Imported ${sam.summaries.size} days of ${sam.kind}" +
                            if (sam.skipped > 0) " (${sam.skipped} rows skipped)" else "")
                        load(); return@launch
                    }
                }

                val res = CsvImporter.parse(ByteArrayInputStream(bytes))
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
