package com.snapcal.app.scale

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Passive BLE listener for Xiaomi scales. No pairing or connection: the scale broadcasts encrypted
 * MiBeacon advertisements while someone is standing on it, and the official Mi Home app keeps working.
 *
 *  - [startForeground]: low-latency scan while the app is open.
 *  - [startBackground]: filtered PendingIntent scan delivered to [ScaleScanReceiver] even when the app
 *    isn't running (Android 8+). Some OEM ROMs throttle this; the foreground scan is the reliable path.
 */
class ScaleScanner(private val context: Context) {
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    val isSupported: Boolean get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    val isEnabled: Boolean get() = adapter?.isEnabled == true

    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_SCAN)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private val filters = listOf(
        ScanFilter.Builder().setServiceData(ParcelUuid.fromString(FE95_UUID), null).build(),
    )

    private var callback: ScanCallback? = null

    @SuppressLint("MissingPermission")
    fun startForeground(onAdvertisement: (address: String, serviceData: ByteArray) -> Unit): Boolean {
        val scanner = adapter?.bluetoothLeScanner ?: return false
        if (!hasPermissions()) return false
        stopForeground()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = deliver(result, onAdvertisement)
            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { deliver(it, onAdvertisement) }
            override fun onScanFailed(errorCode: Int) { Log.w(TAG, "scan failed: $errorCode") }
        }
        callback = cb
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        return runCatching { scanner.startScan(filters, settings, cb); true }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun stopForeground() {
        val cb = callback ?: return
        callback = null
        runCatching { adapter?.bluetoothLeScanner?.stopScan(cb) }
    }

    @SuppressLint("MissingPermission")
    fun startBackground(): Boolean {
        val scanner = adapter?.bluetoothLeScanner ?: return false
        if (!hasPermissions()) return false
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        return runCatching { scanner.startScan(filters, settings, backgroundIntent()) == 0 }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun stopBackground() {
        runCatching { adapter?.bluetoothLeScanner?.stopScan(backgroundIntent()) }
    }

    private fun backgroundIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        1,
        Intent(context, ScaleScanReceiver::class.java).setAction(ScaleScanReceiver.ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    private fun deliver(result: ScanResult, sink: (String, ByteArray) -> Unit) {
        val data = result.scanRecord?.getServiceData(ParcelUuid.fromString(FE95_UUID)) ?: return
        sink(result.device.address, data)
    }

    companion object {
        const val FE95_UUID = "0000fe95-0000-1000-8000-00805f9b34fb"
        private const val TAG = "ScaleScanner"
    }
}
