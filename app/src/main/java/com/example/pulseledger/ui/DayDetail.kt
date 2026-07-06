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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailSheet(day: DailySummary, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(day.dayEpoch).atZone(zone).toLocalDate()
    val titleFmt = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PL.Card) {
        Column(Modifier.fillMaxWidth().padding(20.dp).padding(bottom = 24.dp)) {
            Text(date.format(titleFmt), color = PL.Txt, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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

            Spacer(Modifier.height(20.dp))

            // "Where was I?" — deep link into Google Maps Timeline for this exact date
            Button(
                onClick = {
                    val d = "%04d/%02d/%02d".format(date.year, date.monthValue, date.dayOfMonth)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://timeline.google.com/maps/timeline?pb=!1m2!1m1!1s$d"))
                    runCatching { ctx.startActivity(intent) }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PL.CardUp),
            ) {
                Text("📍  Where was I this day?", color = PL.Txt, fontSize = 14.sp)
            }
            Text(
                "Opens this date in your Google Maps Timeline (your location history stays in your Google account — Pulse Ledger never sees it).",
                color = PL.Dim, fontSize = 10.5.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
