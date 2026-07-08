package com.example.pulseledger.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    val titleFmt = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
    val dow = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PL.Card) {
        Column(Modifier.fillMaxWidth().padding(20.dp).padding(bottom = 24.dp)) {
            Text(date.format(titleFmt), color = PL.Txt, fontSize = 18.sp, fontWeight = FontWeight.Bold)

            // quick narrative line
            val note = buildString {
                day.steps?.let { append("%,d steps".format(it)) }
                day.exerciseMin?.let { if (isNotEmpty()) append(" · "); append("${it}m active") }
                day.sleepMinutes?.let { if (isNotEmpty()) append(" · "); append("slept %dh%02dm".format(it/60, it%60)) }
            }
            if (note.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(note, color = PL.Soft, fontSize = 13.sp)
            }
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
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(label, color = PL.Soft, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(value, color = PL.Txt, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                    if (extra.isNotEmpty()) Text("  $extra", color = PL.Dim, fontSize = 12.sp)
                }
                HorizontalDivider(color = PL.Line)
            }

            if (location != null && location.placesJson != "[]") {
                Spacer(Modifier.height(16.dp))
                Text("WHERE YOU WERE", color = PL.Soft, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(8.dp))
                PlacesMap(listOf(location), heightDp = 220)
                Spacer(Modifier.height(8.dp))
                val arr = runCatching { JSONArray(location.placesJson) }.getOrNull()
                if (arr != null) for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val mins = o.optInt("minutes")
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(o.optString("label", "Place"), color = PL.Txt, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(if (mins >= 60) "%dh %02dm".format(mins/60, mins%60) else "${mins}m",
                            color = PL.Soft, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                if (location.distanceMeters > 100) {
                    Text("Traveled ≈ %.1f mi".format(location.distanceMeters / 1609.34),
                        color = PL.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("SEE THIS DAY ELSEWHERE", color = PL.Soft, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(10.dp))

            // Timeline: Google removed date-specific deep links (Timeline is on-device now),
            // so we open Timeline and tell the user which date to scroll to.
            LinkButton("🗺  Open Maps Timeline") {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/timeline"))
                    .setPackage("com.google.android.apps.maps")
                runCatching { ctx.startActivity(i) }.onFailure {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://timeline.google.com"))) }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Samsung Health opens to its history where this day's detail lives
            LinkButton("💜  Open Samsung Health") {
                val i = ctx.packageManager.getLaunchIntentForPackage("com.sec.android.app.shealth")
                if (i != null) runCatching { ctx.startActivity(i) }
            }
            Spacer(Modifier.height(10.dp))
            Text("Scroll to $dow, ${date.format(DateTimeFormatter.ofPattern("MMM d"))} — Google moved Timeline to on-device only, so apps can't jump straight to a date anymore.",
                color = PL.Dim, fontSize = 10.5.sp, lineHeight = 15.sp)
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
