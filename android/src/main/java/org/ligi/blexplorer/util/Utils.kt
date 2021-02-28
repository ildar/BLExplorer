package org.ligi.blexplorer.util

import android.content.Intent

internal const val KEY_BLUETOOTH_DEVICE = "bluetooth_device"
internal const val KEY_SERVICE_UUID = "service_uuid"

internal class AtomicOptional<T> {
    private var value : T? = null

    @Synchronized
    fun orElseGet(newValue : T) : T {
        val oldValue = value
        if(oldValue != null) return oldValue

        value = newValue
        return newValue
    }

    @Synchronized
    fun compareAndSet(expectedValue : T?, newValue : T?) {
        if(value === expectedValue) value = newValue
    }
}

fun Intent.hasAllExtras(vararg keys : String) : Boolean {
    for(key in keys) {
        if(!hasExtra(key)) return false
    }
    return true
}