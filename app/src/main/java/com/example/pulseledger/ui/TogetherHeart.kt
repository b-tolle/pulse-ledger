package com.example.pulseledger.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.pulseledger.life.TogetherBeacon

/** Live proximity heart: advertises + scans while Home is visible.
 *  Throbs like a heartbeat when the partner's beacon is in range. */
@Composable
fun TogetherHeart(ui: DashboardViewModel.Ui) {
    val code = Profile.partnerCode ?: return
    val ctx = LocalContext.current
    var lastSeen by remember { mutableStateOf(0L) }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res -> granted = res.values.all { it } }
    LaunchedEffect(Unit) {
        if (!granted) permLauncher.launch(arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
        ))
    }

    DisposableEffect(granted, code) {
        if (!granted) return@DisposableEffect onDispose { }
        val adv = TogetherBeacon.startAdvertising(ctx, code)
        val scan = TogetherBeacon.startScan(ctx, code) { lastSeen = System.currentTimeMillis() }
        onDispose {
            TogetherBeacon.stopAdvertising(ctx, adv)
            TogetherBeacon.stopScan(ctx, scan)
        }
    }

    // "near" = beacon sighted in the last 25s
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(5_000); now = System.currentTimeMillis() } }
    val near = now - lastSeen < 25_000
    val btOn = remember(now) {
        runCatching {
            (ctx.getSystemService(android.content.Context.BLUETOOTH_SERVICE)
                as android.bluetooth.BluetoothManager).adapter?.isEnabled == true
        }.getOrDefault(false)
    }

    val beat = rememberInfiniteTransition(label = "beat")
    val scale by beat.animateFloat(
        initialValue = 1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 900
                1f at 0; 1.28f at 120; 1f at 260; 1.18f at 380; 1f at 560; 1f at 900
            }
        ), label = "beatScale",
    )

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Icon(
            Icons.Filled.Favorite, contentDescription = "together",
            tint = if (near) PL.Sys else PL.Line,
            modifier = Modifier.size(18.dp).scale(if (near) scale else 1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                !granted -> "Tap to allow nearby devices"
                !btOn -> "Bluetooth is off"
                near -> "Together right now"
                ui.togetherTodayMin > 0 -> "Together ${ui.togetherTodayMin / 60}h ${ui.togetherTodayMin % 60}m today"
                else -> "Apart"
            },
            color = if (near) PL.Sys else PL.Dim,
            fontSize = 12.5.sp, fontWeight = if (near) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (near && ui.togetherTodayMin > 0) {
            Text("  ·  ${ui.togetherTodayMin / 60}h ${ui.togetherTodayMin % 60}m today",
                color = PL.Soft, fontSize = 12.sp)
        }
    }
}
