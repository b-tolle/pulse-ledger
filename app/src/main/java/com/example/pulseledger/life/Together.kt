package com.example.pulseledger.life

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import java.util.UUID

/**
 * App-to-app couple beacon. Both phones run Tolle Track with the same partner
 * code; the code is hashed into a service UUID that each phone advertises and
 * scans for. Nothing identifying is broadcast — just a UUID only the two of
 * you share. All logging stays on-device.
 */
object TogetherBeacon {

    fun coupleUuid(code: String): UUID {
        var h = 0x811C9DC5.toInt()                 // FNV-1a over the code
        code.trim().lowercase().forEach { c -> h = (h xor c.code) * 0x01000193 }
        return UUID.fromString("70113a7e-c0de-4a11-b055-7011%08x".format(h))
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising(ctx: Context, code: String): AdvertiseCallback? {
        val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val adv = adapter?.bluetoothLeAdvertiser ?: return null
        val cb = object : AdvertiseCallback() {}
        runCatching {
            adv.startAdvertising(
                AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                    .setConnectable(false)
                    .build(),
                AdvertiseData.Builder()
                    .addServiceUuid(ParcelUuid(coupleUuid(code)))
                    .setIncludeDeviceName(false)
                    .build(),
                cb,
            )
        }.onFailure { return null }
        return cb
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising(ctx: Context, cb: AdvertiseCallback?) {
        cb ?: return
        val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        runCatching { adapter?.bluetoothLeAdvertiser?.stopAdvertising(cb) }
    }

    /** Continuous scan while foreground; onSeen(rssi) fires per sighting. */
    @SuppressLint("MissingPermission")
    fun startScan(ctx: Context, code: String, onSeen: (Int) -> Unit): ScanCallback? {
        val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val scanner = adapter?.bluetoothLeScanner ?: return null
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) { onSeen(result.rssi) }
        }
        runCatching {
            scanner.startScan(
                listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(coupleUuid(code))).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build(),
                cb,
            )
        }.onFailure { return null }
        return cb
    }

    @SuppressLint("MissingPermission")
    fun stopScan(ctx: Context, cb: ScanCallback?) {
        cb ?: return
        val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        runCatching { adapter?.bluetoothLeScanner?.stopScan(cb) }
    }
}
