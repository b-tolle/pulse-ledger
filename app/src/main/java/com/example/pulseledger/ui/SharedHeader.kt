package com.example.pulseledger.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun AppHeader(ui: DashboardViewModel.Ui, vm: DashboardViewModel) {
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.importCsvs(uris)
    }
    var update by remember { mutableStateOf<Updater.Available?>(null) }
    var updating by remember { mutableStateOf(false) }
    var checkMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { update = Updater.check(currentVersionCode(ctx)) }

    Column {
        update?.let { av ->
            Card(Modifier.padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Update available", color = PL.Charge, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Version ${av.versionName}", color = PL.Soft, fontSize = 11.sp)
                    }
                    Button(onClick = { updating = true; scope.launch { runCatching { Updater.downloadAndInstall(ctx, av.apkUrl) } } },
                        enabled = !updating, colors = ButtonDefaults.buttonColors(containerColor = PL.Charge)) {
                        Text(if (updating) "Downloading…" else "Update", color = PL.Bg)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Pulse Ledger", color = PL.Txt, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "v${versionName(ctx)} · tap to check for updates",
                    color = PL.Dim, fontSize = 11.sp,
                    modifier = Modifier.clickable {
                        checkMsg = "Checking…"
                        scope.launch {
                            val av = Updater.check(currentVersionCode(ctx))
                            if (av != null) { update = av; checkMsg = null }
                            else checkMsg = "You're on the latest version"
                        }
                    },
                )
            }
            TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Import", color = PL.Dim, fontSize = 12.sp) }
            TextButton(onClick = vm::load, enabled = !ui.loading) {
                Text(if (ui.loading) "Syncing…" else "Refresh", color = PL.Dia, fontSize = 12.sp)
            }
        }
        checkMsg?.let { Spacer(Modifier.height(6.dp)); Text(it, color = PL.Charge, fontSize = 12.sp) }
        ui.notice?.let { Spacer(Modifier.height(6.dp)); Card { Text(it, color = PL.Charge, fontSize = 12.sp, lineHeight = 17.sp) } }
        ui.error?.let { Spacer(Modifier.height(6.dp)); Card { Text("Health Connect: $it", color = PL.Drain, fontSize = 12.sp) } }
    }
}

@Composable
fun Greeting(ui: DashboardViewModel.Ui) {
    val hour = java.time.LocalTime.now().hour
    val greeting = when { hour < 12 -> "Good morning"; hour < 18 -> "Good afternoon"; else -> "Good evening" }
    val date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d"))
    Column {
        Text(greeting, color = PL.Txt, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        val sub = buildString {
            append(date)
            ui.calendar?.let { if (it.eventCount > 0) append("  ·  ${it.eventCount} event${if (it.eventCount>1) "s" else ""}") }
        }
        Text(sub, color = PL.Soft, fontSize = 13.sp)
    }
}

fun versionName(ctx: android.content.Context): String = runCatching {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
}.getOrDefault("?")

fun currentVersionCode(ctx: android.content.Context): Int = runCatching {
    val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
    if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else @Suppress("DEPRECATION") pi.versionCode
}.getOrDefault(0)
