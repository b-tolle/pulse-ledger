package com.example.pulseledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Skeleton screen. Wire it to a ViewModel exposing
 * dao().summariesSince(...) as StateFlow, then port the visual design
 * from the React mockup: pressure-band chart (Vico), AM/PM average
 * cards, MAP/pulse-pressure line, HRV baseline delta, sleep insight.
 */
@Composable
fun DashboardScreen(
    hcAvailable: Boolean,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                !hcAvailable -> Text("Health Connect isn't available on this device. Install or update it from the Play Store.")
                !permissionsGranted -> {
                    Text("Pulse Ledger reads your Fitbit and Omron data from Health Connect. Nothing leaves this phone.")
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRequestPermissions) { Text("Grant access") }
                }
                else -> Text("Connected. Syncing your last 30 days…")
            }
        }
    }
}
