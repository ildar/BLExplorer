package org.ligi.blexplorer

import android.app.Application
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService
import org.ligi.tracedroid.TraceDroid
import java.util.*

class App : Application() {
    override fun onCreate() {
        TraceDroid.init(this)
        super.onCreate()

        bluetoothController = BluetoothController(this)
    }

    companion object {
        lateinit var gatt: BluetoothGatt
        lateinit var service: BluetoothGattService
        var notifyingCharacteristicsUUids: MutableList<UUID> = ArrayList()
    }
}

internal lateinit var bluetoothController: BluetoothController
    private set