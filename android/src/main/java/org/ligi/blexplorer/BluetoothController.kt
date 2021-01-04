package org.ligi.blexplorer

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import androidx.collection.ArrayMap
import androidx.lifecycle.LiveData
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.scan.ScanResult
import com.polidea.rxandroidble2.scan.ScanSettings
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import org.ligi.blexplorer.scan.DeviceInfo

internal class BluetoothController(context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    internal val rxBleClient = RxBleClient.create(context)

    fun isBluetoothEnabled() : Boolean {
        bluetoothManager.adapter ?: return false
        return bluetoothManager.adapter.isEnabled
    }

    internal fun deviceListLiveData() : LiveData<List<DeviceInfo>> = DeviceListLiveData()

    internal fun bluetoothStateEvents() : Observable<RxBleClient.State> = rxBleClient.observeStateChanges()
                                                     .startWith(rxBleClient.state)
                                                     .replay(1)
                                                     .autoConnect()
}

private class DeviceListLiveData : LiveData<List<DeviceInfo>>() {
    private val errorHandler: Consumer<in Throwable> = Consumer {
        Log.e("bluetooth_scan", "Exception occurred while scanning for BLE devices", it)
    }

    private val scanResultHandler: Consumer<in ScanResult> = Consumer {
        val device = it.bleDevice.bluetoothDevice
        devices[device] = DeviceInfo(device, it.rssi, it.scanRecord.bytes)
        postValue(devices.values.toList())
    }

    private val devices: MutableMap<BluetoothDevice, DeviceInfo> = ArrayMap()
    private var disposable : Disposable? = null

    override fun onActive() {
        super.onActive()
        disposable = bluetoothController.rxBleClient.scanBleDevices(ScanSettings.Builder().build())
                .subscribe(scanResultHandler, errorHandler)
    }

    override fun onInactive() {
        super.onInactive()
        disposable?.dispose()
    }
}