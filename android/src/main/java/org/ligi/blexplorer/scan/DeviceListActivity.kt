package org.ligi.blexplorer.scan

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.DialogInterface
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
import org.ligi.blexplorer.App
import org.ligi.blexplorer.HelpActivity
import org.ligi.blexplorer.R
import org.ligi.blexplorer.bluetoothController
import org.ligi.blexplorer.databinding.ActivityWithRecyclerBinding
import org.ligi.blexplorer.databinding.ItemDeviceBinding
import org.ligi.blexplorer.services.DeviceServiceExploreActivity
import org.ligi.blexplorer.util.DevicePropertiesDescriber
import org.ligi.blexplorer.util.ManufacturerRecordParserFactory
import org.ligi.blexplorer.util.from_lollipop.ScanRecord
import org.ligi.tracedroid.sending.TraceDroidEmailSender
import java.math.BigInteger
import java.util.*

class DeviceListActivity : AppCompatActivity() {


    private lateinit var binding : ActivityWithRecyclerBinding
    private lateinit var viewModel: DeviceListViewModel

    private class DeviceRecycler : ListAdapter<DeviceInfo, DeviceViewHolder>(DeviceListDiffCallback()) {
        override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): DeviceViewHolder {
            val layoutInflater = LayoutInflater.from(viewGroup.context)
            val binding = ItemDeviceBinding.inflate(layoutInflater, viewGroup, false)
            return DeviceViewHolder(binding)
                    .apply { installOnClickListener() }
        }

        override fun onBindViewHolder(deviceViewHolder: DeviceViewHolder, i: Int) {
            val bluetoothDeviceInfo = getItem(i)
            deviceViewHolder.applyDevice(bluetoothDeviceInfo)
        }
    }

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
    }

    override fun onResume() {
        super.onResume()
        if (!bluetoothController.isBluetoothAvailable()) {
            AlertDialog.Builder(this)
                    .setMessage(R.string.bluetooth_needed_error_msg)
                    .setTitle(R.string.error)
                    .setPositiveButton(R.string.exit) { _: DialogInterface, _: Int ->
                this@DeviceListActivity.finish()
            }.show()
        } else if (!bluetoothController.isBluetoothEnabled()) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
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
            oldItem.bluetoothDevice == newItem.bluetoothDevice

    override fun areContentsTheSame(oldItem: DeviceInfo, newItem: DeviceInfo): Boolean {
        if(oldItem.rssi != newItem.rssi) return false
        if(!oldItem.scanRecord.contentEquals(newItem.scanRecord)) return false
        return oldItem.last_seen == newItem.last_seen
    }
}

class DeviceListViewModel : ViewModel() {
    internal val deviceListData : LiveData<List<DeviceInfo>> = bluetoothController.deviceListLiveData
}

internal data class DeviceInfo(val bluetoothDevice: BluetoothDevice, val rssi: Int, val scanRecord: ByteArray) {
    val last_seen: Long = System.currentTimeMillis()
}

private class DeviceViewHolder(private val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root) {

    lateinit var device: BluetoothDevice

    fun applyDevice(newDeviceInfo: DeviceInfo) {
        device = newDeviceInfo.bluetoothDevice
        binding.name.text = if (TextUtils.isEmpty(device.name)) "no name" else device.name
        binding.rssi.text = "${newDeviceInfo.rssi}db"
        binding.lastSeen.text = "${(System.currentTimeMillis() - newDeviceInfo.last_seen) / 1000}s"
        binding.address.text = device.address

        val scanRecord = ScanRecord.parseFromBytes(newDeviceInfo.scanRecord)
        val scanRecordStr = StringBuilder()
        if (scanRecord.serviceUuids != null) {
            for (parcelUuid in scanRecord.serviceUuids) {
                scanRecordStr.append("${parcelUuid.toString()}\n")
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
            val intent = Intent(it.context, DeviceServiceExploreActivity::class.java)
            App.device = device
            it.context.startActivity(intent)
        }
    }

}