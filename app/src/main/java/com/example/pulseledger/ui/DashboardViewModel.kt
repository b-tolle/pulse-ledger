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

    data class WorkoutUi(
        val title: String, val start: Long, val end: Long,
        val avgHr: Int?, val maxHr: Int?, val spark: List<Double>,
    )

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
        val hrvWeek: List<Double?> = emptyList(),
        val stepWeekLive: List<Double?> = emptyList(),
        val weekLabels: List<String> = emptyList(),
        val sleepNight: HealthConnectManager.SleepNight? = null,
        val workouts: List<WorkoutUi> = emptyList(),
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

                val zoneId = java.time.ZoneId.systemDefault()
                val todayLocal = java.time.LocalDate.now(zoneId)
                val todayStart = todayLocal.atStartOfDay(zoneId).toInstant().toEpochMilli()
                val byDay = hc.dailySteps(from, now)
                val today = byDay[todayStart]
                val last7 = (1..7).mapNotNull {
                    byDay[todayLocal.minusDays(it.toLong()).atStartOfDay(zoneId).toInstant().toEpochMilli()]
                }
                val avg7 = if (last7.isEmpty()) null else last7.sum() / last7.size
                val stepWeekLive = (6 downTo 0).map { back ->
                    byDay[todayLocal.minusDays(back.toLong()).atStartOfDay(zoneId).toInstant().toEpochMilli()]?.toDouble()
                }
                val weekLabels = (6 downTo 0).map { back ->
                    todayLocal.minusDays(back.toLong()).dayOfWeek.name.take(1)
                }

                val rhr = hc.readRestingHeartRate(from, now)
                    .maxByOrNull { it.time }?.beatsPerMinute
                val latestHr = runCatching { hc.latestHeartRate(from, now) }.getOrNull()
                val hrDiag = runCatching { hc.heartRateSources(from, now) }.getOrNull()
                val dayStart = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
                val hrToday = runCatching { hc.hrSamples(dayStart, now) }.getOrDefault(emptyList())
                val hrvNow = runCatching { hc.latestHrv(now.minus(Duration.ofDays(2)), now) }.getOrNull()
                val hrvByDay = runCatching { hc.dailyHrv(now.minus(Duration.ofDays(8)), now) }.getOrDefault(emptyMap())
                val hrvWeek = (6 downTo 0).map { back ->
                    hrvByDay[java.time.LocalDate.now(zoneId2()).minusDays(back.toLong())
                        .atStartOfDay(zoneId2()).toInstant().toEpochMilli()]
                }
                val night = runCatching { hc.lastSleepSession(now) }.getOrNull()
                val workouts = runCatching {
                    hc.recentWorkouts(now.minus(Duration.ofDays(14)), now).take(5).map { wk ->
                        val hr = hc.hrSamples(
                            java.time.Instant.ofEpochMilli(wk.start),
                            java.time.Instant.ofEpochMilli(wk.end),
                        ).map { it.first }
                        WorkoutUi(
                            title = wk.title, start = wk.start, end = wk.end,
                            avgHr = hr.takeIf { it.isNotEmpty() }?.average()?.toInt(),
                            maxHr = hr.maxOrNull()?.toInt(),
                            spark = hr.map { it.toDouble() },
                        )
                    }
                }.getOrDefault(emptyList())

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
                    hrvWeek = hrvWeek,
                    stepWeekLive = stepWeekLive,
                    weekLabels = weekLabels,
                    sleepNight = night,
                    workouts = workouts,
                )
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(loading = false, error = t.message ?: "read failed")
            }
        }
    }

    private fun zoneId2() = java.time.ZoneId.systemDefault()

    fun weekly(pick: (com.example.pulseledger.data.db.DailySummary) -> Double?): List<Double?> {
        val now = System.currentTimeMillis(); val dayMs = 86_400_000L
        val today = now - now % dayMs
        val byDay = _ui.value.summaries.associateBy { it.dayEpoch }
        return (6 downTo 0).map { back -> byDay[today - back * dayMs]?.let(pick) }
    }

    fun addManual(sys: Int, dia: Int, pulse: Int?) {
        viewModelScope.launch {
            val nowMs = System.currentTimeMillis()
            Db.get(getApplication()).dao().upsertReadings(listOf(
                BpReading(nowMs, sys, dia, pulse, "manual")
            ))
            val wrote = runCatching {
                HealthConnectManager(getApplication()).writeBloodPressure(listOf(Triple(nowMs, sys, dia)))
            }.getOrDefault(0)
            _ui.value = _ui.value.copy(notice = "Saved $sys/$dia" + if (wrote > 0) " · shared to Health Connect" else "")
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
                // Self-healing re-import: wipe our prior CSV rows locally AND every
                // BP record we previously wrote to Health Connect, then insert fresh.
                runCatching { Db.get(getApplication()).dao().deleteCsvReadings() }
                runCatching { HealthConnectManager(getApplication()).deleteMyBloodPressure() }
                Db.get(getApplication()).dao().upsertReadings(res.readings)
                val wrote = runCatching {
                    HealthConnectManager(getApplication())
                        .writeBloodPressure(res.readings.map { Triple(it.epochMillis, it.systolic, it.diastolic) })
                }.getOrDefault(0)
                "✓ ${res.readings.size} BP readings" +
                    (if (wrote > 0) " · $wrote shared to Health Connect" else "") +
                    (if (res.skipped > 0) " (${res.skipped} skipped)" else "")
            }
        } catch (t: Throwable) {
            "Import failed: ${t.message}"
        }
    }
}