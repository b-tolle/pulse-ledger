package com.example.pulseledger.backfill

import android.util.JsonReader
import android.util.JsonToken
import com.example.pulseledger.data.db.LocationDay
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.*

/**
 * STREAMING importer for Google's on-device Timeline.json.
 * The real files are 100 MB+, so we must NOT load the whole thing into memory
 * (that OOMs and silently yields nothing). We walk the JSON token-by-token
 * with android.util.JsonReader and accumulate per-day rollups on the fly.
 */
object LocationImporter {

    private const val DAY = 86_400_000L

    private class Acc {
        var dist = 0.0
        var points = 0
        val visits = ArrayList<JSONObject>()
    }

    fun parse(stream: InputStream): List<LocationDay> {
        val byDay = HashMap<Long, Acc>()
        JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.nextName() == "semanticSegments") readSegments(reader, byDay)
                else reader.skipValue()
            }
            reader.endObject()
        }
        return byDay.map { (day, a) ->
            val places = a.visits.filter { it.optInt("minutes") >= 15 }
                .sortedByDescending { it.optInt("minutes") }
            LocationDay(day, a.dist, JSONArray(places).toString(), a.points)
        }.filter { it.distanceMeters > 0 || it.pointCount > 0 || it.placesJson != "[]" }
    }

    private fun readSegments(reader: JsonReader, byDay: HashMap<Long, Acc>) {
        reader.beginArray()
        while (reader.hasNext()) readOneSegment(reader, byDay)
        reader.endArray()
    }

    private fun readOneSegment(reader: JsonReader, byDay: HashMap<Long, Acc>) {
        var startMs: Long? = null
        var endMs: Long? = null
        var activityDist = 0.0
        var visitLatLng: Pair<Double, Double>? = null
        var visitType = ""
        val pathPoints = ArrayList<Pair<Double, Double>>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "startTime" -> startMs = parseIso(reader.nextString())
                "endTime" -> endMs = parseIso(reader.nextString())
                "activity" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        if (reader.nextName() == "distanceMeters") activityDist = reader.nextDouble()
                        else reader.skipValue()
                    }
                    reader.endObject()
                }
                "visit" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        if (reader.nextName() == "topCandidate") {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "semanticType" -> visitType = reader.nextString()
                                    "placeLocation" -> {
                                        reader.beginObject()
                                        while (reader.hasNext()) {
                                            if (reader.nextName() == "latLng") visitLatLng = parseLatLng(reader.nextString())
                                            else reader.skipValue()
                                        }
                                        reader.endObject()
                                    }
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()
                        } else reader.skipValue()
                    }
                    reader.endObject()
                }
                "timelinePath" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            if (reader.nextName() == "point") parseLatLng(reader.nextString())?.let { pathPoints.add(it) }
                            else reader.skipValue()
                        }
                        reader.endObject()
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val day = (startMs ?: return) / DAY * DAY
        val acc = byDay.getOrPut(day) { Acc() }
        acc.dist += activityDist
        var prev: Pair<Double, Double>? = null
        for (p in pathPoints) {
            acc.points++
            prev?.let { acc.dist += haversine(it, p) }
            prev = p
        }
        if (visitLatLng != null) {
            val mins = if (startMs != null && endMs != null) ((endMs - startMs) / 60_000).toInt() else 0
            acc.visits.add(JSONObject().apply {
                put("lat", visitLatLng!!.first); put("lng", visitLatLng!!.second)
                put("label", prettyType(visitType)); put("minutes", mins.coerceAtLeast(0))
            })
        }
    }

    private fun prettyType(t: String): String = when (t.uppercase()) {
        "HOME", "INFERRED_HOME" -> "Home"
        "WORK", "INFERRED_WORK" -> "Work"
        "SEARCHED_ADDRESS" -> "Visited"
        "ALIASED_LOCATION" -> "Saved place"
        else -> "Place"
    }

    private fun parseLatLng(s: String?): Pair<Double, Double>? {
        if (s.isNullOrBlank()) return null
        val m = Regex("(-?\\d+\\.\\d+)\\s*\u00b0?\\s*,\\s*(-?\\d+\\.\\d+)").find(s) ?: return null
        return m.groupValues[1].toDouble() to m.groupValues[2].toDouble()
    }

    private fun parseIso(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return runCatching { java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
    }

    private fun haversine(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val R = 6_371_000.0
        val dLat = Math.toRadians(b.first - a.first); val dLng = Math.toRadians(b.second - a.second)
        val x = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.first)) * cos(Math.toRadians(b.first)) * sin(dLng / 2).pow(2)
        return R * 2 * atan2(sqrt(x), sqrt(1 - x))
    }
}
