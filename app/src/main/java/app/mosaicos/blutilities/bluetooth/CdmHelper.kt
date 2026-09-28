package app.mosaicos.blutilities.bluetooth

import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.ViewModel
import app.mosaicos.blutilities.R

object CdmHelper {
    fun isAvailable(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP) &&
            context.getSystemService(CompanionDeviceManager::class.java) != null

    fun isAssociated(context: Context, macAddress: String): Result<Boolean> = runCatching {
        if (!isAvailable(context)) return@runCatching false
        val cdm = context.getSystemService(CompanionDeviceManager::class.java)
            ?: return@runCatching false
        cdm.myAssociations.any {
            it.deviceMacAddress?.toString()?.equals(macAddress, ignoreCase = true) == true
        }
    }

    class RequestState : ViewModel() {
        var onChanged: (() -> Unit)? = null
        var permissionPending = false
        var permissionRequested = false
        var address: String? = null
        var pending = false
        var launched = false
        var sender: IntentSender? = null
        var error: Int? = null
        var restored = false
        private var cleared = false
        private val handler = Handler(Looper.getMainLooper())
        private val timeout = Runnable { fail(R.string.cdm_failed) }

        fun request(context: Context, macAddress: String) {
            if (pending) return
            if (address == macAddress) {
                error = error ?: R.string.codec_access_denied
                onChanged?.invoke()
                return
            }
            address = macAddress
            error = null
            pending = true
            val appContext = context.applicationContext
            try {
                if (!isAvailable(appContext)) {
                    fail(R.string.cdm_unavailable)
                    return
                }
                val cdm = appContext.getSystemService(CompanionDeviceManager::class.java)
                val request = AssociationRequest.Builder()
                    .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(macAddress).build())
                    .setSingleDevice(true)
                    .build()
                handler.postDelayed(timeout, 30_000)
                cdm?.associate(request, appContext.mainExecutor, object : CompanionDeviceManager.Callback() {
                    override fun onAssociationPending(intentSender: IntentSender) {
                        if (cleared || !pending || address != macAddress) return
                        handler.removeCallbacks(timeout)
                        sender = intentSender
                        onChanged?.invoke()
                    }

                    override fun onAssociationCreated(associationInfo: AssociationInfo) {
                        if (!cleared && address == macAddress) onChanged?.invoke()
                    }

                    override fun onFailure(error: CharSequence?) {
                        if (!cleared && pending && address == macAddress) {
                            Log.w("BlutilitiesCdm", "Association request failed")
                            fail(R.string.cdm_failed)
                        }
                    }
                }) ?: fail(R.string.cdm_unavailable)
            } catch (failure: RuntimeException) {
                Log.w("BlutilitiesCdm", "Association unavailable: ${failure.javaClass.simpleName}")
                fail(R.string.cdm_failed)
            }
        }

        fun complete(accepted: Boolean) {
            handler.removeCallbacks(timeout)
            pending = false
            launched = false
            sender = null
            error = if (accepted) null else R.string.cdm_denied
            onChanged?.invoke()
        }

        fun fail(message: Int) {
            handler.removeCallbacks(timeout)
            pending = false
            launched = false
            sender = null
            error = message
            onChanged?.invoke()
        }

        override fun onCleared() {
            cleared = true
            onChanged = null
            handler.removeCallbacksAndMessages(null)
        }
    }
}
