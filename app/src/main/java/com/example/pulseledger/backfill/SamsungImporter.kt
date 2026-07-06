package com.example.pulseledger.backfill

import com.example.pulseledger.data.db.DailySummary
import java.io.InputStream

/**
 * Imports CSVs from Samsung Health's "Download personal data" export.
 *
 * Samsung's format quirks handled here:
 *  - Line 1 is a metadata line like "com.samsung.shealth.tracker.pedometer_day_summary,6,3"
 *    (real column headers are on line 2)
 *  - Timestamps are epoch milliseconds (sometimes with a separate time_offset column)
 *
 * Supported files (auto-detected from the metadata/header line):
 *  - pedometer_day_summary  → daily step totals (this is the years-of-steps one)
 *  - tracker.heart_rate     → resting-ish HR samples, folded to a daily min
 *  - sleep                  → nightly sleep duration
 */
object SamsungImporter {

    data class Result(val summaries: List<DailySummary>, val kind: String, val skipped: Int)

    fun parse(stream: InputStream): Result? {
        val lines = stream.bufferedReader().readLines().filter { it.isNotBlank() }
        if (lines.size < 2) return null
        val meta = lines[0].lowercase()
        val headerLine = if (meta.startsWith("com.samsung")) 1 else 0
        val header = lines[headerLine].split(',').map { it.trim().lowercase() }
        val rows = lines.drop(headerLine + 1)

        fun col(vararg keys: String) = header.indexOfFirst { h -> keys.any { h == it || h.contains(it) } }
        val dayMs = 86_400_000L
        fun dayOf(ms: Long) = ms - ms % dayMs

        return when {
            meta.contains("pedometer_day_summary") || (col("step_count") >= 0 && col("day_time") >= 0) -> {
                val iDay = col("day_time"); val iSteps = col("step_count"); val iSource = col("source_type")
                var skipped = 0
                // Samsung writes one row per source per day; keep the max per day (dedupes phone+watch overlap)
                val perDay = HashMap<Long, Int>()
                for (r in rows) {
                    val c = r.split(',')
                    val day = c.getOrNull(iDay)?.trim()?.toLongOrNull()?.let(::dayOf)
                    val steps = c.getOrNull(iSteps)?.trim()?.toDoubleOrNull()?.toInt()
                    if (day == null || steps == null || steps <= 0) { skipped++; continue }
                    // ignore aggregated "combined" duplicates conservatively via max()
                    perDay[day] = maxOf(perDay[day] ?: 0, steps)
                    if (iSource >= 0) Unit
                }
                Result(perDay.map { DailySummary(it.key, null, null, null, null, null, null, null, it.value) }, "steps", skipped)
            }

            meta.contains("heart_rate") || col("heart_rate") >= 0 -> {
                val iHr = col("heart_rate"); val iT = col("start_time", "create_time")
                var skipped = 0
                val perDayMin = HashMap<Long, Int>()
                for (r in rows) {
                    val c = r.split(',')
                    val t = c.getOrNull(iT)?.trim()?.toLongOrNull()?.let(::dayOf)
                    val hr = c.getOrNull(iHr)?.trim()?.toDoubleOrNull()?.toInt()
                    if (t == null || hr == null || hr < 30 || hr > 230) { skipped++; continue }
                    perDayMin[t] = minOf(perDayMin[t] ?: 999, hr)   // daily min ≈ resting proxy
                }
                Result(perDayMin.map { DailySummary(it.key, null, null, null, null, it.value, null, null, null) }, "heart rate", skipped)
            }

            meta.contains("sleep") || (col("sleep_duration") >= 0 || (col("start_time") >= 0 && col("end_time") >= 0)) -> {
                val iStart = col("start_time"); val iEnd = col("end_time"); val iDur = col("sleep_duration")
                var skipped = 0
                val perDay = HashMap<Long, Int>()
                for (r in rows) {
                    val c = r.split(',')
                    val start = c.getOrNull(iStart)?.trim()?.toLongOrNull()
                    val minutes = when {
                        iDur >= 0 -> c.getOrNull(iDur)?.trim()?.toDoubleOrNull()?.let { (it / 60_000).toInt() }
                        start != null -> c.getOrNull(iEnd)?.trim()?.toLongOrNull()?.let { ((it - start) / 60_000).toInt() }
                        else -> null
                    }
                    if (start == null || minutes == null || minutes !in 10..1200) { skipped++; continue }
                    val day = dayOf(start)
                    perDay[day] = (perDay[day] ?: 0) + minutes
                }
                Result(perDay.map { DailySummary(it.key, null, null, null, null, null, null, it.value, null) }, "sleep", skipped)
            }

            else -> null
        }
    }
}
