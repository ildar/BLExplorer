package org.ligi.blexplorer

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.collection.ArrayMap
import androidx.lifecycle.LiveData
import org.ligi.blexplorer.scan.DeviceInfo

internal class BluetoothController(context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    fun isBluetoothAvailable() = bluetoothManager.adapter != null

    fun isBluetoothEnabled() : Boolean {
        bluetoothManager.adapter ?: return false
        return bluetoothManager.adapter.isEnabled
    }

    internal fun bluetoothAdapter() : BluetoothAdapter? = bluetoothManager.adapter

    internal val deviceListLiveData : LiveData<List<DeviceInfo>> = DeviceListLiveData()
}

private class DeviceListLiveData : LiveData<List<DeviceInfo>>(), BluetoothAdapter.LeScanCallback {
    private val devices: MutableMap<BluetoothDevice, DeviceInfo> = ArrayMap()

    override fun onActive() {
        super.onActive()
        bluetoothController.bluetoothAdapter()?.startLeScan( this)
    }

    override fun onInactive() {
        super.onInactive()
        bluetoothController.bluetoothAdapter()?.stopLeScan(this)
    }

    override fun onLeScan(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray) {
        devices[device] = DeviceInfo(device, rssi, scanRecord)
        postValue(devices.values.toList())
    }
}