package app.mosaicos.blutilities.model

import android.bluetooth.BluetoothCodecConfig

data class BluetoothCodecInfo(
    val codecName: String,
    val qualityLabel: String? = null,
    val codecType: Int = -1,
    val codecConfig: BluetoothCodecConfig? = null,
    val ldacQuality: Long? = null,
    val isSelected: Boolean = false,
    val isHeader: Boolean = false
)
