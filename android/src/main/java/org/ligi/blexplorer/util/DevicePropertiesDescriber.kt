package org.ligi.blexplorer.util

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.text.TextUtils
import com.polidea.rxandroidble2.utils.StandardUUIDsParser

object DevicePropertiesDescriber {

    fun describeBondState(device: BluetoothDevice) = when (device.bondState) {
        BluetoothDevice.BOND_NONE -> "not bonded"
        BluetoothDevice.BOND_BONDING -> "bonding"
        BluetoothDevice.BOND_BONDED -> "bonded"
        else -> "unknown bondstate"
    }


    fun describeType(device: BluetoothDevice) = when (device.type) {
        BluetoothDevice.DEVICE_TYPE_CLASSIC -> "classic"
        BluetoothDevice.DEVICE_TYPE_DUAL -> "dual"
        BluetoothDevice.DEVICE_TYPE_LE -> "LE"
        BluetoothDevice.DEVICE_TYPE_UNKNOWN -> "unknown device type"
        else -> "unknown device type"
    }


    fun getNameOrAddressAsFallback(device: BluetoothDevice) = if (TextUtils.isEmpty(device.name)) device.address else device.name


    fun describeServiceType(service: BluetoothGattService) = when (service.type) {
        BluetoothGattService.SERVICE_TYPE_PRIMARY -> "primary"
        BluetoothGattService.SERVICE_TYPE_SECONDARY -> "secondary"
        else -> "unknown service type"
    }


    fun getPermission(from: BluetoothGattCharacteristic) = when (from.permissions) {
        BluetoothGattCharacteristic.PERMISSION_READ -> "read"

        BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED -> "read encrypted"

        BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM -> "read encrypted mitm"

        BluetoothGattCharacteristic.PERMISSION_WRITE -> "write"

        BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED -> "write encrypted"

        BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM -> "write encrypted mitm"

        BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED -> "write signed"

        BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED_MITM -> "write signed mitm"

        else -> "unknown permission ${from.permissions}"
    }

    val property2stringMap = mapOf(
            BluetoothGattCharacteristic.PROPERTY_BROADCAST to "boadcast",
            BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS to "extended",
            BluetoothGattCharacteristic.PROPERTY_INDICATE to "indicate",
            BluetoothGattCharacteristic.PROPERTY_NOTIFY to "notify",
            BluetoothGattCharacteristic.PROPERTY_READ to "read",
            BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE to "signed write",
            BluetoothGattCharacteristic.PROPERTY_WRITE to "write",
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE to "write no response"
    )

    fun getProperty(from: BluetoothGattCharacteristic): String {

        val res = property2stringMap.keys
                .filter { from.properties and it > 0 }
                .map { property2stringMap[it] }
                .joinToString(",")

        return if (res.isEmpty()) {
            "no property"
        } else {
            res
        }
    }

    fun getServiceName(service: BluetoothGattService, defaultString: String): String {
        return StandardUUIDsParser.getServiceName(service.uuid) ?: defaultString
    }

    fun connectionStateToString(state: Int) = when (state) {
        BluetoothProfile.STATE_DISCONNECTED -> "disconnected"
        BluetoothProfile.STATE_CONNECTING -> "connecting"
        BluetoothProfile.STATE_CONNECTED -> "connected"
        BluetoothProfile.STATE_DISCONNECTING -> "disconnecting"
        else -> "unknown state:" + state

    }
    /*
    public static String statusToString(int status) {
        switch (status){
            case BluetoothGatt.GATT_SUCCESS:
                return "GATT_SUCCESS";
            case BluetoothGatt.GATT_READ_NOT_PERMITTED:
                return "GATT_READ_NOT_PERMITTED";
            case BluetoothGatt.GATT_WRITE_NOT_PERMITTED:
                return "GATT_WRITE_NOT_PERMITTED";
            case BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED:
                return "GATT_REQUEST_NOT_SUPPORTED";
            case BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION:
                return "GATT_INSUFFICIENT_AUTHENTICATION";
            case BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION:
                return "GATT_INSUFFICIENT_ENCRYPTION";
            case BluetoothGatt.GATT_INVALID_OFFSET:
                return "GATT_INVALID_OFFSET";
            case BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH:
                return "GATT_INVALID_ATTRIBUTE_LENGTH";
            case BluetoothGatt.GATT_FAILURE:
                return "GATT_FAILURE";
            default:
                return "unknown state:" + status;
        }
    }
*/
}
