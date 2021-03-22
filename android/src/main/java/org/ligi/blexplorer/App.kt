package org.ligi.blexplorer

import androidx.multidex.MultiDexApplication
import org.ligi.tracedroid.TraceDroid
import timber.log.Timber

class App : MultiDexApplication() {
    override fun onCreate() {
        TraceDroid.init(this)
        super.onCreate()

        if(BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        bluetoothController = BluetoothController(this)
    }
}

internal lateinit var bluetoothController: BluetoothController
    private set