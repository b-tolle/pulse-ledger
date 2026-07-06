package com.example.pulseledger.backfill

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

/**
 * Backfills YEARS of history from a Google Takeout archive, bypassing
 * Health Connect's 30-day history window for newly-installed apps.
 *
 * Two shapes are supported:
 *
 * 1. Google Fit ("Fit" folder): "Daily activity metrics" CSVs and
 *    raw_com.google.step_count.delta_*.json files with
 *    { "Data Points": [ { startTimeNanos, endTimeNanos, fitValue: [{value:{intVal}}] } ] }
 *
 * 2. Fitbit / Google Health ("Fitbit" folder): Physical Activity/steps-YYYY-MM-DD.json
 *    arrays of { "dateTime": "MM/DD/YY HH:mm:ss", "value": "123" }
 *
 * Everything lands in the local Room DB as day-level step totals.
 */
object TakeoutImporter {

    data class DaySteps(val dayEpoch: Long, val steps: Int)

    fun parseFitStepsJson(stream: InputStream): List<DaySteps> {
        val root = JSONObject(stream.bufferedReader().readText())
        val points = root.optJSONArray("Data Points") ?: return emptyList()
        val perDay = HashMap<Long, Int>()
        for (i in 0 until points.length()) {
            val p = points.getJSONObject(i)
            val startMs = p.getLong("startTimeNanos") / 1_000_000
            val day = startMs - startMs % 86_400_000
            val v = p.getJSONArray("fitValue").getJSONObject(0)
                .getJSONObject("value").optInt("intVal", 0)
            perDay.merge(day, v, Int::plus)
        }
        return perDay.map { DaySteps(it.key, it.value) }
    }

    fun parseFitbitStepsJson(stream: InputStream): List<DaySteps> {
        val arr = JSONArray(stream.bufferedReader().readText())
        val perDay = HashMap<Long, Int>()
        val fmt = java.text.SimpleDateFormat("MM/dd/yy HH:mm:ss", java.util.Locale.US)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val t = fmt.parse(o.getString("dateTime"))!!.time
            val day = t - t % 86_400_000
            perDay.merge(day, o.getString("value").toInt(), Int::plus)
        }
        return perDay.map { DaySteps(it.key, it.value) }
    }
}
