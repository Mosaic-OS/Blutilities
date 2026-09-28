package app.mosaicos.blutilities.bluetooth

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.mosaicos.blutilities.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class A2dpProfileManager(
    context: Context,
    private val onConnected: (BluetoothA2dp, BluetoothDevice?) -> Unit,
    private val onUnavailable: (Int) -> Unit
) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var executor: ExecutorService? = null
    private var proxy: BluetoothA2dp? = null
    private var receiver: BroadcastReceiver? = null
    private var requested = false
    private var ready = false
    private var querying = false
    private var queryRevision = 0
    @Volatile private var generation = 0
    @Volatile private var running = false

    private val queryTimeout = Runnable {
        queryRevision++
        if (running) onUnavailable(R.string.bt_service_unavailable)
    }
    private val connectionTimeout = Runnable {
        if (running && !ready) onUnavailable(R.string.bt_service_unavailable)
    }

    fun connect() {
        if (running) return
        running = true
        val session = ++generation
        executor = Executors.newSingleThreadExecutor()
        val listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (running && generation == session) refresh()
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED)
            addAction("android.bluetooth.a2dp.profile.action.ACTIVE_DEVICE_CHANGED")
            addAction("android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED")
        }
        try {
            // Bluetooth broadcasts originate from a privileged UID outside the system UID.
            context.registerReceiver(listener, filter, Context.RECEIVER_EXPORTED)
            receiver = listener
        } catch (error: RuntimeException) {
            Log.w("A2dpProfileManager", "registerReceiver failed: ${error.javaClass.simpleName}")
            disconnect()
            onUnavailable(R.string.bt_service_unavailable)
            return
        }
        refresh()
    }

    fun refresh() {
        if (!running) return
        val requestRevision = ++queryRevision
        if (!BluetoothPermissionHelper.hasPermission(context)) {
            onUnavailable(R.string.bt_permission_denied)
            return
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) {
            onUnavailable(R.string.bt_not_supported)
            return
        }
        val enabled = try {
            adapter.isEnabled
        } catch (error: RuntimeException) {
            onUnavailable(
                if (error is SecurityException) R.string.bt_permission_denied
                else R.string.bt_service_unavailable
            )
            return
        }
        if (!enabled) {
            onUnavailable(R.string.tile_bluetooth_off)
            return
        }
        if (!requested) {
            requestProxy(adapter)
            return
        }
        val current = proxy
        if (!ready || current == null) return
        if (querying) return
        querying = true
        handler.postDelayed(queryTimeout, 10_000)
        execute({ selectDevice(current) }) { result ->
            querying = false
            handler.removeCallbacks(queryTimeout)
            if (queryRevision != requestRevision) {
                refresh()
            } else {
                result.fold(
                    onSuccess = { onConnected(current, it) },
                    onFailure = {
                        onUnavailable(
                            if (it is MultipleDevicesException) R.string.bt_multiple_devices
                            else if (it is SecurityException) R.string.bt_permission_denied
                            else R.string.bt_service_unavailable
                        )
                    }
                )
            }
        }
    }

    private fun requestProxy(adapter: BluetoothAdapter) {
        requested = true
        val session = generation
        handler.postDelayed(connectionTimeout, 10_000)
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, bluetoothProfile: BluetoothProfile) {
                handler.post {
                    if (!running || generation != session) {
                        closeProxy(adapter, bluetoothProfile)
                    } else if (profile == BluetoothProfile.A2DP && bluetoothProfile is BluetoothA2dp) {
                        proxy = bluetoothProfile
                        ready = true
                        handler.removeCallbacks(connectionTimeout)
                        refresh()
                    }
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                handler.post {
                    if (running && generation == session && profile == BluetoothProfile.A2DP) {
                        ready = false
                        queryRevision++
                        onUnavailable(R.string.bt_service_unavailable)
                    }
                }
            }
        }
        try {
            if (!adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)) {
                requested = false
                handler.removeCallbacks(connectionTimeout)
                onUnavailable(R.string.bt_service_unavailable)
            }
        } catch (error: RuntimeException) {
            requested = false
            handler.removeCallbacks(connectionTimeout)
            Log.w("A2dpProfileManager", "getProfileProxy failed: ${error.javaClass.simpleName}")
            onUnavailable(
                if (error is SecurityException) R.string.bt_permission_denied
                else R.string.bt_service_unavailable
            )
        }
    }

    // The first connected device need not be the active audio route.
    fun selectDevice(current: BluetoothA2dp): BluetoothDevice? {
        val devices = current.connectedDevices
        if (devices.size <= 1) return devices.singleOrNull()
        val active = A2dpCodecApi.getActiveDevice(current).getOrNull()
        return devices.firstOrNull { it == active } ?: throw MultipleDevicesException()
    }

    fun <T> execute(block: (isCurrent: () -> Boolean) -> T, onResult: (Result<T>) -> Unit) {
        val worker = executor ?: return
        val session = generation
        worker.execute task@{
            if (!running || generation != session) return@task
            val result = runCatching { block { running && generation == session } }
            handler.post {
                if (running && generation == session) onResult(result)
            }
        }
    }

    fun disconnect() {
        running = false
        generation++
        handler.removeCallbacks(queryTimeout)
        handler.removeCallbacks(connectionTimeout)
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (error: IllegalArgumentException) {
                Log.w("A2dpProfileManager", "unregisterReceiver failed: ${error.javaClass.simpleName}")
            }
        }
        receiver = null
        executor?.shutdownNow()
        executor = null
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        proxy?.let { if (adapter != null) closeProxy(adapter, it) }
        proxy = null
        requested = false
        ready = false
        querying = false
        queryRevision++
    }

    private fun closeProxy(adapter: BluetoothAdapter, current: BluetoothProfile) {
        try {
            adapter.closeProfileProxy(BluetoothProfile.A2DP, current)
        } catch (error: RuntimeException) {
            Log.w("A2dpProfileManager", "closeProfileProxy failed: ${error.javaClass.simpleName}")
        }
    }

    class MultipleDevicesException : Exception()
}
