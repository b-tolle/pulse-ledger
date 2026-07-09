package com.example.pulseledger.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
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

private fun initOsm(ctx: Context) {
    Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osm", Context.MODE_PRIVATE))
    Configuration.getInstance().userAgentValue = "PulseLedger"
}

private fun dot(color: Int): BitmapDrawable {
    val size = 36
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = AColor.WHITE; c.drawCircle(size / 2f, size / 2f, size / 2f, p)
    p.color = color; c.drawCircle(size / 2f, size / 2f, size / 2f - 4f, p)
    return BitmapDrawable(null, bmp)
}

private fun colorFor(label: String): Int = when (label) {
    "Home" -> AColor.parseColor("#3EE58A")
    "Work" -> AColor.parseColor("#5B9BFF")
    "Visited" -> AColor.parseColor("#F5A623")
    "Saved place" -> AColor.parseColor("#F0C36D")
    else -> AColor.parseColor("#9D8CFF")
}

@Composable
fun PlacesMap(days: List<LocationDay>, heightDp: Int = 220) {
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
                    icon = dot(colorFor(label))
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    map.overlays.add(this)
                }
                minLat = minOf(minLat, lat); maxLat = maxOf(maxLat, lat)
                minLng = minOf(minLng, lng); maxLng = maxOf(maxLng, lng)
            }
            map.post {
                runCatching {
                    if (points.size == 1) {
                        map.controller.setZoom(15.0)
                        map.controller.setCenter(GeoPoint(points[0].first, points[0].second))
                    } else {
                        map.zoomToBoundingBox(BoundingBox(maxLat + 0.02, maxLng + 0.02, minLat - 0.02, minLng - 0.02), false, 50)
                    }
                }
            }
            map.invalidate()
        },
    )
}
