package org.ligi.blexplorer

import android.app.Application
import org.ligi.tracedroid.TraceDroid
import timber.log.Timber

class App : Application() {
    override fun onCreate() {
        TraceDroid.init(this)
        super.onCreate()

        if(BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        bluetoothController = BluetoothController(this)
    }
}

internal lateinit var bluetoothController: BluetoothController
    private set