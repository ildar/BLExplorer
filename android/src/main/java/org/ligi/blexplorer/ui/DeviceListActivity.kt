package org.ligi.blexplorer.ui

import android.Manifest
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.scan.ScanRecord
import io.reactivex.BackpressureStrategy
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.ligi.blexplorer.DeviceInfo
import org.ligi.blexplorer.HelpActivity
import org.ligi.blexplorer.R
import org.ligi.blexplorer.bluetoothController
import org.ligi.blexplorer.databinding.ActivityWithRecyclerBinding
import org.ligi.blexplorer.databinding.ItemDeviceBinding
import org.ligi.blexplorer.util.DevicePropertiesDescriber
import org.ligi.blexplorer.util.ManufacturerRecordParserFactory
import org.ligi.tracedroid.sending.TraceDroidEmailSender
import java.math.BigInteger

class DeviceListActivity : AppCompatActivity() {


    private lateinit var binding : ActivityWithRecyclerBinding
    private lateinit var viewModel: DeviceListViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        TraceDroidEmailSender.sendStackTraces(BUG_REPORT_EMAIL, this)

        binding = ActivityWithRecyclerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val adapter = DeviceRecycler()

        binding.contentList.layoutManager = LinearLayoutManager(this)
        binding.contentList.adapter = adapter

        viewModel = ViewModelProvider(this).get(DeviceListViewModel::class.java)
        viewModel.deviceListLiveData.observe(this) { adapter.submitList(it) }

        val requestLocPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
        viewModel.bluetoothStateLiveData.observe(this) { state ->
            @Suppress("NON_EXHAUSTIVE_WHEN")
            when(state) {
                RxBleClient.State.BLUETOOTH_NOT_ENABLED -> {
                    if (!bluetoothController.isBluetoothEnabled()) {
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        startActivity(enableBtIntent)
                    }
                }
                RxBleClient.State.LOCATION_PERMISSION_NOT_GRANTED -> {
                    if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION))
                    { LocPermExplanationDialog(requestLocPermLauncher).show(supportFragmentManager, null) }
                    else { requestLocPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                }
                RxBleClient.State.LOCATION_SERVICES_NOT_ENABLED -> {
                    LocServiceEnableDialog().show(supportFragmentManager, null)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        startActivity(Intent(this, HelpActivity::class.java))
        return super.onOptionsItemSelected(item)
    }
}

class LocPermExplanationDialog(private val requestLocPermLauncher: ActivityResultLauncher<String>) : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
                .setMessage(R.string.loc_perm_explanation)
                .setPositiveButton(android.R.string.ok) { _,_ ->requestLocPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)  }
                .create()
    }
}

class LocServiceEnableDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
                .setMessage(R.string.loc_service_enable_dialog_msg)
                .setPositiveButton(R.string.open_location_settings) { _, _ ->
                    val enableLocationIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    startActivity(enableLocationIntent)
                }.setNegativeButton(android.R.string.cancel) { _,_ -> }
                .create()
    }
}

private class DeviceListDiffCallback : DiffUtil.ItemCallback<DeviceListDeviceInfo>() {
    override fun areItemsTheSame(oldItem: DeviceListDeviceInfo, newItem: DeviceListDeviceInfo): Boolean =
            oldItem.deviceInfo.scanResult.bleDevice.bluetoothDevice == newItem.deviceInfo.scanResult.bleDevice.bluetoothDevice

    override fun areContentsTheSame(oldItem: DeviceListDeviceInfo, newItem: DeviceListDeviceInfo): Boolean {
        if(oldItem.deviceInfo.scanResult.rssi != newItem.deviceInfo.scanResult.rssi) return false
        if(!oldItem.deviceInfo.scanResult.scanRecord.bytes.contentEquals(newItem.deviceInfo.scanResult.scanRecord.bytes)) return false
        return oldItem.deviceInfo.last_seen == newItem.deviceInfo.last_seen
    }
}

class DeviceListViewModel : ViewModel() {
    private val deviceListData = DeviceListLiveData()
    internal val deviceListLiveData : LiveData<List<DeviceListDeviceInfo>> = deviceListData
    internal val bluetoothStateLiveData : LiveData<RxBleClient.State> = BluetoothStateChangeLiveData()

    override fun onCleared() {
        super.onCleared()
        deviceListData.onCleared()
    }
}

private class DeviceListLiveData : LiveData<List<DeviceListDeviceInfo>>() {
    private var disposable : Disposable = bluetoothController.deviceListObservable
            .toFlowable(BackpressureStrategy.LATEST)
            .observeOn(Schedulers.computation())
            .map {
                val newList = mutableListOf<DeviceListDeviceInfo>()
                for(item in it) {
                    val scanRecordString = parseScanRecord(item.scanResult.scanRecord, item.scanResult.bleDevice.bluetoothDevice)
                    newList.add(DeviceListDeviceInfo(item, scanRecordString))
                }
                return@map newList
            }.observeOn(AndroidSchedulers.mainThread())
            .subscribe { value = it }

    fun onCleared() {
        disposable.dispose()
    }
}

private class BluetoothStateChangeLiveData : LiveData<RxBleClient.State>() {
    private var disposable : Disposable? = null

    override fun onActive() {
        super.onActive()
        disposable = bluetoothController.bluetoothStateEvents
                                        .toFlowable(BackpressureStrategy.LATEST)
                                        .subscribe { postValue(it) }
    }

    override fun onInactive() {
        super.onInactive()
        disposable?.dispose()
    }
}

private class DeviceViewHolder(private val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root) {

    lateinit var device: BluetoothDevice

    fun applyDevice(deviceInfo: DeviceListDeviceInfo) {
        val newDeviceInfo = deviceInfo.deviceInfo
        device = newDeviceInfo.scanResult.bleDevice.bluetoothDevice
        binding.name.text =
            if (TextUtils.isEmpty(device.name)) itemView.context.getString(R.string.device_name_missing_placeholder_string) else device.name
        binding.rssi.text =
            itemView.context.getString(R.string.rssi_string, newDeviceInfo.scanResult.rssi)
        val lastSeenDuration = (System.currentTimeMillis() - newDeviceInfo.last_seen) / 1000
        binding.lastSeen.text =
            itemView.context.getString(R.string.last_seen_string, lastSeenDuration)
        binding.address.text = device.address
        binding.scanRecord.text = deviceInfo.scanRecordString
        binding.type.text = DevicePropertiesDescriber.describeType(device)
        binding.bondstate.text = DevicePropertiesDescriber.describeBondState(device)
    }

    fun installOnClickListener() {
        itemView.setOnClickListener {
            val intent = DeviceServiceExploreActivity.createIntent(it.context, device)
            it.context.startActivity(intent)
        }
    }
}

private fun parseScanRecord(scanRecord: ScanRecord, device: BluetoothDevice): String {
    val scanRecordStr = StringBuilder()
    scanRecord.serviceUuids?.let { uuids ->
        for (parcelUuid in uuids) {
            scanRecordStr.append("$parcelUuid\n")
        }
    }

    val manufacturerSpecificData = scanRecord.manufacturerSpecificData

    (0 until manufacturerSpecificData.size())
        .map { manufacturerSpecificData.keyAt(it) }
        .forEach { key ->
            val p = ManufacturerRecordParserFactory.parse(
                key,
                manufacturerSpecificData.get(key),
                device
            )
            if (p == null) {
                scanRecordStr.append(
                    "$key=${
                        BigInteger(
                            1,
                            manufacturerSpecificData.get(key)
                        ).toString(16)
                    }\n"
                )
            } else {
                scanRecordStr.append("${p.keyDescriptor} = {\n$p}\n")
            }
        }

    for (parcelUuid in scanRecord.serviceData.keys) {
        scanRecordStr.append(
            "$parcelUuid=${
                BigInteger(
                    1,
                    scanRecord.serviceData[parcelUuid]
                ).toString(16)
            }\n"
        )
    }
    return scanRecordStr.toString()
}

private class DeviceRecycler : ListAdapter<DeviceListDeviceInfo, DeviceViewHolder>(DeviceListDiffCallback()) {
    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): DeviceViewHolder {
        val layoutInflater = LayoutInflater.from(viewGroup.context)
        val binding = ItemDeviceBinding.inflate(layoutInflater, viewGroup, false)
        return DeviceViewHolder(binding).apply { installOnClickListener() }
    }

    override fun onBindViewHolder(deviceViewHolder: DeviceViewHolder, position: Int) {
        val bluetoothDeviceInfo = getItem(position)
        deviceViewHolder.applyDevice(bluetoothDeviceInfo)
    }
}

internal data class DeviceListDeviceInfo(val deviceInfo: DeviceInfo, val scanRecordString : String)