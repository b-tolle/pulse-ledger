package com.example.pulseledger.backfill

import com.example.pulseledger.data.db.LocationDay
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import kotlin.math.*

/**
 * Imports Google's on-device Timeline.json (the current format, verified
 * against a real 14-year, 102 MB export).
 *
 * Structure: { "semanticSegments": [ segment, ... ] } where each segment has
 * startTime/endTime plus one of:
 *   - "visit":   { topCandidate: { semanticType: HOME|WORK|..., placeLocation: { latLng: "36.18°, -96.09°" } } }
 *   - "activity":{ distanceMeters, start/end latLng }
 *   - "timelinePath": [ { point: "lat°, lng°", time } ]
 *
 * Output: one LocationDay per day with total distance + labeled stay-places.
 */
object LocationImporter {

    fun parse(stream: InputStream): List<LocationDay> {
        // Stream the (large) file token-light: read fully but parse once.
        val root = runCatching { JSONObject(stream.bufferedReader().readText()) }.getOrNull() ?: return emptyList()
        val segs = root.optJSONArray("semanticSegments") ?: return emptyList()
        val dayMs = 86_400_000L

        data class Acc(var dist: Double = 0.0, var points: Int = 0, val visits: ArrayList<JSONObject> = ArrayList())
        val byDay = HashMap<Long, Acc>()
        fun accFor(iso: String): Acc? {
            val ms = parseIso(iso) ?: return null
            return byDay.getOrPut(ms / dayMs * dayMs) { Acc() }
        }

        for (i in 0 until segs.length()) {
            val s = segs.optJSONObject(i) ?: continue
            val acc = accFor(s.optString("startTime")) ?: continue

            s.optJSONObject("activity")?.let { acc.dist += it.optDouble("distanceMeters", 0.0) }

            s.optJSONObject("visit")?.optJSONObject("topCandidate")?.let { tc ->
                val ll = parseLatLng(tc.optJSONObject("placeLocation")?.optString("latLng"))
                if (ll != null) {
                    val start = parseIso(s.optString("startTime")) ?: 0L
                    val end = parseIso(s.optString("endTime")) ?: start
                    val mins = ((end - start) / 60_000).toInt().coerceAtLeast(0)
                    acc.visits += JSONObject().apply {
                        put("lat", ll.first); put("lng", ll.second)
                        put("label", prettyType(tc.optString("semanticType")))
                        put("minutes", mins)
                    }
                }
            }

            s.optJSONArray("timelinePath")?.let { path ->
                var prev: Pair<Double, Double>? = null
                for (j in 0 until path.length()) {
                    val ll = parseLatLng(path.optJSONObject(j)?.optString("point")) ?: continue
                    acc.points++
                    prev?.let { acc.dist += haversine(it, ll) }
                    prev = ll
                }
            }
        }

        return byDay.map { (day, a) ->
            // keep only meaningful stays (>=15 min), largest first
            val places = a.visits
                .filter { it.optInt("minutes") >= 15 }
                .sortedByDescending { it.optInt("minutes") }
            LocationDay(day, a.dist, JSONArray(places).toString(), a.points)
        }.filter { it.distanceMeters > 0 || it.pointCount > 0 || it.placesJson != "[]" }
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
        val m = Regex("(-?\\d+\\.\\d+)\\s*°?\\s*,\\s*(-?\\d+\\.\\d+)").find(s) ?: return null
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
