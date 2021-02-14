package org.ligi.blexplorer.characteristics

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDevice
import com.uber.autodispose.AutoDispose
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider
import de.cketti.shareintentbuilder.ShareIntentBuilder
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.ligi.blexplorer.App
import org.ligi.blexplorer.R
import org.ligi.blexplorer.bluetoothController
import org.ligi.blexplorer.databinding.ActivityWithRecyclerBinding
import org.ligi.blexplorer.databinding.ItemCharacteristicBinding
import org.ligi.blexplorer.util.DevicePropertiesDescriber
import org.ligi.blexplorer.util.KEY_BLUETOOTH_DEVICE
import org.ligi.blexplorer.util.KEY_SERVICE_UUID
import org.ligi.blexplorer.util.hasAllExtras
import java.math.BigInteger
import java.util.*


class CharacteristicActivity : AppCompatActivity() {
    private lateinit var binding : ActivityWithRecyclerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!intent.hasAllExtras(KEY_BLUETOOTH_DEVICE, KEY_SERVICE_UUID)) {
            finish()
            return
        }
        val device = intent.getParcelableExtra<BluetoothDevice>(KEY_BLUETOOTH_DEVICE)
        val serviceUUID = intent.getStringExtra(KEY_SERVICE_UUID)

        val deviceInfo = bluetoothController.getDeviceInfo(device)
        deviceInfo ?: kotlin.run {
            finish()
            return
        }

        ConnectionStateChangeLiveData(deviceInfo.scanResult.bleDevice).observe(this) { newState ->
            val stateToString = DevicePropertiesDescriber.connectionStateToString(newState, this)
            supportActionBar?.subtitle = "$serviceName ($stateToString)"
        }

        binding = ActivityWithRecyclerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.contentList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        val adapter = CharacteristicRecycler()
        binding.contentList.adapter = adapter

        val autoDisposable = AutoDispose.autoDisposable<BluetoothGattService>(
                AndroidLifecycleScopeProvider.from(this, Lifecycle.Event.ON_DESTROY)
        )

        bluetoothController.getConnection(deviceInfo.scanResult.bleDevice)
                .flatMapSingle { it.discoverServices() }
                .flatMapSingle { it.getService(UUID.fromString(serviceUUID)) }
                .observeOn(AndroidSchedulers.mainThread())
                .`as`(autoDisposable)
                .subscribe { adapter.submitList(it.characteristics) }

        device.connectGatt(this, true, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                App.gatt = gatt
//                gatt.discoverServices()
                super.onConnectionStateChange(gatt, status, newState)
            }

            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                super.onCharacteristicRead(gatt, characteristic, status)
//                characteristicUpdate(characteristic, adapter)

            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                super.onCharacteristicChanged(gatt, characteristic)
//                characteristicUpdate(characteristic, adapter)
            }
        })
    }

//    private fun characteristicUpdate(characteristic: BluetoothGattCharacteristic, adapter: CharacteristicRecycler) {
//        var found: BluetoothGattCharacteristic? = null
//        for (bluetoothGattCharacteristic in characteristicList) {
//            if (bluetoothGattCharacteristic.uuid == characteristic.uuid) {
//                found = bluetoothGattCharacteristic
//            }
//        }
//
//        if (found == null) {
//            characteristicList.add(characteristic)
//            adapter.notifyDataSetChanged()
//        } else {
//            val index = characteristicList.indexOf(found)
//            characteristicList[index] = characteristic
//            runOnUiThread { adapter.notifyItemChanged(index) }
//
//        }
//    }

    private val serviceName: String
        get() = DevicePropertiesDescriber.getServiceName(App.service, App.service.uuid.toString())

    override fun onPause() {
        App.gatt?.disconnect()
        super.onPause()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        finish()
        return super.onOptionsItemSelected(item)
    }

    companion object {
        fun createIntent(context: Context, device: BluetoothDevice, service: BluetoothGattService): Intent = Intent(context, CharacteristicActivity::class.java)
                .putExtra(KEY_BLUETOOTH_DEVICE, device)
                .putExtra(KEY_SERVICE_UUID, service.uuid.toString())
    }
}

private class CharacteristicRecycler : ListAdapter<BluetoothGattCharacteristic, CharacteristicViewHolder>(CharacteristicDiffCallback()) {
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): CharacteristicViewHolder {
        val layoutInflater = LayoutInflater.from(viewGroup.context)
        val binding = ItemCharacteristicBinding.inflate(layoutInflater, viewGroup, false)
        return CharacteristicViewHolder(binding)
    }

    override fun onBindViewHolder(deviceViewHolder: CharacteristicViewHolder, position: Int) {
        deviceViewHolder.applyCharacteristic(getItem(position))
    }
}

private class CharacteristicDiffCallback : DiffUtil.ItemCallback<BluetoothGattCharacteristic>() {
    override fun areItemsTheSame(oldItem: BluetoothGattCharacteristic, newItem: BluetoothGattCharacteristic) =
            oldItem.uuid == newItem.uuid

    override fun areContentsTheSame(oldItem: BluetoothGattCharacteristic, newItem: BluetoothGattCharacteristic) =
            Arrays.equals(oldItem.value, newItem.value)
}

private class ConnectionStateChangeLiveData(private val rxBleDevice: RxBleDevice) : LiveData<RxBleConnection.RxBleConnectionState>() {
    private var disposable : Disposable? = null

    override fun onActive() {
        super.onActive()
        disposable = rxBleDevice.observeConnectionStateChanges()
                .startWith(rxBleDevice.connectionState)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { value = it }
    }

    override fun onInactive() {
        super.onInactive()
        disposable?.dispose()
    }
}

private class CharacteristicViewHolder(private val binding: ItemCharacteristicBinding) : RecyclerView.ViewHolder(binding.root) {

    private var characteristic: BluetoothGattCharacteristic? = null

    fun applyCharacteristic(characteristic: BluetoothGattCharacteristic) {
        this.characteristic = characteristic
        binding.uuid.text = characteristic.uuid.toString()

        if (characteristic.value != null) {
            binding.value.text = getValue(characteristic)
        } else {
            binding.value.text = itemView.context.getString(R.string.gatt_characteristic_no_value_msg)
        }
        binding.type.text = DevicePropertiesDescriber.getProperty(characteristic)
        binding.permissions.text = DevicePropertiesDescriber.getPermission(characteristic) + "  " + characteristic.descriptors.size

        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) {
            binding.notify.visibility = View.VISIBLE
            binding.notify.isChecked = App.notifyingCharacteristicsUUids.contains(characteristic.uuid)
        } else {
            binding.notify.visibility = View.GONE
        }

        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ > 0) {
            binding.read.visibility = View.VISIBLE
        } else {
            binding.read.visibility = View.GONE
        }

        binding.read.setOnClickListener {
            App.gatt.readCharacteristic(characteristic)
        }

        binding.share.setOnClickListener {
            val activity = binding.root.context as Activity
            var text = "characteristic UUID: " + characteristic.uuid.toString() + "\n"
            text += "service UUID: " + characteristic.service.uuid.toString() + "\n"
            if (characteristic.value != null) {
                text += "value: " + getValue(characteristic)
            }
            activity.startActivity(ShareIntentBuilder.from(activity).text(text).build())

        }

        binding.notify.setOnCheckedChangeListener { compoundButton, check ->
            if (check) {
                if (!App.notifyingCharacteristicsUUids.contains(characteristic.uuid)) {
                    App.notifyingCharacteristicsUUids.add(characteristic.uuid)
                }
            } else {
                App.notifyingCharacteristicsUUids.remove(characteristic.uuid)
            }

            if (!App.gatt.setCharacteristicNotification(characteristic, check)) {
                Toast.makeText(itemView.context, "setCharacteristicNotification returned false", Toast.LENGTH_LONG).show()
            } else {

                val descriptor = characteristic.descriptors[0]
                descriptor.value = if (check) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                if (!App.gatt.writeDescriptor(descriptor)) {
                    Toast.makeText(itemView.context, "Could not write descriptor for notification", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    private fun getValue(characteristic: BluetoothGattCharacteristic): String {
        return BigInteger(1, characteristic.value).toString(16) +
                " = " +
                characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0) +
                " = " +
                characteristic.getStringValue(0)
    }

}