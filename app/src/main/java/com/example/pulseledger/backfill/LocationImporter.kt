package com.example.pulseledger.backfill

import com.example.pulseledger.data.db.LocationDay
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import kotlin.math.*

/**
 * Imports Google Takeout Location History into per-day rollups.
 *
 * Google has shipped several shapes over the years; we detect and handle:
 *  1. Records.json  → { "locations": [ { timestampMs | timestamp, latitudeE7, longitudeE7 } ] }
 *  2. Semantic "timeline" per-month JSON → { "timelineObjects": [ { placeVisit | activitySegment } ] }
 *  3. New on-device export "Timeline.json" → { "semanticSegments": [ ... ] }
 *
 * Output: one LocationDay per day with total distance + clustered stay-places.
 */
object LocationImporter {

    data class Pt(val t: Long, val lat: Double, val lng: Double)

    fun parse(stream: InputStream): List<LocationDay> {
        val text = stream.bufferedReader().readText()
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return emptyList()
        val pts = when {
            root.has("locations") -> fromRecords(root.getJSONArray("locations"))
            root.has("timelineObjects") -> fromSemantic(root.getJSONArray("timelineObjects"))
            root.has("semanticSegments") -> fromNewTimeline(root.getJSONArray("semanticSegments"))
            else -> emptyList()
        }
        return rollup(pts)
    }

    private fun fromRecords(arr: JSONArray): List<Pt> {
        val out = ArrayList<Pt>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val t = o.optLong("timestampMs", 0L).takeIf { it > 0 }
                ?: parseIso(o.optString("timestamp")) ?: continue
            val lat = o.optLong("latitudeE7", Long.MIN_VALUE)
            val lng = o.optLong("longitudeE7", Long.MIN_VALUE)
            if (lat == Long.MIN_VALUE || lng == Long.MIN_VALUE) continue
            out += Pt(t, lat / 1e7, lng / 1e7)
        }
        return out
    }

    private fun fromSemantic(arr: JSONArray): List<Pt> {
        val out = ArrayList<Pt>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            o.optJSONObject("placeVisit")?.optJSONObject("location")?.let { loc ->
                val lat = loc.optLong("latitudeE7", Long.MIN_VALUE)
                val lng = loc.optLong("longitudeE7", Long.MIN_VALUE)
                val t = o.getJSONObject("placeVisit").optJSONObject("duration")
                    ?.let { parseIso(it.optString("startTimestamp")) } ?: 0L
                if (lat != Long.MIN_VALUE && t > 0) out += Pt(t, lat / 1e7, lng / 1e7)
            }
        }
        return out
    }

    private fun fromNewTimeline(arr: JSONArray): List<Pt> {
        val out = ArrayList<Pt>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val t = parseIso(o.optString("startTime")) ?: continue
            val visit = o.optJSONObject("visit")?.optJSONObject("topCandidate")
            val latlng = visit?.optString("placeLocation") ?: o.optString("position")
            parseLatLng(latlng)?.let { (la, ln) -> out += Pt(t, la, ln) }
        }
        return out
    }

    private fun parseLatLng(s: String?): Pair<Double, Double>? {
        if (s.isNullOrBlank()) return null
        val m = Regex("(-?\\d+\\.\\d+)[,\\s]+(-?\\d+\\.\\d+)").find(s) ?: return null
        return m.groupValues[1].toDouble() to m.groupValues[2].toDouble()
    }

    private fun parseIso(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return runCatching { java.time.Instant.parse(s).toEpochMilli() }.getOrNull()
    }

    private fun rollup(points: List<Pt>): List<LocationDay> {
        if (points.isEmpty()) return emptyList()
        val dayMs = 86_400_000L
        val byDay = points.sortedBy { it.t }.groupBy { it.t - it.t % dayMs }
        return byDay.map { (day, pts) ->
            var dist = 0.0
            for (i in 1 until pts.size) dist += haversine(pts[i - 1], pts[i])
            // cluster into stay-places: consecutive points within 150m
            val places = ArrayList<JSONObject>()
            var cLat = pts[0].lat; var cLng = pts[0].lng; var cStart = pts[0].t; var cCount = 1
            fun flush(endT: Long) {
                val mins = ((endT - cStart) / 60000).toInt()
                if (mins >= 15) places += JSONObject().apply {
                    put("lat", cLat / cCount); put("lng", cLng / cCount); put("minutes", mins)
                }
            }
            for (i in 1 until pts.size) {
                if (haversine(Pt(0, cLat / cCount, cLng / cCount), pts[i]) < 150) {
                    cLat += pts[i].lat; cLng += pts[i].lng; cCount++
                } else { flush(pts[i].t); cLat = pts[i].lat; cLng = pts[i].lng; cStart = pts[i].t; cCount = 1 }
            }
            flush(pts.last().t)
            LocationDay(day, dist, JSONArray(places).toString(), pts.size)
        }
    }

    private fun haversine(a: Pt, b: Pt): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(b.lat - a.lat); val dLng = Math.toRadians(b.lng - a.lng)
        val x = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        return R * 2 * atan2(sqrt(x), sqrt(1 - x))
    }
}
