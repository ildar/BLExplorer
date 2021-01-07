package org.ligi.blexplorer.scan

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.polidea.rxandroidble2.RxBleClient
import io.reactivex.BackpressureStrategy
import io.reactivex.disposables.Disposable
import org.ligi.blexplorer.DeviceInfo
import org.ligi.blexplorer.HelpActivity
import org.ligi.blexplorer.R
import org.ligi.blexplorer.bluetoothController
import org.ligi.blexplorer.databinding.ActivityWithRecyclerBinding
import org.ligi.blexplorer.databinding.ItemDeviceBinding
import org.ligi.blexplorer.services.DeviceServiceExploreActivity
import org.ligi.blexplorer.util.DevicePropertiesDescriber
import org.ligi.blexplorer.util.ManufacturerRecordParserFactory
import org.ligi.tracedroid.sending.TraceDroidEmailSender
import java.math.BigInteger

class DeviceListActivity : AppCompatActivity() {


    private lateinit var binding : ActivityWithRecyclerBinding
    private lateinit var viewModel: DeviceListViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        TraceDroidEmailSender.sendStackTraces("ligi@ligi.de", this)

        binding = ActivityWithRecyclerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val adapter = DeviceRecycler()

        binding.contentList.layoutManager = LinearLayoutManager(this)
        binding.contentList.adapter = adapter

        viewModel = ViewModelProvider(this).get(DeviceListViewModel::class.java)
        viewModel.deviceListData.observe(this) { adapter.submitList(it) }
        viewModel.bluetoothStateLiveData.observe(this) { state ->
            @Suppress("NON_EXHAUSTIVE_WHEN")
            when(state) {
                RxBleClient.State.BLUETOOTH_NOT_ENABLED -> {
                    if (!bluetoothController.isBluetoothEnabled()) {
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                    }
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

    companion object {
        private const val REQUEST_ENABLE_BT = 2300
    }

}

private class DeviceListDiffCallback : DiffUtil.ItemCallback<DeviceInfo>() {
    override fun areItemsTheSame(oldItem: DeviceInfo, newItem: DeviceInfo): Boolean =
            oldItem.scanResult.bleDevice.bluetoothDevice == newItem.scanResult.bleDevice.bluetoothDevice

    override fun areContentsTheSame(oldItem: DeviceInfo, newItem: DeviceInfo): Boolean {
        if(oldItem.scanResult.rssi != newItem.scanResult.rssi) return false
        if(!oldItem.scanResult.scanRecord.bytes.contentEquals(newItem.scanResult.scanRecord.bytes)) return false
        return oldItem.last_seen == newItem.last_seen
    }
}

class DeviceListViewModel : ViewModel() {
    internal val deviceListData : LiveData<List<DeviceInfo>> = bluetoothController.deviceListLiveData()
    internal val bluetoothStateLiveData : LiveData<RxBleClient.State> = BluetoothStateChangeLiveData()
}

private class BluetoothStateChangeLiveData : LiveData<RxBleClient.State>() {
    private var disposable : Disposable? = null

    override fun onActive() {
        super.onActive()
        disposable = bluetoothController.bluetoothStateEvents()
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

    fun applyDevice(newDeviceInfo: DeviceInfo) {
        device = newDeviceInfo.scanResult.bleDevice.bluetoothDevice
        binding.name.text = if (TextUtils.isEmpty(device.name)) "no name" else device.name
        binding.rssi.text = "${newDeviceInfo.scanResult.rssi}db"
        binding.lastSeen.text = "${(System.currentTimeMillis() - newDeviceInfo.last_seen) / 1000}s"
        binding.address.text = device.address

        val scanRecord = newDeviceInfo.scanResult.scanRecord
        val scanRecordStr = StringBuilder()
        scanRecord.serviceUuids?.let { uuids ->
            for (parcelUuid in uuids) {
                scanRecordStr.append("$parcelUuid\n")
            }
        }

        val manufacturerSpecificData = scanRecord.manufacturerSpecificData

        (0 until manufacturerSpecificData.size())
                .map { manufacturerSpecificData.keyAt(it) }
                .forEach {key ->
                    val p = ManufacturerRecordParserFactory.parse(key, manufacturerSpecificData.get(key), device)
                    if (p == null) {
                        scanRecordStr.append("$key=${BigInteger(1, manufacturerSpecificData.get(key)).toString(16)}\n")
                    } else {
                        scanRecordStr.append("${p.keyDescriptor} = {\n$p}\n")
                        if (!TextUtils.isEmpty(p.getName(device))) {
                            binding.name.text = p.getName(device)
                        }
                    }
                }

        for (parcelUuid in scanRecord.serviceData.keys) {
            scanRecordStr.append("$parcelUuid=${BigInteger(1, scanRecord.serviceData[parcelUuid]).toString(16)}\n")
        }

        binding.scanRecord.text = scanRecordStr.toString()

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

private class DeviceRecycler : ListAdapter<DeviceInfo, DeviceViewHolder>(DeviceListDiffCallback()) {
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