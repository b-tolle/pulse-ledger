package com.example.pulseledger.ui

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.pulseledger.data.db.LocationDay
import org.json.JSONArray
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private fun initOsm(ctx: Context) {
    Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osm", Context.MODE_PRIVATE))
    Configuration.getInstance().userAgentValue = "PulseLedger"
}

/** Places from one or more LocationDays, drawn as dots on a real street map. */
@Composable
fun PlacesMap(days: List<LocationDay>, heightDp: Int = 260) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { initOsm(ctx) }

    val points = remember(days) {
        val out = ArrayList<Triple<Double, Double, String>>()
        for (d in days) {
            val arr = runCatching { JSONArray(d.placesJson) }.getOrNull() ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += Triple(o.getDouble("lat"), o.getDouble("lng"), o.optString("label", "Place"))
            }
        }
        out
    }

    AndroidView(
        modifier = Modifier.fillMaxWidth().height(heightDp.dp),
        factory = { c ->
            MapView(c).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                setUseDataConnection(true)
            }
        },
        update = { map ->
            map.overlays.clear()
            if (points.isEmpty()) return@AndroidView
            var minLat = 90.0; var maxLat = -90.0; var minLng = 180.0; var maxLng = -180.0
            points.forEach { (lat, lng, label) ->
                Marker(map).apply {
                    position = GeoPoint(lat, lng)
                    title = label
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    map.overlays.add(this)
                }
                minLat = minOf(minLat, lat); maxLat = maxOf(maxLat, lat)
                minLng = minOf(minLng, lng); maxLng = maxOf(maxLng, lng)
            }
            map.post {
                runCatching {
                    map.zoomToBoundingBox(
                        BoundingBox(maxLat + 0.01, maxLng + 0.01, minLat - 0.01, minLng - 0.01),
                        false, 60,
                    )
                }
            }
            map.invalidate()
        },
    )
}
