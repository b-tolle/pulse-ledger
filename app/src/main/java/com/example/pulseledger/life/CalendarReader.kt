package com.example.pulseledger.life

import android.content.Context
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads today's calendar events (READ_CALENDAR permission) purely to
 * contextualize your day — "4 meetings, 5.5 busy hours". Nothing is stored
 * beyond counts; event titles never leave the query.
 */
object CalendarReader {

    data class DayLoad(val eventCount: Int, val busyMinutes: Int, val backToBack: Int)

    fun today(context: Context): DayLoad? {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val proj = arrayOf(CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY)
        val sel = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ?"
        val cursor = try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI, proj,
                sel, arrayOf(start.toString(), end.toString()),
                "${CalendarContract.Events.DTSTART} ASC",
            )
        } catch (t: SecurityException) { return null } ?: return null

        var count = 0; var busy = 0; var b2b = 0; var lastEnd = 0L
        cursor.use {
            while (it.moveToNext()) {
                val s = it.getLong(0); val e = it.getLong(1); val allDay = it.getInt(2) == 1
                if (allDay || e <= s) continue
                count++
                busy += ((e - s) / 60000).toInt()
                if (lastEnd > 0 && s - lastEnd <= 10 * 60000) b2b++
                lastEnd = e
            }
        }
        return DayLoad(count, busy, b2b)
    }
}
