package com.example.pulseledger.life

import com.example.pulseledger.data.db.LocationDay
import org.json.JSONArray
import kotlin.math.*

/**
 * Learns your Home and Work anchor coordinates from imported history
 * (Google already labeled thousands of Home/Work visits), then classifies
 * a live GPS fix into the nearest known place.
 */
object Places {

    data class Anchor(val label: String, val lat: Double, val lng: Double, val visits: Int)

    /** Cluster all labeled visits; return the dominant coordinate per label. */
    fun learnAnchors(days: List<LocationDay>): List<Anchor> {
        data class Agg(var lat: Double = 0.0, var lng: Double = 0.0, var n: Int = 0)
        val byLabel = HashMap<String, Agg>()
        for (d in days) {
            val arr = runCatching { JSONArray(d.placesJson) }.getOrNull() ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val label = o.optString("label")
                if (label != "Home" && label != "Work") continue
                val a = byLabel.getOrPut(label) { Agg() }
                a.lat += o.getDouble("lat"); a.lng += o.getDouble("lng"); a.n++
            }
        }
        return byLabel.map { (label, a) -> Anchor(label, a.lat / a.n, a.lng / a.n, a.n) }
            .sortedByDescending { it.visits }
    }

    /** Nearest anchor within ~250m, else null (= "Out and about"). */
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
