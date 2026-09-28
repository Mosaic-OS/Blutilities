package app.mosaicos.blutilities.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import app.mosaicos.blutilities.R
import app.mosaicos.blutilities.bluetooth.BluetoothPermissionHelper
import app.mosaicos.blutilities.bluetooth.CdmHelper

class BluetoothQualityActivity : AppCompatActivity(), CodecPickerDialog.Host {
    private var picker: CodecPickerDialog? = null
    private var resumed = false
    private val requests: CdmHelper.RequestState by lazy {
        ViewModelProvider(this)[CdmHelper.RequestState::class.java]
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            requests.permissionPending = false
            if (!granted) picker?.showMessage(R.string.bt_permission_help)
            updatePicker()
        }

    private val associationLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            requests.complete(result.resultCode == RESULT_OK)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setNoOpenCloseTransition()
        if (!requests.restored) {
            requests.restored = true
            savedInstanceState?.let {
                requests.permissionPending = it.getBoolean("permissionPending")
                requests.permissionRequested = it.getBoolean("permissionRequested")
                requests.address = it.getString("associationAddress")
                requests.launched = it.getBoolean("associationLaunched")
                requests.pending = requests.launched
                requests.error = it.getInt("associationError").takeIf { value -> value != 0 }
                if (it.getBoolean("associationPending") && !requests.launched) {
                    requests.error = R.string.cdm_failed
                }
            }
        }
        val dialog = CodecPickerDialog(this, this)
        picker = dialog
        dialog.setOnDismissListener { if (!isFinishing) finishWithoutTransition() }
        dialog.show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!requests.pending && !requests.permissionPending) {
            requests.address = null
            requests.error = null
            requests.permissionRequested = false
            picker?.pause()
            updatePicker()
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        requests.onChanged = ::updatePicker
        updatePicker()
    }

    override fun onPause() {
        resumed = false
        requests.onChanged = null
        picker?.pause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("permissionPending", requests.permissionPending)
        outState.putBoolean("permissionRequested", requests.permissionRequested)
        outState.putString("associationAddress", requests.address)
        outState.putBoolean("associationPending", requests.pending)
        outState.putBoolean("associationLaunched", requests.launched)
        outState.putInt("associationError", requests.error ?: 0)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        requests.onChanged = null
        picker?.apply {
            setOnDismissListener(null)
            dismiss()
        }
        picker = null
        super.onDestroy()
    }

    private fun updatePicker() {
        if (!resumed || isFinishing) return
        val dialog = picker ?: return
        if (requests.permissionPending || requests.pending) {
            dialog.pause()
            dialog.showMessage(R.string.bt_waiting_for_consent)
            val sender = requests.sender
            if (sender != null && !requests.launched) {
                requests.launched = true
                requests.sender = null
                try {
                    associationLauncher.launch(IntentSenderRequest.Builder(sender).build())
                } catch (error: RuntimeException) {
                    Log.w("BlutilitiesCdm", "Consent launch failed: ${error.javaClass.simpleName}")
                    requests.fail(R.string.cdm_failed)
                }
            }
        } else if (!BluetoothPermissionHelper.hasPermission(this)) {
            dialog.showMessage(R.string.bt_permission_help)
            onPermissionNeeded()
        } else {
            dialog.resume()
        }
    }

    override fun onPermissionNeeded() {
        if (!resumed || requests.permissionPending || requests.permissionRequested) return
        requests.permissionRequested = true
        requests.permissionPending = true
        try {
            permissionLauncher.launch(BluetoothPermissionHelper.permission)
        } catch (error: RuntimeException) {
            requests.permissionPending = false
            Log.w("BlutilitiesCdm", "Permission launch failed: ${error.javaClass.simpleName}")
            picker?.showMessage(R.string.bt_permission_help)
        }
    }

    override fun onAssociationNeeded(address: String) {
        if (!resumed) return
        if (requests.address == address && !requests.pending && requests.error != null) {
            picker?.showMessage(requests.error!!)
            return
        }
        requests.request(this, address)
        updatePicker()
    }

    @Suppress("DEPRECATION")
    private fun setNoOpenCloseTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            overridePendingTransition(0, 0)
        }
    }

    @Suppress("DEPRECATION")
    private fun finishWithoutTransition() {
        finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overridePendingTransition(0, 0)
        }
    }
}
