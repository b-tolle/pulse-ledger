package com.example.pulseledger.life

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings

/**
 * "Am I with my wife right now?"
 *
 * HONEST LIMITS FIRST: modern phones randomize their Bluetooth MAC address
 * and don't advertise constantly, so you canNOT reliably detect an arbitrary
 * phone passively. What works:
 *
 *  1. BOND ONCE (recommended): pair your phones in Android Bluetooth settings
 *     one time. Bonded devices exchange an Identity Resolving Key, so your
 *     phone can recognize her phone's rotating addresses in BLE scan results.
 *     After that, presence detection is dependable when Bluetooth is on.
 *  2. HER ACCESSORIES: her smartwatch/earbuds often advertise; once you add
 *     them (from a scan picker, with her knowledge) they act as proxies.
 *  3. FUSION: co-arrival at the same geofence + calendar events containing
 *     her name + evening/weekend heuristics = "date night" classification.
 *
 * All matching happens on-device; we store only intervals ("together
 * 6:40–10:15 PM"), never her locations.
 */
class TogetherDetector(
    private val adapter: BluetoothAdapter,
    private val partnerAddresses: Set<String>,   // bonded phone + her accessories
    private val onPresence: (rssi: Int) -> Unit,
) {
    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (result.device.address in partnerAddresses) onPresence(result.rssi)
        }
    }

    @SuppressLint("MissingPermission") // requires BLUETOOTH_SCAN + BLUETOOTH_CONNECT at runtime
    fun start() {
        val filters = partnerAddresses.map {
            ScanFilter.Builder().setDeviceAddress(it).build()
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)   // battery-friendly
            .build()
        adapter.bluetoothLeScanner?.startScan(filters, settings, callback)
    }

    @SuppressLint("MissingPermission")
    fun stop() { adapter.bluetoothLeScanner?.stopScan(callback) }

    companion object {
        /** RSSI stronger than this ≈ same room / same table. */
        const val NEAR_RSSI = -70
        /** Merge presence pings closer than this into one session. */
        const val SESSION_GAP_MIN = 12
    }
}
