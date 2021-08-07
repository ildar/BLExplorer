package org.ligi.blexplorer.ui

import android.app.Dialog
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.polidea.rxandroidble2.RxBleDevice
import com.uber.autodispose.AutoDispose
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import net.steamcrafted.loadtoast.LoadToast
import org.ligi.blexplorer.R
import org.ligi.blexplorer.bluetoothController
import org.ligi.blexplorer.databinding.ActivityWithRecyclerBinding
import org.ligi.blexplorer.databinding.ItemServiceBinding
import org.ligi.blexplorer.util.DevicePropertiesDescriber
import org.ligi.blexplorer.util.KEY_BLUETOOTH_DEVICE
import org.ligi.snackengage.SnackEngage
import org.ligi.snackengage.snacks.DefaultRateSnack
import timber.log.Timber


class DeviceServiceExploreActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWithRecyclerBinding
    private lateinit var device: BluetoothDevice
    private var gattServicesListDisposable : Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if(!intent.hasExtra(KEY_BLUETOOTH_DEVICE)) {
            finish()
            return
        }
        device = intent.getParcelableExtra<BluetoothDevice>(KEY_BLUETOOTH_DEVICE) as BluetoothDevice

        binding = ActivityWithRecyclerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.run {
            subtitle = DevicePropertiesDescriber.getNameOrAddressAsFallback(device)
            setDisplayHomeAsUpEnabled(true)
        }

        SnackEngage.from(this).withSnack(DefaultRateSnack()).build().engageWhenAppropriate()

        binding.contentList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        val adapter = ServiceRecycler(device)
        binding.contentList.adapter = adapter

        val rxbleDevice = bluetoothController.getDeviceInfo(device)?.scanResult?.bleDevice
        rxbleDevice ?: run {
            finish()
            return
        }

        val loadToast = LoadToast(this)

        val actDestScopeProvider = AndroidLifecycleScopeProvider.from(this, Lifecycle.Event.ON_DESTROY)

        //keeping the BLE device connection open so that actions in CharacteristicActivity are faster
        bluetoothController.getConnection(rxbleDevice)
                .`as`(AutoDispose.autoDisposable(actDestScopeProvider))
                .subscribe({}, {})

        gattServicesListDisposable = Completable.fromAction { loadToast.setText(getString(R.string.connecting)).show() }
                .subscribeOn(AndroidSchedulers.mainThread())
                .andThen(bluetoothController.getConnection(rxbleDevice))
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext {
                    Completable.fromAction { loadToast.setText(getString(R.string.discovering)) }
                            .subscribeOn(AndroidSchedulers.mainThread()).subscribe()
                }.flatMapSingle { it.discoverServices() }
                .take(1)
                .observeOn(AndroidSchedulers.mainThread())
                .`as`(AutoDispose.autoDisposable(actDestScopeProvider))
                .subscribe(
                        { services ->
                            adapter.submitList(services.bluetoothGattServices)
                            loadToast.success()
                        },
                        { throwable ->
                            Timber.e(throwable, "Failed to discover services for device ${rxbleDevice.bluetoothDevice.name}")
                            supportFragmentManager.beginTransaction().add(ExitOnDismissAlertDialog(rxbleDevice), ExitOnDismissAlertDialog.TAG).commit()
                        }
                )

        supportFragmentManager.beginTransaction()
                .add(ExitActivityOnBluetoothDisable(), null)
                .commit()
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    companion object {
        fun createIntent(context : Context, device : BluetoothDevice) : Intent = Intent(context, DeviceServiceExploreActivity::class.java)
                .putExtra(KEY_BLUETOOTH_DEVICE, device)
    }
}

private class ServiceRecycler(private val device: BluetoothDevice) : ListAdapter<BluetoothGattService, ServiceViewHolder>(BluetoothGattServiceDiffCallback()) {
    override fun onCreateViewHolder(viewGroup: ViewGroup, position: Int): ServiceViewHolder {
        val layoutInflater = LayoutInflater.from(viewGroup.context)
        val binding = ItemServiceBinding.inflate(layoutInflater, viewGroup, false)
        return ServiceViewHolder(binding)
    }

    override fun onBindViewHolder(deviceViewHolder: ServiceViewHolder, position: Int) {
        val service = getItem(position)
        deviceViewHolder.applyService(device, service)
    }
}

private class BluetoothGattServiceDiffCallback : DiffUtil.ItemCallback<BluetoothGattService>() {
    override fun areItemsTheSame(oldItem: BluetoothGattService, newItem: BluetoothGattService) = oldItem.uuid == newItem.uuid

    override fun areContentsTheSame(oldItem: BluetoothGattService, newItem: BluetoothGattService) = true
}

private class ServiceViewHolder(private val binding: ItemServiceBinding) : RecyclerView.ViewHolder(binding.root) {

    fun applyService(device: BluetoothDevice, service: BluetoothGattService) {
        itemView.setOnClickListener { v ->
            val intent = CharacteristicActivity.createIntent(v.context, device, service)
            v.context.startActivity(intent)
        }
        binding.uuid.text = service.uuid.toString()
        binding.type.text = DevicePropertiesDescriber.describeServiceType(service)
        binding.name.text = DevicePropertiesDescriber.getServiceName(service, "unknown")
    }
}

class ExitOnDismissAlertDialog(private val rxbleDevice: RxBleDevice) : DialogFragment() {
    companion object {
        const val TAG = "exit_on_dismiss_alert_dialog"
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        requireActivity().finish()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        requireActivity().finish()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
                .setTitle(R.string.error)
                .setMessage(getString(R.string.device_connect_failed_dialog_text, rxbleDevice.bluetoothDevice.name))
                .setPositiveButton(android.R.string.ok) { _,_  -> requireActivity().finish() }
                .create()
    }
}