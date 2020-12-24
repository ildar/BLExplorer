package org.ligi.blexplorer

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context

internal class BluetoothController(context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    fun isBluetoothAvailable() = bluetoothManager.adapter != null

    fun isBluetoothEnabled() : Boolean {
        bluetoothManager.adapter ?: return false
        return bluetoothManager.adapter.isEnabled
    }

    fun bluetoothAdapter() : BluetoothAdapter? = bluetoothManager.adapter
}