package com.example.pulseledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pulseledger.data.db.BpReading
import com.example.pulseledger.data.db.DailySummary
import com.example.pulseledger.domain.Calculations
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(hcAvailable: Boolean, permissionsGranted: Boolean, onRequestPermissions: () -> Unit) {
    val vm: DashboardViewModel = viewModel()
    val ui by vm.ui.collectAsState()
    var tab by remember { mutableStateOf(0) }
    LaunchedEffect(permissionsGranted) { if (permissionsGranted) vm.load() }

    val tabs = listOf(
        Triple("Home", "\u2302", 0), Triple("Heart", "\u2665", 1),
        Triple("Sleep", "\u263D", 2), Triple("Activity", "\u26A1", 3),
        Triple("Data", "\u2317", 4),
    )
    Scaffold(
        containerColor = PL.Bg,
        bottomBar = {
            NavigationBar(containerColor = PL.Card, tonalElevation = 0.dp) {
                tabs.forEach { (label, icon, i) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Text(icon, fontSize = 17.sp) },
                        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PL.Txt, selectedTextColor = PL.Txt,
                            unselectedIconColor = PL.Dim, unselectedTextColor = PL.Dim,
                            indicatorColor = PL.CardUp),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                !hcAvailable -> Center("Health Connect isn't available on this device.")
                !permissionsGranted -> Column(Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Pulse Ledger reads your health data from Health Connect. Nothing leaves this phone.",
                        color = PL.Soft, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp)); Button(onClick = onRequestPermissions) { Text("Grant access") }
                }
                else -> when (tab) {
                    0 -> HomeTab(ui, vm, onNavigate = { tab = it })
                    1 -> HeartTab(ui, vm)
                    2 -> SleepTab(ui, vm)
                    3 -> ActivityTab(ui, vm)
                    else -> DataTab(ui, vm)
                }
            }
        }
    }
}

/* ── Data tab = Pressure + History combined ── */
@Composable
private fun DataTab(ui: DashboardViewModel.Ui, vm: DashboardViewModel) {
    var sub by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Pressure", "History").forEachIndexed { i, label ->
                FilterChip(selected = sub == i, onClick = { sub = i }, label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PL.CardUp, selectedLabelColor = PL.Txt,
                        containerColor = PL.Card, labelColor = PL.Dim))
            }
        }
        if (sub == 0) PressureContent(ui, vm) else HistoryContent(ui)
    }
}

@Composable
private fun PressureContent(ui: DashboardViewModel.Ui, vm: DashboardViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    if (showAdd) AddReadingDialog(onDismiss = { showAdd = false }, onSave = { s, d, p -> showAdd = false; vm.addManual(s, d, p) })
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                Text("+ Add reading", color = PL.Dia)
            }
        }
        item { LatestBpCard(ui) }
        if (ui.readings.size >= 2) item {
            Card {
                SectionLabel("PRESSURE BANDS · LAST 21 READINGS"); Spacer(Modifier.height(10.dp))
                PressureBandChart(ui.readings)
            }
        }
        item { MedEffectCard(ui) }
        if (ui.readings.isNotEmpty()) {
            item {
                Card {
                    SectionLabel("7-DAY AVERAGES"); Spacer(Modifier.height(10.dp))
                    val weekAgo = System.currentTimeMillis() - 7 * 86_400_000L
                    val (am, pm) = Calculations.weeklyAmPm(ui.readings.filter { it.epochMillis >= weekAgo })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AvgCell("Morning", am, Modifier.weight(1f)); AvgCell("Evening", pm, Modifier.weight(1f))
                    }
                }
            }
            item { SectionLabel("RECENT READINGS") }
            items(ui.readings.take(30)) { ReadingRow(it) }
        }
    }
}

@Composable
private fun HistoryContent(ui: DashboardViewModel.Ui) {
    var range by remember { mutableStateOf(Range.ALL) }
    var selectedDay by remember { mutableStateOf<DailySummary?>(null) }
    val insights = remember(ui.summaries, ui.locationDays) { mineInsights(ui.summaries, ui.locationDays) }
    selectedDay?.let { d -> DayDetailSheet(d, location = ui.locationDays[d.dayEpoch], onDismiss = { selectedDay = null }) }
    val now = System.currentTimeMillis()
    val scoped = remember(ui.summaries, range) {
        if (range == Range.ALL) ui.summaries else ui.summaries.filter { it.dayEpoch >= now - range.days * 86_400_000L }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        if (ui.summaries.size < 30) {
            item { Card { Text("Import your Samsung Health export to unlock years of history.", color = PL.Soft, fontSize = 13.sp) } }
            return@LazyColumn
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Range.entries.forEach { r ->
                    FilterChip(selected = range == r, onClick = { range = r }, label = { Text(r.label, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PL.CardUp, selectedLabelColor = PL.Txt,
                            containerColor = PL.Card, labelColor = PL.Dim))
                }
            }
        }
        if (range == Range.ALL) {
            val recs = records(ui.summaries)
            if (recs.isNotEmpty()) item {
                Card {
                    SectionLabel("PERSONAL RECORDS · TAP TO SEE THE DAY")
                    recs.forEach { r ->
                        val day = r.dayEpoch?.let { ep -> ui.summaries.firstOrNull { it.dayEpoch == ep } }
                        Row(Modifier.fillMaxWidth().clickable(enabled = day != null) { day?.let { selectedDay = it } }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(r.emoji, fontSize = 18.sp, modifier = Modifier.padding(end = 10.dp))
                            Text(r.label, color = PL.Soft, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(r.value, color = PL.Txt, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                            if (day != null) Text("  ›", color = PL.Dim, fontSize = 18.sp)
                        }
                    }
                }
            }
            items(insights) { ins ->
                Card {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(ins.emoji, fontSize = 22.sp, modifier = Modifier.padding(end = 12.dp))
                        Column {
                            Text(ins.title, color = PL.Txt, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(2.dp)); Text(ins.body, color = PL.Soft, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }
        }
        item {
            Card {
                SectionLabel("TAP ANY POINT TO SEE THAT DAY")
                if (scoped.size < 2) Text("Not enough data in this range.", color = PL.Soft, fontSize = 13.sp)
                else {
                    val xMin = scoped.first().dayEpoch; val xMax = scoped.last().dayEpoch
                    val tap: (DailySummary) -> Unit = { selectedDay = it }
                    FramedChart("STEPS / DAY", scoped, xMin, xMax, range, PL.Charge, "", onTapDay = tap) { it.steps?.toDouble() }
                    FramedChart("RESTING HEART RATE", scoped, xMin, xMax, range, PL.Sys, "bpm", onTapDay = tap) { it.restingHr?.toDouble() }
                    FramedChart("SLEEP", scoped, xMin, xMax, range, PL.Sleep, "", fmtValue = { "%dh%02dm".format((it/60).toInt(), (it%60).toInt()) }, onTapDay = tap) { it.sleepMinutes?.toDouble() }
                    FramedChart("STRESS", scoped, xMin, xMax, range, PL.Drain, "", onTapDay = tap) { it.stressAvg }
                    FramedChart("EXERCISE", scoped, xMin, xMax, range, PL.Dia, "min", onTapDay = tap) { it.exerciseMin?.toDouble() }
                    FramedChart("WEIGHT", scoped, xMin, xMax, range, PL.Gold, "lb", fmtValue = { "%.0f".format(it) }, onTapDay = tap) { it.weightKg?.times(2.20462) }
                    AxisLabels(xMin, xMax)
                }
            }
        }
    }
}

@Composable
private fun MedEffectCard(ui: DashboardViewModel.Ui) {
    // BP meds started May 22, 2026 (edit MED_START to adjust)
    val MED_START = java.time.LocalDate.of(2026, 5, 22)
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val before = ui.readings.filter { it.epochMillis < MED_START }
    val after = ui.readings.filter { it.epochMillis >= MED_START }
    if (before.size < 3 || after.size < 3) return
    fun avg(l: List<BpReading>, f: (BpReading) -> Int) = l.map(f).average().toInt()
    val bS = avg(before) { it.systolic }; val bD = avg(before) { it.diastolic }
    val aS = avg(after) { it.systolic }; val aD = avg(after) { it.diastolic }
    Card {
        SectionLabel("SINCE STARTING BP MEDS · MAY 22")
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Before (n=${before.size})", color = PL.Soft, fontSize = 12.sp)
                Text("$bS/$bD", color = bpSeverityColor(bS, bD), fontSize = 26.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            Text("→", color = PL.Dim, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 10.dp))
            Column(Modifier.weight(1f)) {
                Text("Since (n=${after.size})", color = PL.Soft, fontSize = 12.sp)
                Text("$aS/$aD", color = bpSeverityColor(aS, aD), fontSize = 26.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
        Spacer(Modifier.height(8.dp))
        val dS = aS - bS; val dD = aD - bD
        Text(
            "Change: ${if (dS <= 0) "" else "+"}$dS systolic, ${if (dD <= 0) "" else "+"}$dD diastolic. " +
            "Numbers to bring to your prescriber — the app never advises dose changes.",
            color = PL.Dim, fontSize = 11.5.sp, lineHeight = 16.sp,
        )
    }
}

/* ── shared bits used by Data tab ── */
@Composable
private fun LatestBpCard(ui: DashboardViewModel.Ui) {
    Card {
        SectionLabel("LATEST BLOOD PRESSURE")
        val latest = ui.readings.firstOrNull()
        Spacer(Modifier.height(8.dp))
        if (latest == null) Text("No readings yet — add one above or import an Omron CSV.", color = PL.Soft, fontSize = 13.sp)
        else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${latest.systolic}", color = PL.Sys, fontSize = 42.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                Text("/", color = PL.Dim, fontSize = 26.sp, fontFamily = FontFamily.Monospace)
                Text("${latest.diastolic}", color = PL.Dia, fontSize = 42.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(8.dp)); Text("mmHg", color = PL.Dim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                Spacer(Modifier.weight(1f)); CategoryChip(latest.systolic, latest.diastolic)
            }
            Spacer(Modifier.height(6.dp))
            Text("MAP ${Calculations.meanArterialPressure(latest.systolic, latest.diastolic)}  ·  PP ${latest.systolic - latest.diastolic}  ·  ${fmtTime(latest.epochMillis)}",
                color = PL.Soft, fontSize = 12.sp)
        }
    }
}

@Composable private fun CategoryChip(s: Int, d: Int) {
    val (name, color) = when (Calculations.category(s, d)) {
        Calculations.BpCategory.STAGE_2 -> "Stage 2" to PL.Sys
        Calculations.BpCategory.STAGE_1 -> "Stage 1" to Color(0xFFFF8A5D)
        Calculations.BpCategory.ELEVATED -> "Elevated" to PL.Drain
        Calculations.BpCategory.NORMAL -> "Normal" to PL.Charge
    }
    Text(name.uppercase(), color = PL.Bg, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(color, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 3.dp))
}

@Composable private fun AvgCell(title: String, avg: Calculations.Averages?, modifier: Modifier) = Column(
    modifier.background(PL.CardUp, RoundedCornerShape(12.dp)).padding(12.dp)) {
    Text(title, color = PL.Soft, fontSize = 12.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp))
    if (avg == null) Text("—", color = PL.Soft, fontSize = 20.sp)
    else Row(verticalAlignment = Alignment.Bottom) {
        Text("${avg.sys}", color = PL.Sys, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
        Text("/", color = PL.Dim, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
        Text("${avg.dia}", color = PL.Dia, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(6.dp)); Text("n=${avg.n}", color = PL.Soft, fontSize = 10.sp, modifier = Modifier.padding(bottom = 3.dp))
    }
}

@Composable private fun ReadingRow(r: BpReading) {
    val sev = bpSeverityColor(r.systolic, r.diastolic)
    Row(
        Modifier.fillMaxWidth().background(PL.Card, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(26.dp).background(sev, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(10.dp))
        Text(fmtTime(r.epochMillis), color = PL.Soft, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text("${r.systolic}", color = sev, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
        Text("/", color = PL.Dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Text("${r.diastolic}", color = sev, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
    }
}

@Composable private fun AddReadingDialog(onDismiss: () -> Unit, onSave: (Int, Int, Int?) -> Unit) {
    var sys by remember { mutableStateOf("") }; var dia by remember { mutableStateOf("") }; var pulse by remember { mutableStateOf("") }
    val valid = sys.toIntOrNull()?.let { it in 60..260 } == true && dia.toIntOrNull()?.let { it in 30..200 } == true
    AlertDialog(onDismissRequest = onDismiss, containerColor = PL.CardUp,
        title = { Text("Add blood pressure", color = PL.Txt) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(sys, { sys = it.filter(Char::isDigit).take(3) }, label = { Text("Systolic") }, singleLine = true)
            OutlinedTextField(dia, { dia = it.filter(Char::isDigit).take(3) }, label = { Text("Diastolic") }, singleLine = true)
            OutlinedTextField(pulse, { pulse = it.filter(Char::isDigit).take(3) }, label = { Text("Pulse (optional)") }, singleLine = true)
        } },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(sys.toInt(), dia.toInt(), pulse.toIntOrNull()) }) {
            Text("Save", color = if (valid) PL.Charge else PL.Soft) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = PL.Soft) } })
}

@Composable private fun Center(msg: String) = Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
    Text(msg, color = PL.Soft)
}

private fun fmtTime(epoch: Long): String =
    DateTimeFormatter.ofPattern("MMM d · h:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epoch))
