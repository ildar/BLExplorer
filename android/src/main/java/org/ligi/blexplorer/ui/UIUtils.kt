package org.ligi.blexplorer.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.polidea.rxandroidble2.RxBleClient
import com.uber.autodispose.AutoDispose
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider
import io.reactivex.BackpressureStrategy
import io.reactivex.android.schedulers.AndroidSchedulers
import org.ligi.blexplorer.bluetoothController

class ExitActivityOnBluetoothDisable : Fragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val autoDisposable = AutoDispose.autoDisposable<RxBleClient.State>(AndroidLifecycleScopeProvider.from(this, Lifecycle.Event.ON_DESTROY))
        bluetoothController.bluetoothStateEvents
                .toFlowable(BackpressureStrategy.LATEST)
                .filter { it == RxBleClient.State.BLUETOOTH_NOT_ENABLED }
                .take(1)
                .observeOn(AndroidSchedulers.mainThread())
                .`as`(autoDisposable)
                .subscribe(
                        { activity?.finish() },
                        {}
                )
    }
}