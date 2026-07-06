package com.example.pulseledger.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
fun DashboardScreen(
    hcAvailable: Boolean,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
) {
    val vm: DashboardViewModel = viewModel()
    val ui by vm.ui.collectAsState()
    var tab by remember { mutableStateOf(0) }

    LaunchedEffect(permissionsGranted) { if (permissionsGranted) vm.load() }

    Scaffold(
        containerColor = PL.Bg,
        bottomBar = {
            NavigationBar(containerColor = PL.Card, tonalElevation = 0.dp) {
                listOf("⚡ Today", "◉ Pressure", "∿ History").forEachIndexed { i, label ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Text(label.take(2), fontSize = 16.sp) },
                        label = { Text(label.drop(2), fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PL.Txt, selectedTextColor = PL.Txt,
                            unselectedIconColor = PL.Dim, unselectedTextColor = PL.Dim,
                            indicatorColor = PL.CardUp,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                !hcAvailable -> Center("Health Connect isn't available on this device.")
                !permissionsGranted -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Pulse Ledger reads your health data from Health Connect. Nothing leaves this phone.",
                        color = PL.Soft, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRequestPermissions) { Text("Grant access") }
                }
                else -> when (tab) {
                    0 -> TodayTab(ui, vm)
                    1 -> PressureTab(ui, vm)
                    else -> HistoryTab(ui)
                }
            }
        }
    }
}

/* ───────────────────────── Today ───────────────────────── */
@Composable
private fun TodayTab(ui: DashboardViewModel.Ui, vm: DashboardViewModel) {
    val charge = remember(ui.summaries, ui.stepsToday) { computeCharge(ui.summaries, ui.stepsToday) }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp),
    ) {
        item { Header(ui, vm) }
        item {
            Panel {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ChargeGauge(charge.value)
                    Spacer(Modifier.height(4.dp))
                    charge.contributors.forEach { (what, delta) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Mono(if (delta > 0) "+$delta" else "$delta", 14.sp,
                                if (delta > 0) PL.Charge else PL.Drain, Modifier.width(44.dp))
                            Text(what, color = PL.Txt, fontSize = 13.sp)
                        }
                    }
                    Text("HRV joins the formula when your Fitbit Air arrives",
                        color = PL.Dim, fontSize = 10.5.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        item {
            Panel {
                Label("ACTIVITY")
                Row {
                    Stat("Steps today", ui.stepsToday?.let { "%,d".format(it) } ?: "—", Modifier.weight(1f))
                    Stat("7-day avg", ui.steps7dAvg?.let { "%,d".format(it) } ?: "—", Modifier.weight(1f))
                    Stat("Resting HR", ui.restingHr?.let { "$it" } ?: "—", Modifier.weight(1f))
                }
            }
        }
        item { LatestBpCard(ui) }
    }
}

/* ──────────────────────── Pressure ─────────────────────── */
@Composable
private fun PressureTab(ui: DashboardViewModel.Ui, vm: DashboardViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    if (showAdd) AddReadingDialog(onDismiss = { showAdd = false }, onSave = { s, d, p ->
        showAdd = false; vm.addManual(s, d, p)
    })
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp),
    ) {
        item { Header(ui, vm) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.weight(1f)) {
                    Text("+ Add reading", color = PL.Dia)
                }
            }
        }
        item { LatestBpCard(ui) }
        if (ui.readings.isNotEmpty()) {
            item {
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
            item { Label("RECENT READINGS", pad = true) }
            items(ui.readings.take(30)) { r -> ReadingRow(r) }
        }
    }
}

/* ──────────────────────── History ──────────────────────── */
@Composable
private fun HistoryTab(ui: DashboardViewModel.Ui) {
    val vm: DashboardViewModel = viewModel()
    var range by remember { mutableStateOf(Range.ALL) }
    var selectedDay by remember { mutableStateOf<DailySummary?>(null) }
    val insights = remember(ui.summaries) { mineInsights(ui.summaries) }

    selectedDay?.let { DayDetailSheet(it, onDismiss = { selectedDay = null }) }

    val now = System.currentTimeMillis()
    val scoped = remember(ui.summaries, range) {
        if (range == Range.ALL) ui.summaries
        else ui.summaries.filter { it.dayEpoch >= now - range.days * 86_400_000L }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp),
    ) {
        item { Header(ui, vm) }
        if (ui.summaries.size < 30) {
            item { Panel { Text("Import your Samsung Health export to unlock years of history here.",
                color = PL.Soft, fontSize = 13.sp) } }
            return@LazyColumn
        }

        // Range scope chips
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Range.entries.forEach { r ->
                    FilterChip(
                        selected = range == r, onClick = { range = r },
                        label = { Text(r.label, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PL.CardUp, selectedLabelColor = PL.Txt,
                            containerColor = PL.Card, labelColor = PL.Dim,
                        ),
                    )
                }
            }
        }

        if (insights.isNotEmpty() && range == Range.ALL) items(insights) { ins ->
            Panel {
                Row(verticalAlignment = Alignment.Top) {
                    Text(ins.emoji, fontSize = 22.sp, modifier = Modifier.padding(end = 12.dp))
                    Column {
                        Text(ins.title, color = PL.Txt, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        Text(ins.body, color = PL.Soft, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
            }
        }

        item {
            Panel {
                Label("TAP ANY POINT TO SEE THAT DAY")
                if (scoped.size < 2) {
                    Text("Not enough data in this range.", color = PL.Soft, fontSize = 13.sp)
                } else {
                    val xMin = scoped.first().dayEpoch
                    val xMax = scoped.last().dayEpoch
                    val tap: (DailySummary) -> Unit = { selectedDay = it }
                    FramedChart("STEPS / DAY", scoped, xMin, xMax, range, PL.Charge, "", onTapDay = tap) { it.steps?.toDouble() }
                    FramedChart("RESTING HEART RATE", scoped, xMin, xMax, range, PL.Sys, "bpm", onTapDay = tap) { it.restingHr?.toDouble() }
                    FramedChart("SLEEP", scoped, xMin, xMax, range, PL.Sleep, "", fmtValue = { "%dh%02dm".format((it/60).toInt(), (it%60).toInt()) }, onTapDay = tap) { it.sleepMinutes?.toDouble() }
                    FramedChart("STRESS", scoped, xMin, xMax, range, PL.Drain, "", onTapDay = tap) { it.stressAvg }
                    FramedChart("EXERCISE", scoped, xMin, xMax, range, PL.Dia, "min", onTapDay = tap) { it.exerciseMin?.toDouble() }
                    FramedChart("WEIGHT", scoped, xMin, xMax, range, PL.Gold, "lb", fmtValue = { "%.0f".format(it) }, onTapDay = tap) { it.weightKg?.times(2.20462) }
                    AxisLabels(xMin, xMax)
                    val total = scoped.sumOf { (it.steps ?: 0).toLong() }
                    if (total > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text("This range: %,d steps · ≈ %,d miles".format(total, total / 2100),
                            color = PL.Soft, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/* ──────────────────────── Pieces ───────────────────────── */
@Composable
private fun Header(ui: DashboardViewModel.Ui, vm: DashboardViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.importCsvs(uris)
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Pulse Ledger", color = PL.Txt, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Text("on-device · Health Connect", color = PL.Dim, fontSize = 11.sp)
            }
            TextButton(onClick = {
                picker.launch(arrayOf("text/*", "text/csv", "application/octet-stream", "text/comma-separated-values"))
            }) { Text("Import", color = PL.Dim, fontSize = 12.sp) }
            TextButton(onClick = vm::load, enabled = !ui.loading) {
                Text(if (ui.loading) "Syncing…" else "Refresh", color = PL.Dia, fontSize = 12.sp)
            }
        }
        ui.notice?.let {
            Spacer(Modifier.height(6.dp))
            Panel { Text(it, color = PL.Charge, fontSize = 12.sp, lineHeight = 17.sp) }
        }
        ui.error?.let {
            Spacer(Modifier.height(6.dp))
            Panel { Text("Health Connect: $it", color = PL.Drain, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun LatestBpCard(ui: DashboardViewModel.Ui) {
    Panel {
        Label("LATEST BLOOD PRESSURE")
        val latest = ui.readings.firstOrNull()
        if (latest == null) {
            Text("No readings yet — add one on the Pressure tab, import an Omron CSV, or take a cuff reading once the sync chain is live.",
                color = PL.Soft, fontSize = 13.sp, lineHeight = 19.sp)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Mono("${latest.systolic}", 42.sp, PL.Sys)
                Mono("/", 26.sp, PL.Dim)
                Mono("${latest.diastolic}", 42.sp, PL.Dia)
                Spacer(Modifier.width(8.dp))
                Text("mmHg", color = PL.Dim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                Spacer(Modifier.weight(1f))
                CategoryChip(latest.systolic, latest.diastolic)
            }
            Spacer(Modifier.height(6.dp))
            val map = Calculations.meanArterialPressure(latest.systolic, latest.diastolic)
            Text("MAP $map  ·  PP ${latest.systolic - latest.diastolic}  ·  ${fmtTime(latest.epochMillis)}",
                color = PL.Soft, fontSize = 12.sp)
        }
    }
}

@Composable
fun Panel(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxWidth().background(PL.Card, RoundedCornerShape(18.dp)).padding(16.dp),
    content = content,
)

@Composable
fun Label(t: String, pad: Boolean = false) = Text(
    t, color = PL.Soft, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
    modifier = if (pad) Modifier.padding(top = 4.dp) else Modifier.padding(bottom = 8.dp),
)

@Composable
fun Mono(t: String, size: androidx.compose.ui.unit.TextUnit, color: Color, modifier: Modifier = Modifier) =
    Text(t, color = color, fontSize = size, fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace, modifier = modifier)

@Composable
private fun CategoryChip(s: Int, d: Int) {
    val (name, color) = when (Calculations.category(s, d)) {
        Calculations.BpCategory.STAGE_2 -> "Stage 2" to PL.Sys
        Calculations.BpCategory.STAGE_1 -> "Stage 1" to Color(0xFFFF8A5D)
        Calculations.BpCategory.ELEVATED -> "Elevated" to PL.Drain
        Calculations.BpCategory.NORMAL -> "Normal" to PL.Charge
    }
    Text(name.uppercase(), color = PL.Bg, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(color, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 3.dp))
}

@Composable
private fun AvgCell(title: String, avg: Calculations.Averages?, modifier: Modifier) = Column(
    modifier.background(PL.CardUp, RoundedCornerShape(12.dp)).padding(12.dp),
) {
    Text(title, color = PL.Soft, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    if (avg == null) Text("—", color = PL.Soft, fontSize = 20.sp)
    else Row(verticalAlignment = Alignment.Bottom) {
        Mono("${avg.sys}", 22.sp, PL.Sys); Mono("/", 16.sp, PL.Dim); Mono("${avg.dia}", 22.sp, PL.Dia)
        Spacer(Modifier.width(6.dp))
        Text("n=${avg.n}", color = PL.Soft, fontSize = 10.sp, modifier = Modifier.padding(bottom = 3.dp))
    }
}

@Composable
private fun Stat(title: String, value: String, modifier: Modifier) = Column(modifier) {
    Text(title, color = PL.Soft, fontSize = 11.sp)
    Text(value, color = PL.Txt, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
}

@Composable
private fun ReadingRow(r: BpReading) = Row(
    Modifier.fillMaxWidth().background(PL.Card, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(fmtTime(r.epochMillis), color = PL.Soft, fontSize = 12.sp, modifier = Modifier.weight(1f))
    Mono("${r.systolic}", 16.sp, PL.Sys); Mono("/", 13.sp, PL.Dim); Mono("${r.diastolic}", 16.sp, PL.Dia)
}

@Composable
private fun AddReadingDialog(onDismiss: () -> Unit, onSave: (Int, Int, Int?) -> Unit) {
    var sys by remember { mutableStateOf("") }
    var dia by remember { mutableStateOf("") }
    var pulse by remember { mutableStateOf("") }
    val valid = sys.toIntOrNull()?.let { it in 60..260 } == true &&
        dia.toIntOrNull()?.let { it in 30..200 } == true
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PL.CardUp,
        title = { Text("Add blood pressure", color = PL.Txt) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = sys, onValueChange = { sys = it.filter(Char::isDigit).take(3) },
                    label = { Text("Systolic") }, singleLine = true)
                OutlinedTextField(value = dia, onValueChange = { dia = it.filter(Char::isDigit).take(3) },
                    label = { Text("Diastolic") }, singleLine = true)
                OutlinedTextField(value = pulse, onValueChange = { pulse = it.filter(Char::isDigit).take(3) },
                    label = { Text("Pulse (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(sys.toInt(), dia.toInt(), pulse.toIntOrNull()) }) {
                Text("Save", color = if (valid) PL.Charge else PL.Soft)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = PL.Soft) } },
    )
}

@Composable
private fun Center(msg: String) = Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
    Text(msg, color = PL.Soft)
}

private fun fmtTime(epoch: Long): String =
    DateTimeFormatter.ofPattern("MMM d · h:mm a").withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epoch))
