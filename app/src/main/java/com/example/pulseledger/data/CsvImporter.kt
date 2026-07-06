package com.example.pulseledger.data

import com.example.pulseledger.data.db.BpReading
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Fallback if the Omron app doesn't write to Health Connect on your phone:
 * Omron Connect can export History as CSV; share that file to this app.
 * Adjust the column indices/date format to match your export locale.
 */
object CsvImporter {
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun parse(stream: InputStream): List<BpReading> =
        stream.bufferedReader().readLines()
            .drop(1) // header
            .mapNotNull { line ->
                val c = line.split(',').map { it.trim('"', ' ') }
                runCatching {
                    BpReading(
                        epochMillis = LocalDateTime.parse("${c[0]} ${c[1]}", fmt)
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        systolic = c[2].toInt(),
                        diastolic = c[3].toInt(),
                        pulse = c.getOrNull(4)?.toIntOrNull(),
                        source = "csv_import",
                    )
                }.getOrNull()
            }
}
