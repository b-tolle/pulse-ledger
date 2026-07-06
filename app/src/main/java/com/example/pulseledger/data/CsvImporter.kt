package com.example.pulseledger.data

import com.example.pulseledger.data.db.BpReading
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Tolerant blood-pressure CSV importer. Works with Omron Connect exports and
 * most other apps: finds the systolic/diastolic/pulse/date columns by header
 * name, and tries several common date formats.
 */
object CsvImporter {

    private val dateFormats = listOf(
        "yyyy-MM-dd HH:mm", "yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd HH:mm",
        "MM/dd/yyyy HH:mm", "MM/dd/yyyy hh:mm a", "dd/MM/yyyy HH:mm",
        "MMM d, yyyy h:mm a", "yyyy-MM-dd'T'HH:mm:ss",
    ).map { SimpleDateFormat(it, Locale.US).apply { isLenient = true } }

    data class Result(val readings: List<BpReading>, val skipped: Int)

    fun parse(stream: InputStream): Result {
        val lines = stream.bufferedReader().readLines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return Result(emptyList(), 0)

        val delim = if (lines[0].count { it == ';' } > lines[0].count { it == ',' }) ';' else ','
        val header = split(lines[0], delim).map { it.lowercase() }

        fun col(vararg keys: String): Int =
            header.indexOfFirst { h -> keys.any { h.contains(it) } }

        val iSys = col("sys")
        val iDia = col("dia")
        val iPulse = col("pulse", "bpm", "heart")
        val iDate = col("date", "measure", "time")
        if (iSys < 0 || iDia < 0) return Result(emptyList(), lines.size - 1)

        var skipped = 0
        val out = ArrayList<BpReading>()
        for (line in lines.drop(1)) {
            val c = split(line, delim)
            val sys = c.getOrNull(iSys)?.filter { it.isDigit() }?.toIntOrNull()
            val dia = c.getOrNull(iDia)?.filter { it.isDigit() }?.toIntOrNull()
            if (sys == null || dia == null || sys < 60 || sys > 260 || dia < 30 || dia > 200) { skipped++; continue }

            // Date may be one column or date+time in adjacent columns
            val dateStr = buildString {
                append(c.getOrNull(iDate).orEmpty().trim())
                val next = c.getOrNull(iDate + 1).orEmpty().trim()
                if (next.matches(Regex("^\\d{1,2}:\\d{2}.*"))) { append(' '); append(next) }
            }
            val epoch = parseDate(dateStr) ?: run { skipped++; null } ?: continue

            out += BpReading(
                epochMillis = epoch,
                systolic = sys, diastolic = dia,
                pulse = c.getOrNull(iPulse)?.filter { it.isDigit() }?.toIntOrNull(),
                source = "csv_import",
            )
        }
        return Result(out, skipped)
    }

    private fun parseDate(s: String): Long? {
        if (s.isBlank()) return null
        for (f in dateFormats) runCatching { return f.parse(s)!!.time }
        return null
    }

    private fun split(line: String, delim: Char): List<String> =
        line.split(delim).map { it.trim().trim('"') }
}
