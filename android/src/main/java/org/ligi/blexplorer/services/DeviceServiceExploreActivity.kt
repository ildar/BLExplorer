package org.ligi.blexplorer.services

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import net.steamcrafted.loadtoast.LoadToast
import org.ligi.blexplorer.App
import org.ligi.blexplorer.R
import org.ligi.blexplorer.characteristics.CharacteristicActivity
import org.ligi.blexplorer.databinding.ActivityWithRecyclerBinding
import org.ligi.blexplorer.databinding.ItemServiceBinding
import org.ligi.blexplorer.util.DevicePropertiesDescriber
import org.ligi.blexplorer.util.KEY_BLUETOOTH_DEVICE
import org.ligi.snackengage.SnackEngage
import org.ligi.snackengage.snacks.DefaultRateSnack
import java.util.*


class DeviceServiceExploreActivity() : AppCompatActivity() {

    private lateinit var binding: ActivityWithRecyclerBinding
    private lateinit var device: BluetoothDevice

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if(!intent.hasExtra(KEY_BLUETOOTH_DEVICE)) {
            finish()
            return
        }
        device = intent.getParcelableExtra(KEY_BLUETOOTH_DEVICE)

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

        val loadToast = LoadToast(this).setText(getString(R.string.connecting)).show()

        device.connectGatt(this@DeviceServiceExploreActivity, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                App.gatt = gatt
                gatt.discoverServices()
                runOnUiThread { loadToast.setText(getString(R.string.discovering)) }
                super.onConnectionStateChange(gatt, status, newState)
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val services = gatt.services
                runOnUiThread {
                    adapter.serviceList.addAll(services)
                    adapter.notifyDataSetChanged()
                    loadToast.success()
                }
                super.onServicesDiscovered(gatt, status)
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onPause() {
        App.gatt.disconnect()
        super.onPause()
    }

    companion object {
        fun createIntent(context : Context, device : BluetoothDevice) : Intent = Intent(context, DeviceServiceExploreActivity::class.java)
                .putExtra(KEY_BLUETOOTH_DEVICE, device)
    }
}

private class ServiceRecycler(private val device: BluetoothDevice) : RecyclerView.Adapter<ServiceViewHolder>() {
    val serviceList : MutableList<BluetoothGattService> = ArrayList<BluetoothGattService>()

    override fun onCreateViewHolder(viewGroup: ViewGroup, position: Int): ServiceViewHolder {
        val layoutInflater = LayoutInflater.from(viewGroup.context)
        val binding = ItemServiceBinding.inflate(layoutInflater, viewGroup, false)
        return ServiceViewHolder(binding)
    }

    override fun onBindViewHolder(deviceViewHolder: ServiceViewHolder, position: Int) {
        val service = serviceList[position]
        deviceViewHolder.applyService(device, service)
    }

    override fun getItemCount() = serviceList.size

}

private class ServiceViewHolder(private val binding: ItemServiceBinding) : RecyclerView.ViewHolder(binding.root) {

    fun applyService(device: BluetoothDevice, service: BluetoothGattService) {
        itemView.setOnClickListener { v ->
            App.service = service
            val intent = CharacteristicActivity.createIntent(v.context, device)
            v.context.startActivity(intent)
        }
        binding.uuid.text = service.uuid.toString()
        binding.type.text = DevicePropertiesDescriber.describeServiceType(service)
        binding.name.text = DevicePropertiesDescriber.getServiceName(itemView.context, service, "unknown")
    }
}