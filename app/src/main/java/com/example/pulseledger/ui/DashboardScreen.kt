package com.example.pulseledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pulseledger.data.db.BpReading
import com.example.pulseledger.domain.Calculations
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Bg = Color(0xFF0A0F1C)
private val Card = Color(0xFF111A2C)
private val CardUp = Color(0xFF18243A)
private val Line = Color(0xFF1E2A42)
private val Txt = Color(0xFFEAF0F9)
private val Soft = Color(0xFF8CA0BE)
private val Sys = Color(0xFFFF5D73)
private val Dia = Color(0xFF5B9BFF)
private val Ok = Color(0xFF3EE58A)
private val Warn = Color(0xFFF5A623)

@Composable
fun DashboardScreen(
    hcAvailable: Boolean,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
) {
    val vm: DashboardViewModel = viewModel()
    val ui by vm.ui.collectAsState()

    LaunchedEffect(permissionsGranted) { if (permissionsGranted) vm.load() }

    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        when {
            !hcAvailable -> Center("Health Connect isn't available on this device. Install or update it from the Play Store.")
            !permissionsGranted -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Pulse Ledger reads your health data from Health Connect. Nothing leaves this phone.",
                    color = Soft, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRequestPermissions) { Text("Grant access") }
            }
            else -> Dashboard(ui, onRefresh = vm::load)
        }
    }
}

@Composable
private fun Center(msg: String) = Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
    Text(msg, color = Soft)
}

@Composable
private fun Dashboard(ui: DashboardViewModel.Ui, onRefresh: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pulse Ledger", color = Txt, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                    Text("on-device · Health Connect", color = Soft, fontSize = 11.sp)
                }
                TextButton(onClick = onRefresh, enabled = !ui.loading) {
                    Text(if (ui.loading) "Syncing…" else "Refresh", color = Dia)
                }
            }
        }

        ui.error?.let { item { Panel { Text("Couldn't read Health Connect: $it", color = Warn, fontSize = 13.sp) } } }

        // ── Latest blood pressure ─────────────────────────────
        item {
            Panel {
                Label("LATEST BLOOD PRESSURE")
                val latest = ui.readings.firstOrNull()
                if (latest == null) {
                    Text(
                        "No readings yet. If your Omron app is set to write to Health Connect, take a reading and hit Refresh. Otherwise readings can be imported from a CSV export.",
                        color = Soft, fontSize = 13.sp, lineHeight = 19.sp,
                    )
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Mono("${latest.systolic}", 42.sp, Sys)
                        Mono("/", 26.sp, Soft)
                        Mono("${latest.diastolic}", 42.sp, Dia)
                        Spacer(Modifier.width(8.dp))
                        Text("mmHg", color = Soft, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                        Spacer(Modifier.weight(1f))
                        CategoryChip(latest.systolic, latest.diastolic)
                    }
                    Spacer(Modifier.height(6.dp))
                    val map = Calculations.meanArterialPressure(latest.systolic, latest.diastolic)
                    val pp = Calculations.pulsePressure(latest.systolic, latest.diastolic)
                    Text("MAP $map  ·  PP $pp  ·  ${fmtTime(latest.epochMillis)}", color = Soft, fontSize = 12.sp)
                }
            }
        }

        // ── 7-day averages ────────────────────────────────────
        if (ui.readings.isNotEmpty()) item {
            Panel {
                Label("7-DAY AVERAGES")
                val weekAgo = System.currentTimeMillis() - 7 * 86_400_000L
                val (am, pm) = Calculations.weeklyAmPm(ui.readings.filter { it.epochMillis >= weekAgo })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AvgCell("Morning", am, Modifier.weight(1f))
                    AvgCell("Evening", pm, Modifier.weight(1f))
                }
            }
        }

        // ── Activity ──────────────────────────────────────────
        item {
            Panel {
                Label("ACTIVITY")
                Row {
                    Stat("Steps today", ui.stepsToday?.toString() ?: "—", Modifier.weight(1f))
                    Stat("7-day avg", ui.steps7dAvg?.toString() ?: "—", Modifier.weight(1f))
                    Stat("Resting HR", ui.restingHr?.let { "$it bpm" } ?: "—", Modifier.weight(1f))
                }
                if (ui.stepsToday == null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Steps appear once an app (Google Health, Samsung Health…) writes them to Health Connect. Your Fitbit Air will fill this in.",
                        color = Soft, fontSize = 11.sp, lineHeight = 16.sp,
                    )
                }
            }
        }

        // ── Recent readings list ──────────────────────────────
        if (ui.readings.isNotEmpty()) {
            item { Label("RECENT READINGS", pad = true) }
            items(ui.readings.take(20)) { r -> ReadingRow(r) }
        }
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxWidth().background(Card, RoundedCornerShape(18.dp)).padding(16.dp),
    content = content,
)

@Composable
private fun Label(t: String, pad: Boolean = false) = Text(
    t, color = Soft, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
    modifier = if (pad) Modifier.padding(top = 4.dp) else Modifier.padding(bottom = 8.dp),
)

@Composable
private fun Mono(t: String, size: androidx.compose.ui.unit.TextUnit, color: Color) =
    Text(t, color = color, fontSize = size, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)

@Composable
private fun CategoryChip(s: Int, d: Int) {
    val (name, color) = when (Calculations.category(s, d)) {
        Calculations.BpCategory.STAGE_2 -> "Stage 2" to Sys
        Calculations.BpCategory.STAGE_1 -> "Stage 1" to Color(0xFFFF8A5D)
        Calculations.BpCategory.ELEVATED -> "Elevated" to Warn
        Calculations.BpCategory.NORMAL -> "Normal" to Ok
    }
    Text(
        name.uppercase(), color = Bg, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(color, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun AvgCell(title: String, avg: Calculations.Averages?, modifier: Modifier) = Column(
    modifier.background(CardUp, RoundedCornerShape(12.dp)).padding(12.dp),
) {
    Text(title, color = Soft, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    if (avg == null) Text("—", color = Soft, fontSize = 20.sp)
    else Row(verticalAlignment = Alignment.Bottom) {
        Mono("${avg.sys}", 22.sp, Sys); Mono("/", 16.sp, Soft); Mono("${avg.dia}", 22.sp, Dia)
        Spacer(Modifier.width(6.dp))
        Text("n=${avg.n}", color = Soft, fontSize = 10.sp, modifier = Modifier.padding(bottom = 3.dp))
    }
}

@Composable
private fun Stat(title: String, value: String, modifier: Modifier) = Column(modifier) {
    Text(title, color = Soft, fontSize = 11.sp)
    Text(value, color = Txt, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
}

@Composable
private fun ReadingRow(r: BpReading) = Row(
    Modifier.fillMaxWidth().background(Card, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(fmtTime(r.epochMillis), color = Soft, fontSize = 12.sp, modifier = Modifier.weight(1f))
    Mono("${r.systolic}", 16.sp, Sys); Mono("/", 13.sp, Soft); Mono("${r.diastolic}", 16.sp, Dia)
}

private fun fmtTime(epoch: Long): String =
    DateTimeFormatter.ofPattern("MMM d · h:mm a")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epoch))
