package org.ligi.blexplorer

import android.app.Application
import org.ligi.tracedroid.TraceDroid

class App : Application() {
    override fun onCreate() {
        TraceDroid.init(this)
        super.onCreate()

        bluetoothController = BluetoothController(this)
    }
}

internal lateinit var bluetoothController: BluetoothController
    private set