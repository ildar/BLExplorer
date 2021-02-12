package org.ligi.blexplorer.util

import android.content.Intent

internal const val KEY_BLUETOOTH_DEVICE = "bluetooth_device"
internal const val KEY_SERVICE_UUID = "service_uuid"

internal class MyOptional<T> {
    private var value : T? = null

    fun orElseGet(newValue : T) : T {
        val temp = value
        if(temp != null) return temp

        value = newValue
        return newValue
    }

    fun set(newValue : T?) { value = newValue }
}

fun Intent.hasAllExtras(vararg keys : String) : Boolean {
    for(key in keys) {
        if(!hasExtra(key)) return false
    }
    return true
}