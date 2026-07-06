package com.example.pulseledger.backfill

import com.example.pulseledger.data.db.DailySummary
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Importer for Samsung Health "Download personal data" CSVs.
 * Logic validated against real exports (2017–2026):
 *  - Line 1 = metadata ("com.samsung.shealth.tracker.pedometer_day_summary,7000107,7"), may start with BOM
 *  - Line 2 = headers, often prefixed ("com.samsung.health.heart_rate.start_time") → match on the last dot-segment
 *  - Timestamps = "yyyy-MM-dd HH:mm:ss.SSS" in UTC, with a separate time_offset column ("UTC-0500")
 *  - pedometer day_time = epoch-millis of the day; several rows per day (devices) → take max
 */
object SamsungImporter {

    data class Result(val summaries: List<DailySummary>, val kind: String, val skipped: Int)

    private const val DAY = 86_400_000L

    fun parse(stream: InputStream): Result? {
        val lines = stream.bufferedReader().readLines().filter { it.isNotBlank() }
        if (lines.size < 3) return null
        val meta = lines[0].removePrefix("\uFEFF").lowercase()
        if (!meta.startsWith("com.samsung")) return null
        val header = lines[1].split(',').map { it.trim() }
        val rows = lines.drop(2)

        fun col(name: String): Int = header.indexOfFirst { it.substringAfterLast('.').lowercase() == name }

        return when {
            "pedometer_day_summary" in meta -> parseSteps(header, rows, ::col)
            "tracker.heart_rate" in meta -> parseHr(rows, ::col)
            "shealth.sleep" in meta -> parseSleep(rows, ::col)
            "shealth.stress" in meta -> parseDailyAvg(rows, ::col, "score", "stress") { day, v ->
                DailySummary(day, null, null, null, null, null, null, null, null, stressAvg = v) }
            "shealth.exercise" in meta -> parseExercise(rows, ::col)
            "health.weight" in meta -> parseDailyAvg(rows, ::col, "weight", "weight") { day, v ->
                DailySummary(day, null, null, null, null, null, null, null, null, weightKg = v) }
            "health.ecg" in meta -> parseEcg(rows, ::col)
            else -> null
        }
    }

    private fun parseSteps(header: List<String>, rows: List<String>, col: (String) -> Int): Result {
        val iSteps = col("step_count"); val iDay = col("day_time"); val iCreate = col("create_time")
        if (iSteps < 0) return Result(emptyList(), "steps", rows.size)
        var skipped = 0
        val perDay = HashMap<Long, Int>()
        for (r in rows) {
            val c = r.split(',')
            val steps = c.getOrNull(iSteps)?.trim()?.toDoubleOrNull()?.toInt()
            val dayRaw = c.getOrNull(iDay)?.trim().orEmpty()
            val day: Long? = when {
                dayRaw.all { it.isDigit() } && dayRaw.length >= 12 -> dayRaw.toLong() / DAY * DAY
                else -> parseUtc(c.getOrNull(iCreate), null)?.let { it / DAY * DAY }
            }
            if (steps == null || steps <= 0 || day == null) { skipped++; continue }
            perDay[day] = maxOf(perDay[day] ?: 0, steps)
        }
        return Result(perDay.map { steps(it.key, it.value) }, "steps", skipped)
    }

    private fun parseHr(rows: List<String>, col: (String) -> Int): Result {
        val iHr = col("heart_rate"); val iT = col("start_time"); val iOff = col("time_offset")
        if (iHr < 0 || iT < 0) return Result(emptyList(), "heart rate", rows.size)
        var skipped = 0
        val perDayMin = HashMap<Long, Int>()
        for (r in rows) {
            val c = r.split(',')
            val hr = c.getOrNull(iHr)?.trim()?.toDoubleOrNull()?.toInt()
            val t = parseUtc(c.getOrNull(iT), c.getOrNull(iOff))
            if (hr == null || t == null || hr !in 30..230) { skipped++; continue }
            val day = t / DAY * DAY
            perDayMin[day] = minOf(perDayMin[day] ?: 999, hr)
        }
        return Result(perDayMin.map { rhr(it.key, it.value) }, "heart rate", skipped)
    }

    private fun parseSleep(rows: List<String>, col: (String) -> Int): Result {
        val iSt = col("start_time"); val iEn = col("end_time"); val iOff = col("time_offset")
        if (iSt < 0 || iEn < 0) return Result(emptyList(), "sleep", rows.size)
        var skipped = 0
        val perDay = HashMap<Long, Int>()
        for (r in rows) {
            val c = r.split(',')
            val st = parseUtc(c.getOrNull(iSt), c.getOrNull(iOff))
            val en = parseUtc(c.getOrNull(iEn), c.getOrNull(iOff))
            val mins = if (st != null && en != null) ((en - st) / 60_000).toInt() else null
            if (st == null || mins == null || mins !in 10..1200) { skipped++; continue }
            val day = st / DAY * DAY
            perDay[day] = (perDay[day] ?: 0) + mins
        }
        return Result(perDay.map { sleep(it.key, it.value) }, "sleep", skipped)
    }

    /** Parses Samsung's UTC timestamp string, shifted by the record's own offset so days bucket in *your* local wall time. */
    private fun parseUtc(s: String?, offset: String?): Long? {
        val v = s?.trim().orEmpty()
        if (v.isEmpty()) return null
        if (v.all { it.isDigit() } && v.length >= 12) return v.toLong()
        val fmt = if (v.length > 19) FMT_MS else FMT_S
        val base = runCatching { synchronized(fmt) { fmt.parse(v.take(23))!!.time } }.getOrNull() ?: return null
        val m = OFFSET.find(offset?.trim().orEmpty())
        val shift = m?.let {
            val sign = if (it.groupValues[1] == "+") 1 else -1
            sign * (it.groupValues[2].toInt() * 3_600_000L + it.groupValues[3].toInt() * 60_000L)
        } ?: 0L
        return base + shift
    }

    /** Generic: daily average of a numeric column (stress score, weight). */
    private fun parseDailyAvg(
        rows: List<String>, col: (String) -> Int, valueName: String, kind: String,
        make: (Long, Double) -> DailySummary,
    ): Result {
        val iV = col(valueName); val iT = col("start_time"); val iOff = col("time_offset")
        if (iV < 0 || iT < 0) return Result(emptyList(), kind, rows.size)
        var skipped = 0
        val sums = HashMap<Long, Pair<Double, Int>>()
        for (r in rows) {
            val c = r.split(',')
            val v = c.getOrNull(iV)?.trim()?.toDoubleOrNull()
            val t = parseUtc(c.getOrNull(iT), c.getOrNull(iOff))
            if (v == null || v <= 0.0 || t == null) { skipped++; continue }
            val day = t / DAY * DAY
            val (sum, n) = sums[day] ?: (0.0 to 0)
            sums[day] = (sum + v) to (n + 1)
        }
        return Result(sums.map { (day, p) -> make(day, p.first / p.second) }, kind, skipped)
    }

    private fun parseExercise(rows: List<String>, col: (String) -> Int): Result {
        val iDur = col("duration"); val iT = col("start_time"); val iOff = col("time_offset")
        if (iDur < 0 || iT < 0) return Result(emptyList(), "exercise", rows.size)
        var skipped = 0
        val perDay = HashMap<Long, Int>()
        for (r in rows) {
            val c = r.split(',')
            val mins = c.getOrNull(iDur)?.trim()?.toDoubleOrNull()?.let { (it / 60_000).toInt() }
            val t = parseUtc(c.getOrNull(iT), c.getOrNull(iOff))
            if (mins == null || mins !in 1..600 || t == null) { skipped++; continue }
            val day = t / DAY * DAY
            perDay[day] = (perDay[day] ?: 0) + mins
        }
        return Result(perDay.map { DailySummary(it.key, null, null, null, null, null, null, null, null, exerciseMin = it.value) }, "exercise", skipped)
    }

    private fun parseEcg(rows: List<String>, col: (String) -> Int): Result {
        val iT = col("start_time"); val iOff = col("time_offset")
        if (iT < 0) return Result(emptyList(), "ECG sessions", rows.size)
        var skipped = 0
        val perDay = HashMap<Long, Int>()
        for (r in rows) {
            val c = r.split(',')
            val t = parseUtc(c.getOrNull(iT), c.getOrNull(iOff)) ?: run { skipped++; null } ?: continue
            val day = t / DAY * DAY
            perDay[day] = (perDay[day] ?: 0) + 1
        }
        return Result(perDay.map { DailySummary(it.key, null, null, null, null, null, null, null, null, ecgCount = it.value) }, "ECG sessions", skipped)
    }

    private val OFFSET = Regex("UTC([+-])(\\d{2}):?(\\d{2})")
    private val FMT_MS = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    private val FMT_S = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private fun steps(day: Long, v: Int) = DailySummary(day, null, null, null, null, null, null, null, v)
    private fun rhr(day: Long, v: Int) = DailySummary(day, null, null, null, null, v, null, null, null)
    private fun sleep(day: Long, v: Int) = DailySummary(day, null, null, null, null, null, null, v, null)
}
