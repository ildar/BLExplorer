package org.ligi.blexplorer.ui

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.polidea.rxandroidble2.NotificationSetupMode
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDevice
import com.uber.autodispose.AutoDispose
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider
import de.cketti.shareintentbuilder.ShareIntentBuilder
import io.reactivex.BackpressureStrategy
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
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

        binding = ActivityWithRecyclerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.contentList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        val adapter = CharacteristicRecycler(deviceInfo.scanResult.bleDevice)
        binding.contentList.adapter = adapter

        val autoDisposable = AutoDispose.autoDisposable<BluetoothGattService>(
                AndroidLifecycleScopeProvider.from(this, Lifecycle.Event.ON_DESTROY)
        )

        bluetoothController.getConnection(deviceInfo.scanResult.bleDevice)
                .flatMapSingle { it.discoverServices() }
                .flatMapSingle { it.getService(UUID.fromString(serviceUUID)) }
                .observeOn(AndroidSchedulers.mainThread())
                .`as`(autoDisposable)
                .subscribe(
                {
                    val serviceName = DevicePropertiesDescriber.getServiceName(it, it.uuid.toString())
                    ConnectionStateChangeLiveData(deviceInfo.scanResult.bleDevice).observe(this) { newState ->
                        val stateToString = DevicePropertiesDescriber.connectionStateToString(newState, this)
                        supportActionBar?.subtitle = "$serviceName ($stateToString)"
                    }
                    adapter.submitList(it.characteristics)
                },
                {
                    Toast.makeText(this@CharacteristicActivity, R.string.characteristic_list_load_error_msg, Toast.LENGTH_SHORT).show()
                    finish()
                }
                )

        supportFragmentManager.beginTransaction()
                .add(ExitActivityOnBluetoothDisable(), null)
                .commit()
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

private class CharacteristicRecycler(private val bleDevice: RxBleDevice) : ListAdapter<BluetoothGattCharacteristic, CharacteristicViewHolder>(CharacteristicDiffCallback()) {
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): CharacteristicViewHolder {
        val layoutInflater = LayoutInflater.from(viewGroup.context)
        val binding = ItemCharacteristicBinding.inflate(layoutInflater, viewGroup, false)
        return CharacteristicViewHolder(binding)
    }

    override fun onBindViewHolder(deviceViewHolder: CharacteristicViewHolder, position: Int) {
        deviceViewHolder.applyCharacteristic(getItem(position), bleDevice)
    }

    override fun onViewDetachedFromWindow(holder: CharacteristicViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.viewDetached()
    }

    override fun onViewAttachedToWindow(holder: CharacteristicViewHolder) {
        super.onViewAttachedToWindow(holder)
        val itemPos = holder.bindingAdapterPosition
        if(itemPos != RecyclerView.NO_POSITION) {
            holder.viewAttached(getItem(itemPos), bleDevice)
        }
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
    private var readValueDisposable : Disposable? = null
    private var notifyActionDisposable : Disposable? = null

    @MainThread
    fun viewDetached() {
        readValueDisposable?.dispose()
        readValueDisposable = null

        notifyActionDisposable?.dispose()
        notifyActionDisposable = null
    }

    @MainThread
    fun viewAttached(characteristic: BluetoothGattCharacteristic, bleDevice: RxBleDevice) {
        if(binding.notify.isChecked) setupCharacteristicValueNotification(bleDevice, characteristic)
    }

    @MainThread
    fun applyCharacteristic(characteristic: BluetoothGattCharacteristic, bleDevice: RxBleDevice) {
        binding.name.text = DevicePropertiesDescriber.getCharacteristicName(characteristic, binding.root.context.getString(R.string.unknown))
        binding.uuid.text = characteristic.uuid.toString()

        displayCharacteristicValue(characteristic)
        binding.type.text = DevicePropertiesDescriber.getProperty(characteristic)
        binding.permissions.text = DevicePropertiesDescriber.getPermission(characteristic) + "  " + characteristic.descriptors.size

        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) {
            binding.notify.visibility = View.VISIBLE
        } else {
            binding.notify.visibility = View.GONE
        }

        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ > 0) {
            binding.read.visibility = View.VISIBLE
        } else {
            binding.read.visibility = View.GONE
        }

        binding.read.setOnClickListener {
            readValueDisposable = bluetoothController.getConnection(bleDevice)
                    .flatMapSingle { it.readCharacteristic(characteristic.uuid) }
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe (
                            { displayCharacteristicValue(characteristic) },
                            { Toast.makeText(itemView.context, itemView.context.getString(R.string.characteristic_read_failed_message, characteristic.uuid.toString()), Toast.LENGTH_SHORT).show() }
                    )
        }

        binding.share.setOnClickListener {
            val context = binding.root.context
            var text = "characteristic UUID: ${characteristic.uuid}\n"
            text += "service UUID: ${characteristic.service.uuid}\n"
            if (characteristic.value != null) {
                text += "value: ${getValue(characteristic)}"
            }
            context.startActivity(ShareIntentBuilder.from(context).text(text).build())
        }

        binding.notify.setOnCheckedChangeListener { compoundButton, check ->
            if(check) {
                setupCharacteristicValueNotification(bleDevice, characteristic)
            } else {
                notifyActionDisposable?.dispose()
                notifyActionDisposable = null
            }
        }
    }

    private fun setupCharacteristicValueNotification(bleDevice: RxBleDevice, characteristic: BluetoothGattCharacteristic) {
        notifyActionDisposable = bluetoothController.getConnection(bleDevice)
                .flatMap { connection -> connection.setupNotification(characteristic, NotificationSetupMode.COMPAT) }
                .flatMap { notifObservable -> notifObservable }
                .toFlowable(BackpressureStrategy.LATEST)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { bytes ->
                            displayCharacteristicValue(characteristic)
                        },
                        { throwable ->
                            binding.notify.isChecked = false
                            Toast.makeText(binding.root.context,
                                    binding.root.context.getString(R.string.characteristic_notification_setup_fail_msg, characteristic.uuid.toString()),
                                    Toast.LENGTH_SHORT)
                                    .show()
                        }
                )
    }

    @UiThread
    private fun displayCharacteristicValue(characteristic: BluetoothGattCharacteristic) {
        if (characteristic.value != null) {
            binding.value.text = getValue(characteristic)
        } else {
            binding.value.text = itemView.context.getString(R.string.gatt_characteristic_no_value_msg)
        }
    }


    private fun getValue(characteristic: BluetoothGattCharacteristic): String =
            BigInteger(1, characteristic.value).toString(16) +
                    " = " +
                    characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0) +
                    " = " +
                    characteristic.getStringValue(0)
}