package com.example.pulseledger

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pulseledger.data.HealthConnectManager
import com.example.pulseledger.sync.SyncWorker
import com.example.pulseledger.ui.DashboardScreen
import com.example.pulseledger.ui.DashboardViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var hc: HealthConnectManager
    private var granted by mutableStateOf(false)
    private var sharedUris by mutableStateOf<List<Uri>>(emptyList())

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedPerms -> granted = grantedPerms.containsAll(hc.permissions) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hc = HealthConnectManager(this)
        com.example.pulseledger.ui.Profile.init(this)
        sharedUris = extractShared(intent)

        lifecycleScope.launch {
            if (hc.isAvailable()) {
                granted = hc.hasAllPermissions()
                if (!granted) permissionLauncher.launch(hc.permissions)
                else SyncWorker.schedule(this@MainActivity)
            }
        }

        setContent {
            val vm: DashboardViewModel = viewModel()
            // When launched via Share, ingest the files once.
            LaunchedEffect(sharedUris) {
                if (sharedUris.isNotEmpty()) {
                    vm.importCsvs(sharedUris)
                    sharedUris = emptyList()
                }
            }
            DashboardScreen(
                hcAvailable = hc.isAvailable(),
                permissionsGranted = granted,
                onRequestPermissions = { permissionLauncher.launch(hc.permissions) },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uris = extractShared(intent)
        if (uris.isNotEmpty()) sharedUris = uris
    }

    private fun extractShared(intent: Intent?): List<Uri> = when (intent?.action) {
        Intent.ACTION_SEND ->
            (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { listOf(it) } ?: emptyList()
        Intent.ACTION_SEND_MULTIPLE ->
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: emptyList()
        else -> emptyList()
    }
}
