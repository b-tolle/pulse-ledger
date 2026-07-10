package com.example.pulseledger.life

import com.example.pulseledger.data.db.LocationDay
import org.json.JSONArray
import kotlin.math.*

/**
 * Learns Home/Work anchors from history. People move and change jobs, so we
 * must NOT average all Home/Work coords together (that lands the pin between
 * two houses). Instead we cluster distinct sites (~150m) and pick the one with
 * the most RECENT activity as the current anchor.
 */
object Places {

    data class Anchor(val label: String, val lat: Double, val lng: Double, val visits: Int)

    private class Cluster(val lat: Double, val lng: Double) {
        var count = 0; var recent = 0; var sumLat = 0.0; var sumLng = 0.0
        fun center() = Anchor("", sumLat / count, sumLng / count, count)
    }

    fun learnAnchors(days: List<LocationDay>): List<Anchor> {
        val nowDay = System.currentTimeMillis()
        val recentCutoff = nowDay - 365L * 86_400_000L   // "recent" = last 12 months

        val out = ArrayList<Anchor>()
        for (label in listOf("Home", "Work")) {
            val clusters = ArrayList<Cluster>()
            for (d in days) {
                val arr = runCatching { JSONArray(d.placesJson) }.getOrNull() ?: continue
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (o.optString("label") != label) continue
                    val lat = o.getDouble("lat"); val lng = o.getDouble("lng")
                    val c = clusters.firstOrNull { haversine(it.lat, it.lng, lat, lng) < 150 }
                        ?: Cluster(lat, lng).also { clusters.add(it) }
                    c.count++; c.sumLat += lat; c.sumLng += lng
                    if (d.dayEpoch >= recentCutoff) c.recent++
                }
            }
            // prefer the cluster with the most recent visits; fall back to most total
            val best = clusters.maxWithOrNull(
                compareBy({ it.recent }, { it.count })
            ) ?: continue
            val ctr = best.center()
            out.add(Anchor(label, ctr.lat, ctr.lng, best.count))
        }
        return out
    }

    /** Nearest anchor within ~250m, else null. */
    fun classify(lat: Double, lng: Double, anchors: List<Anchor>): Anchor? =
        anchors.minByOrNull { haversine(lat, lng, it.lat, it.lng) }
            ?.takeIf { haversine(lat, lng, it.lat, it.lng) < 250 }

    fun haversine(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val R = 6_371_000.0
        val dLat = Math.toRadians(bLat - aLat); val dLng = Math.toRadians(bLng - aLng)
        val x = sin(dLat / 2).pow(2) + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLng / 2).pow(2)
        return R * 2 * atan2(sqrt(x), sqrt(1 - x))
    }
}
