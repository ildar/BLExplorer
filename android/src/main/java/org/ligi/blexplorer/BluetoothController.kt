package org.ligi.blexplorer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import androidx.collection.ArrayMap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.jakewharton.rx.replayingShare
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDevice
import com.polidea.rxandroidble2.scan.ScanResult
import com.polidea.rxandroidble2.scan.ScanSettings
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.ligi.blexplorer.util.MyOptional
import java.util.*

internal class BluetoothController(context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val rxBleClient = RxBleClient.create(context)

    private val deviceMap: MutableMap<BluetoothDevice, DeviceInfo> = ArrayMap()

    internal fun getDeviceInfo(device : BluetoothDevice) = deviceMap[device]

    internal fun getConnection(rxbleDevice : RxBleDevice) : Observable<RxBleConnection> {
        val deviceInfo = deviceMap[rxbleDevice.bluetoothDevice] as DeviceInfo
        synchronized(deviceInfo) {
            val newValue = rxbleDevice.establishConnection(false)
                    .doOnDispose { synchronized(deviceInfo) { deviceInfo.connection.set(null) } }
                    .replayingShare()

            return deviceInfo.connection.orElseGet(newValue)
        }
    }

    fun isBluetoothEnabled() : Boolean {
        bluetoothManager.adapter ?: return false
        return bluetoothManager.adapter.isEnabled
    }

    internal val bluetoothStateEvents = rxBleClient.observeStateChanges()
                                                     .startWith(rxBleClient.state)
                                                     .replay(1)
                                                     .autoConnect()

    internal val deviceListLiveData : LiveData<List<DeviceInfo>> = DeviceListLiveData()

    @SuppressLint("CheckResult")
    private inner class DeviceListLiveData : MutableLiveData<List<DeviceInfo>>() {
        private val shouldScanObservable : Observable<Boolean> = bluetoothStateEvents.map { state ->
            return@map when(state) {
                RxBleClient.State.BLUETOOTH_NOT_ENABLED,
                RxBleClient.State.BLUETOOTH_NOT_AVAILABLE,
                RxBleClient.State.LOCATION_PERMISSION_NOT_GRANTED,
                RxBleClient.State.LOCATION_SERVICES_NOT_ENABLED -> false
                RxBleClient.State.READY -> true
            }
        }

        private var scanDisposable : Disposable? = null

        init {
            shouldScanObservable.observeOn(AndroidSchedulers.mainThread())
                    .subscribe { shouldScan ->
                        if(shouldScan) {
                            val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
                            scanDisposable = rxBleClient.scanBleDevices(scanSettings)
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe({
                                        val device = it.bleDevice.bluetoothDevice
                                        deviceMap[device] = DeviceInfo(it)
                                        value = deviceMap.values.toList()
                                    },
                                    {
                                        Log.e("bluetooth_scan", "Exception occurred while scanning for BLE devices", it)
                                    })
                        } else {
                            scanDisposable?.dispose()
//                            deviceMap.clear()
//                            value = emptyList()
                            scanDisposable = null
                        }
                    }
        }
    }
}

internal data class DeviceInfo(val scanResult: ScanResult) {
    val last_seen: Long = System.currentTimeMillis()
    val connection = MyOptional<Observable<RxBleConnection>>()
}

