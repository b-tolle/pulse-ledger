package com.example.pulseledger.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pulseledger.data.db.DailySummary
import com.example.pulseledger.data.db.LocationDay
import org.json.JSONArray
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailSheet(day: DailySummary, location: LocationDay? = null, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(day.dayEpoch).atZone(zone).toLocalDate()

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PL.Card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 28.dp),
        ) {
            Text(date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")),
                color = PL.Txt, fontSize = 18.sp, fontWeight = FontWeight.Bold)

            val note = buildString {
                day.steps?.let { append("%,d steps".format(it)) }
                day.exerciseMin?.let { if (isNotEmpty()) append(" · "); append("${it}m active") }
                day.sleepMinutes?.let { if (isNotEmpty()) append(" · "); append("slept %dh%02dm".format(it/60, it%60)) }
            }
            if (note.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(note, color = PL.Soft, fontSize = 13.sp)
            }

            // ── Health metrics ──
            Spacer(Modifier.height(16.dp))
            val rows = buildList {
                day.steps?.let { add(Triple("Steps", "%,d".format(it), "≈ %.1f mi".format(it / 2100.0))) }
                day.restingHr?.let { add(Triple("Resting HR", "$it bpm", "")) }
                day.sleepMinutes?.let { add(Triple("Sleep", "%dh %02dm".format(it / 60, it % 60), "")) }
                day.stressAvg?.let { add(Triple("Stress", "%.0f".format(it), "avg")) }
                day.exerciseMin?.let { add(Triple("Exercise", "$it min", "")) }
                day.weightKg?.let { add(Triple("Weight", "%.1f lb".format(it * 2.20462), "")) }
                day.ecgCount?.let { add(Triple("ECG", "$it recording${if (it > 1) "s" else ""}", "")) }
            }
            rows.forEach { (label, value, extra) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                    Text(label, color = PL.Soft, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(value, color = PL.Txt, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                    if (extra.isNotEmpty()) Text("  $extra", color = PL.Dim, fontSize = 12.sp)
                }
                HorizontalDivider(color = PL.Line)
            }

            // ── Location: fixed compact map, then clean list ──
            val places = location?.let { runCatching { JSONArray(it.placesJson) }.getOrNull() }
            if (location != null && places != null && places.length() > 0) {
                Spacer(Modifier.height(18.dp))
                Text("WHERE YOU WERE", color = PL.Soft, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.clip(RoundedCornerShape(14.dp))) {
                    PlacesMap(listOf(location), heightDp = 200)
                }
                Spacer(Modifier.height(12.dp))
                for (i in 0 until places.length()) {
                    val o = places.getJSONObject(i)
                    val mins = o.optInt("minutes")
                    val label = o.optString("label", "Place")
                    val dotColor = when (label) {
                        "Home" -> PL.Charge; "Work" -> PL.Dia
                        "Visited" -> PL.Drain; "Saved place" -> PL.Gold
                        else -> PL.Sleep
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).then(Modifier).background(dotColor))
                        Spacer(Modifier.width(10.dp))
                        Text(label, color = PL.Txt, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(if (mins >= 60) "%dh %02dm".format(mins / 60, mins % 60) else "${mins}m",
                            color = PL.Soft, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                if (location.distanceMeters > 100) {
                    Spacer(Modifier.height(4.dp))
                    Text("Traveled ≈ %.1f mi that day".format(location.distanceMeters / 1609.34),
                        color = PL.Dim, fontSize = 12.sp)
                }
            }

            // ── External links ──
            Spacer(Modifier.height(20.dp))
            LinkButton("🗺  Open Maps Timeline") {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/timeline"))
                    .setPackage("com.google.android.apps.maps")
                runCatching { ctx.startActivity(i) }.onFailure {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://timeline.google.com"))) }
                }
            }
            Spacer(Modifier.height(8.dp))
            LinkButton("💜  Open Samsung Health") {
                ctx.packageManager.getLaunchIntentForPackage("com.sec.android.app.shealth")
                    ?.let { runCatching { ctx.startActivity(it) } }
            }
        }
    }
}

@Composable
private fun LinkButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = PL.CardUp)) {
        Text(label, color = PL.Txt, fontSize = 14.sp)
    }
}
