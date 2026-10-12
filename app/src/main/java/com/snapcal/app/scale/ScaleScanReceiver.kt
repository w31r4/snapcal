package com.snapcal.app.scale

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.ParcelUuid
import com.snapcal.app.SnapCalApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives background scan results (see [ScaleScanner.startBackground]) and feeds the shared [ScaleBridge]. */
class ScaleScanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val results: List<ScanResult> = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, ScanResult::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT)
        } ?: return
        val bridge = (context.applicationContext as SnapCalApp).scale
        val pending = goAsync()
        scope.launch {
            try {
                results.forEach { r ->
                    val data = r.scanRecord?.getServiceData(ParcelUuid.fromString(ScaleScanner.FE95_UUID)) ?: return@forEach
                    bridge.onAdvertisement(r.device.address, data)
                }
                bridge.flushPending()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.snapcal.app.SCALE_SCAN"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
