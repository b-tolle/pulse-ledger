package com.example.pulseledger.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@Composable
fun HomeTab(ui: DashboardViewModel.Ui, vm: DashboardViewModel, onNavigate: (Int) -> Unit = {}) {
    val ctx = LocalContext.current
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) vm.load() }
    val hasLoc = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    LaunchedEffect(Unit) { if (!hasLoc) locPerm.launch(Manifest.permission.ACCESS_FINE_LOCATION) }

    val charge = remember(ui.summaries, ui.stepsToday, ui.calendar, ui.hrvLatest) {
        computeCharge(ui.summaries, ui.stepsToday, ui.calendar?.eventCount, ui.calendar?.busyMinutes, ui.hrvLatest)
    }
    val stepWeek = remember(ui.summaries) { vm.weekly { it.steps?.toDouble() } }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp),
    ) {
        item { AppHeader(ui, vm) }
        item { Greeting(ui) }

        // Charge hero
        item {
            Card {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ChargeGauge(charge.value)
                    Spacer(Modifier.height(6.dp))
                    charge.contributors.forEach { (what, delta) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(42.dp)) {
                                Text(if (delta > 0) "+$delta" else "$delta",
                                    color = if (delta > 0) PL.Charge else PL.Drain, fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold)
                            }
                            Text(what, color = PL.Txt, fontSize = 13.sp)
                        }
                    }
                    if (ui.hrvLatest == null) Text("HRV joins the formula after your first tracked night",
                        color = PL.Dim, fontSize = 10.5.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        // Two-column metric grid
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Heart Rate", (ui.latestHr ?: ui.restingHr)?.toString(), "bpm", PL.Sys,
                    sub = if (ui.latestHr != null) "live · tap for detail" else "resting today",
                    onClick = { onNavigate(2) }, modifier = Modifier.weight(1f))
                MetricCard("Steps", ui.stepsToday?.let { "%,d".format(it) }, "", PL.Charge,
                    sub = ui.steps7dAvg?.let { "avg %,d".format(it) } ?: "", modifier = Modifier.weight(1f), onClick = { onNavigate(4) }) {
                    WeekBars(stepWeek, PL.Charge)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val loc = ui.currentPlace
                MetricCard("Location", loc, "", when (loc) {
                    "Home" -> PL.Charge; "Work" -> PL.Dia; else -> PL.Gold
                }, sub = if (loc == null) "grant location" else "right now", modifier = Modifier.weight(1f), onClick = { onNavigate(4) })
                MetricCard("Blood Pressure",
                    ui.readings.firstOrNull()?.let { "${it.systolic}/${it.diastolic}" }, "",
                    PL.Dia, sub = "latest reading", modifier = Modifier.weight(1f), onClick = { onNavigate(4) })
            }
        }
    }
}
