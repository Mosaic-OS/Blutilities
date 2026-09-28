package app.mosaicos.blutilities.tiles

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import app.mosaicos.blutilities.R
import app.mosaicos.blutilities.bluetooth.A2dpCodecApi
import app.mosaicos.blutilities.bluetooth.A2dpProfileManager
import app.mosaicos.blutilities.bluetooth.CodecListBuilder
import app.mosaicos.blutilities.ui.BluetoothQualityActivity

class BluetoothQualityTileService : BaseTileService() {
    private var subtitle: String? = null
    private var revision = 0
    private var destroyed = false
    private val codecBuilder by lazy { CodecListBuilder(this) }
    private val profileManager: A2dpProfileManager by lazy {
        A2dpProfileManager(
            this,
            onConnected = { proxy, device ->
                val request = ++revision
                if (device == null) {
                    subtitle = getString(R.string.tile_no_device)
                    refreshTile()
                } else {
                    profileManager.execute({
                        A2dpCodecApi.getCodecStatus(proxy, device).getOrNull()?.let(codecBuilder::resolveActiveLabel)
                    }) { result ->
                        if (revision == request) {
                            subtitle = result.getOrNull()
                                ?: getString(R.string.codec_read_failed)
                            refreshTile()
                        }
                    }
                }
            },
            onUnavailable = {
                revision++
                subtitle = getString(it)
                refreshTile()
            }
        )
    }

    override fun onStartListening() {
        subtitle = getString(R.string.bt_loading)
        super.onStartListening()
        profileManager.connect()
    }

    override fun onStopListening() {
        revision++
        profileManager.disconnect()
        super.onStopListening()
    }

    override fun onDestroy() {
        destroyed = true
        revision++
        profileManager.disconnect()
        super.onDestroy()
    }

    override fun isAvailable() = true

    override fun isActive() = false

    override fun onTileClicked() {
        if (isLocked) unlockAndRun { if (!destroyed) startPickerActivityAndCollapse() }
        else startPickerActivityAndCollapse()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun startPickerActivityAndCollapse() {
        val intent = Intent(this, BluetoothQualityActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // SystemUI sends this activity PendingIntent using its own launch privileges.
                startActivityAndCollapse(
                    PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (error: RuntimeException) {
            Log.w("BlutilitiesTile", "Picker launch failed: ${error.javaClass.simpleName}")
            Toast.makeText(this, R.string.picker_launch_failed, Toast.LENGTH_LONG).show()
        }
    }

    override fun onBeforeRefresh() {
        qsTile?.subtitle = subtitle ?: getString(R.string.bt_loading)
    }
}
