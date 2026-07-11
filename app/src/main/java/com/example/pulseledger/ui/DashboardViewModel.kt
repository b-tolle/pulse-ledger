package com.example.pulseledger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.example.pulseledger.data.CsvImporter
import com.example.pulseledger.data.HealthConnectManager
import com.example.pulseledger.data.db.BpReading
import com.example.pulseledger.data.db.DailySummary
import com.example.pulseledger.data.db.LocationDay
import com.example.pulseledger.life.CalendarReader
import com.example.pulseledger.life.Places
import com.example.pulseledger.life.CurrentLocation
import com.example.pulseledger.data.db.Db
import com.example.pulseledger.backfill.SamsungImporter
import com.example.pulseledger.backfill.LocationImporter
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
        val summaries: List<DailySummary> = emptyList(),
        val calendar: CalendarReader.DayLoad? = null,
        val locationDays: Map<Long, LocationDay> = emptyMap(),
        val locationDayCount: Int = 0,
        val currentPlace: String? = null,
        val anchors: List<Places.Anchor> = emptyList(),
        val latestHr: Long? = null,
        val latestHrTime: Long? = null,
        val latestHrSource: String? = null,
        val hrSampleCount: Int = 0,
        val hrSources: Set<String> = emptySet(),
        val hrToday: List<Pair<Long, Long>> = emptyList(),
        val hrvLatest: Double? = null,
        val sleepNight: HealthConnectManager.SleepNight? = null,
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

                val dayMs = 86_400_000L
                val todayStart = now.toEpochMilli() - now.toEpochMilli() % dayMs
                val byDay = hc.dailySteps(from, now)
                val today = byDay[todayStart]
                val last7 = byDay.filterKeys { it in (todayStart - 7 * dayMs) until todayStart }.values
                val avg7 = if (last7.isEmpty()) null else last7.sum() / last7.size

                val rhr = hc.readRestingHeartRate(from, now)
                    .maxByOrNull { it.time }?.beatsPerMinute
                val latestHr = runCatching { hc.latestHeartRate(from, now) }.getOrNull()
                val hrDiag = runCatching { hc.heartRateSources(from, now) }.getOrNull()
                val dayStart = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
                val hrToday = runCatching { hc.hrSamples(dayStart, now) }.getOrDefault(emptyList())
                val hrvNow = runCatching { hc.latestHrv(now.minus(Duration.ofDays(2)), now) }.getOrNull()
                val night = runCatching { hc.lastSleepSession(now) }.getOrNull()

                val dao = Db.get(getApplication()).dao()
                val histDays = dao.stepDaysCount()
                val histSince = dao.earliestStepDay()
                val allSummaries = dao.allSummaries()
                val locDaysList = dao.allLocationDays()
                val locDays = locDaysList.associateBy { it.dayEpoch }
                val anchors = Places.learnAnchors(locDaysList)
                val here = runCatching { CurrentLocation.get(getApplication()) }.getOrNull()
                val place = here?.let { (la, ln) ->
                    Places.classify(la, ln, anchors)?.label ?: "Out and about"
                }
                val cal = runCatching { CalendarReader.today(getApplication()) }.getOrNull()

                _ui.value = _ui.value.copy(
                    loading = false,
                    readings = merged,
                    stepsToday = today,
                    steps7dAvg = avg7,
                    restingHr = rhr,
                    historyDays = histDays,
                    historySince = histSince,
                    summaries = allSummaries,
                    calendar = cal,
                    locationDays = locDays,
                    locationDayCount = locDays.size,
                    currentPlace = place,
                    anchors = anchors,
                    latestHr = latestHr?.first,
                    latestHrTime = latestHr?.second?.toEpochMilli(),
                    latestHrSource = latestHr?.third,
                    hrSampleCount = hrDiag?.first ?: 0,
                    hrSources = hrDiag?.second ?: emptySet(),
                    hrToday = hrToday,
                    hrvLatest = hrvNow,
                    sleepNight = night,
                )
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(loading = false, error = t.message ?: "read failed")
            }
        }
    }

    fun weekly(pick: (com.example.pulseledger.data.db.DailySummary) -> Double?): List<Double?> {
        val now = System.currentTimeMillis(); val dayMs = 86_400_000L
        val today = now - now % dayMs
        val byDay = _ui.value.summaries.associateBy { it.dayEpoch }
        return (6 downTo 0).map { back -> byDay[today - back * dayMs]?.let(pick) }
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

    fun importCsvs(uris: List<Uri>) {
        viewModelScope.launch {
            val messages = ArrayList<String>()
            for (uri in uris) messages += importOne(uri)
            _ui.value = _ui.value.copy(notice = messages.joinToString("\n"))
            load()
        }
    }

    private suspend fun importOne(uri: Uri): String {
        return try {
            val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return "Couldn't open a file"

            // Google location history (JSON)? Detect from the first bytes, then
            // STREAM from a fresh input stream (never hold the whole file).
            val head = String(bytes.copyOfRange(0, minOf(bytes.size, 200)))
            if (head.contains("semanticSegments") || head.contains("timelineObjects") || head.contains("\"locations\"")) {
                val cr = getApplication<Application>().contentResolver
                val days = cr.openInputStream(uri)?.use { LocationImporter.parse(it) } ?: emptyList()
                if (days.isNotEmpty()) {
                    days.chunked(500).forEach { Db.get(getApplication()).dao().upsertLocationDays(it) }
                    val totalKm = days.sumOf { it.distanceMeters } / 1000.0
                    return "✓ ${days.size} days of location · ${"%,.0f".format(totalKm * 0.621371)} mi mapped"
                }
                return "Recognized a location file but found no usable points"
            }

            val sam = SamsungImporter.parse(ByteArrayInputStream(bytes))
            if (sam != null) {
                if (sam.summaries.isEmpty()) return "Recognized ${sam.kind} file but found no usable rows"
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
                            stressAvg = new.stressAvg ?: old.stressAvg,
                            exerciseMin = new.exerciseMin ?: old.exerciseMin,
                            weightKg = new.weightKg ?: old.weightKg,
                            ecgCount = new.ecgCount ?: old.ecgCount,
                        )
                    } ?: new
                }
                dao.upsertSummaries(merged)
                return "✓ ${sam.summaries.size} days of ${sam.kind}" +
                    if (sam.skipped > 0) " (${sam.skipped} skipped)" else ""
            }

            val res = CsvImporter.parse(ByteArrayInputStream(bytes))
            if (res.readings.isEmpty()) "No BP readings found in one file"
            else {
                Db.get(getApplication()).dao().upsertReadings(res.readings)
                "✓ ${res.readings.size} BP readings" + if (res.skipped > 0) " (${res.skipped} skipped)" else ""
            }
        } catch (t: Throwable) {
            "Import failed: ${t.message}"
        }
    }
}