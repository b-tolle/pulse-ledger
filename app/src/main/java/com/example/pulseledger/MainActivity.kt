package com.example.pulseledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.*
import androidx.health.connect.client.PermissionController
import com.example.pulseledger.data.HealthConnectManager
import com.example.pulseledger.sync.SyncWorker
import com.example.pulseledger.ui.DashboardScreen
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {

    private lateinit var hc: HealthConnectManager
    private var granted by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedPerms -> granted = grantedPerms.containsAll(hc.permissions) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hc = HealthConnectManager(this)

        lifecycleScope.launch {
            if (hc.isAvailable()) {
                granted = hc.hasAllPermissions()
                if (!granted) permissionLauncher.launch(hc.permissions)
                else SyncWorker.schedule(this@MainActivity)
            }
        }

        setContent {
            DashboardScreen(
                hcAvailable = hc.isAvailable(),
                permissionsGranted = granted,
                onRequestPermissions = { permissionLauncher.launch(hc.permissions) },
            )
        }
    }
}
