package com.example.pulseledger.data

import com.example.pulseledger.data.db.BpReading
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Blood-pressure CSV importer. Header-driven column detection, STRICT
 * java.time date parsing (full-string match, no lenient prefix nonsense —
 * SimpleDateFormat once parsed "05/24/2026 5:30 AM" as year 12 AD), and a
 * sane-year guard so nothing can ever be imported into antiquity again.
 */
object CsvImporter {

    private val dateTimeFormats = listOf(
        "M/d/yyyy h:mm a", "M/d/yyyy H:mm", "M/d/yyyy h:mm:ss a",
        "yyyy-M-d H:mm", "yyyy-M-d H:mm:ss", "yyyy/M/d H:mm",
        "MMM d, yyyy h:mm a", "yyyy-MM-dd'T'HH:mm:ss",
    ).map { DateTimeFormatter.ofPattern(it, Locale.US) }

    private val dateOnlyFormats = listOf(
        "M/d/yyyy", "yyyy-M-d", "yyyy/M/d", "MMM d, yyyy",
    ).map { DateTimeFormatter.ofPattern(it, Locale.US) }

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

            val dateStr = buildString {
                append(c.getOrNull(iDate).orEmpty().trim())
                val next = c.getOrNull(iDate + 1).orEmpty().trim()
                if (next.matches(Regex("^\\d{1,2}:\\d{2}.*"))) { append(' '); append(next) }
            }
            val epoch = parseDate(dateStr)
            if (epoch == null) { skipped++; continue }

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
        val zone = ZoneId.systemDefault()
        for (f in dateTimeFormats) {
            runCatching {
                return validated(LocalDateTime.parse(s, f).atZone(zone).toInstant().toEpochMilli())
            }
        }
        for (f in dateOnlyFormats) {
            runCatching {
                return validated(LocalDate.parse(s, f).atTime(12, 0).atZone(zone).toInstant().toEpochMilli())
            }
        }
        return null
    }

    /** Only accept 2000-01-01 .. now+2 days. */
    private fun validated(epoch: Long): Long? {
        val min = 946_684_800_000L
        val max = System.currentTimeMillis() + 2 * 86_400_000L
        return if (epoch in min..max) epoch else null
    }

    private fun split(line: String, delim: Char): List<String> =
        line.split(delim).map { it.trim().trim('"') }
}
