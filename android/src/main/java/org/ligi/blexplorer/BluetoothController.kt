package org.ligi.blexplorer

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import androidx.collection.ArrayMap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.scan.ScanResult
import com.polidea.rxandroidble2.scan.ScanSettings

internal class BluetoothController(context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val rxBleClient = RxBleClient.create(context)

    private val deviceMap: MutableMap<BluetoothDevice, DeviceScanResult> = ArrayMap()

    init {
        rxBleClient.scanBleDevices(ScanSettings.Builder().build())
                .subscribe(
                {
                    val device = it.bleDevice.bluetoothDevice
                    deviceMap[device] = DeviceScanResult(it)
                    deviceListData.postValue(deviceMap.values.toList())
                },
                {
                    Log.e("bluetooth_scan", "Exception occurred while scanning for BLE devices", it)
                })
    }

    fun isBluetoothEnabled() : Boolean {
        bluetoothManager.adapter ?: return false
        return bluetoothManager.adapter.isEnabled
    }

    private val deviceListData = MutableLiveData<List<DeviceScanResult>>()
    internal val deviceListLiveData : LiveData<List<DeviceScanResult>> = deviceListData

    internal val bluetoothStateEvents = rxBleClient.observeStateChanges()
                                                     .startWith(rxBleClient.state)
                                                     .replay(1)
                                                     .autoConnect()
}

internal data class DeviceScanResult(val scanResult: ScanResult) {
    val last_seen: Long = System.currentTimeMillis()
}