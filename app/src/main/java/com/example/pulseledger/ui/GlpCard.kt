package com.example.pulseledger.ui

import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val WEEK_MS = 7L * 86_400_000L

/** Tirzepatide (GLP-1) tracker: shot log with 7-day cadence, due countdown,
 *  one-tap calendar reminder, and a daily appetite check-in. Self-gating:
 *  shows a small opt-in card until the first shot is logged. */
@Composable
fun GlpTracker(ui: DashboardViewModel.Ui, vm: DashboardViewModel) {
    var showDose by remember { mutableStateOf(false) }
    val last = ui.shots.firstOrNull()

    if (showDose) DoseDialog(
        defaultUnits = last?.doseUnits ?: 40.0,
        onDismiss = { showDose = false },
        onSave = { units -> showDose = false; vm.addShot(units, "Tirzepatide") },
    )

    if (last == null) {
        Card {
            SectionLabel("MEDICATION")
            Spacer(Modifier.height(6.dp))
            Text("Track weekly GLP-1 shots (tirzepatide)? Log your first shot and the app will show when the next one is due.",
                color = PL.Soft, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { showDose = true }, modifier = Modifier.fillMaxWidth()) {
                Text("I took my shot today", color = PL.Charge)
            }
        }
        return
    }

    val ctx = LocalContext.current
    val dueMs = last.epochMillis + WEEK_MS
    val daysLeft = ((dueMs - System.currentTimeMillis()) / 86_400_000.0)
    val (dueText, dueColor) = when {
        daysLeft > 1.0 -> "due in ${daysLeft.toInt() + 1} days" to PL.Charge
        daysLeft > 0.0 -> "due today" to PL.Drain
        else -> "overdue by ${(-daysLeft).toInt() + 1} day${if (-daysLeft >= 1) "s" else ""}" to PL.Sys
    }
    val fmt = DateTimeFormatter.ofPattern("EEE, MMM d").withZone(ZoneId.systemDefault())

    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("TIRZEPATIDE", Modifier.weight(1f))
            Text(dueText, color = dueColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Text("Last shot", color = PL.Soft, fontSize = 12.sp)
                Text(fmt.format(Instant.ofEpochMilli(last.epochMillis)), color = PL.Txt,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("%.0f units".format(last.doseUnits), color = PL.Dim, fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace)
            }
            Column(Modifier.weight(1f)) {
                Text("Next due", color = PL.Soft, fontSize = 12.sp)
                Text(fmt.format(Instant.ofEpochMilli(dueMs)), color = dueColor,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { showDose = true }, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = PL.Charge),
            ) { Text("Took my shot", color = PL.Bg, fontSize = 13.sp) }
            OutlinedButton(
                onClick = {
                    val begin = Instant.ofEpochMilli(dueMs).atZone(ZoneId.systemDefault())
                        .toLocalDate().atTime(9, 0).atZone(ZoneId.systemDefault())
                        .toInstant().toEpochMilli()
                    val i = Intent(Intent.ACTION_INSERT).apply {
                        data = CalendarContract.Events.CONTENT_URI
                        putExtra(CalendarContract.Events.TITLE, "Tirzepatide shot")
                        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 30 * 60_000)
                    }
                    runCatching { ctx.startActivity(i) }
                },
                modifier = Modifier.weight(1f),
            ) { Text("Remind me", color = PL.Soft, fontSize = 13.sp) }
        }

        // Daily appetite check-in
        Spacer(Modifier.height(14.dp))
        Text("HOW HUNGRY TODAY?", color = PL.Soft, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(6.dp))
        val todayLevel = ui.hungerWeek.lastOrNull()
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { lvl ->
                FilterChip(
                    selected = todayLevel?.toInt() == lvl,
                    onClick = { vm.logHunger(lvl) },
                    label = { Text("$lvl", fontSize = 13.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PL.CardUp, selectedLabelColor = PL.Txt,
                        containerColor = PL.Card, labelColor = PL.Dim),
                )
            }
        }
        Text("1 = not hungry · 5 = very hungry", color = PL.Dim, fontSize = 10.sp,
            modifier = Modifier.padding(top = 4.dp))
        if (ui.hungerWeek.count { it != null } >= 2) {
            Spacer(Modifier.height(8.dp))
            WeekBars(ui.hungerWeek, PL.Drain, 36, labels = null)
        }
        Spacer(Modifier.height(8.dp))
        Text("Records what you enter — confirm dose and schedule with your prescriber.",
            color = PL.Dim, fontSize = 10.sp)
    }
}

@Composable
private fun DoseDialog(defaultUnits: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var units by remember { mutableStateOf("%.0f".format(defaultUnits)) }
    val v = units.toDoubleOrNull()
    val valid = v != null && v in 1.0..200.0
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = PL.CardUp,
        title = { Text("Log shot", color = PL.Txt) },
        text = {
            Column {
                OutlinedTextField(units, { units = it.filter(Char::isDigit).take(3) },
                    label = { Text("Dose (units)") }, singleLine = true)
                Text("Defaults to your last dose. Next shot will show due 1 week from now.",
                    color = PL.Soft, fontSize = 11.5.sp, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(v!!) }) {
                Text("Save", color = if (valid) PL.Charge else PL.Soft)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = PL.Soft) } },
    )
}
