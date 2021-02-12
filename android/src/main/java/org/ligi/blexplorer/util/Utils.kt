package org.ligi.blexplorer.util

internal const val KEY_BLUETOOTH_DEVICE = "bluetooth_device"

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