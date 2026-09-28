package app.mosaicos.blutilities.bluetooth

import android.bluetooth.BluetoothCodecConfig
import android.bluetooth.BluetoothCodecStatus
import android.content.Context
import android.os.Build
import app.mosaicos.blutilities.R
import app.mosaicos.blutilities.model.BluetoothCodecInfo

class CodecListBuilder(private val context: Context) {
    fun build(codecStatus: BluetoothCodecStatus): List<BluetoothCodecInfo> {
        val current = codecStatus.codecConfig
        val selectable = codecStatus.codecsSelectableCapabilities
            .filter { it.codecType != BluetoothCodecConfig.SOURCE_CODEC_TYPE_INVALID }
            .distinctBy { codecKey(it) }
        if (selectable.isEmpty()) return emptyList()
        return buildList {
            add(
                BluetoothCodecInfo(
                    codecName = context.getString(R.string.codec_system_selection),
                    qualityLabel = context.getString(R.string.codec_system_selection_sub),
                    codecType = BluetoothCodecConfig.SOURCE_CODEC_TYPE_INVALID
                )
            )
            add(BluetoothCodecInfo(context.getString(R.string.header_codec), isHeader = true))
            selectable.sortedByDescending { it.codecType }.forEach { cap ->
                val selected = current != null && sameCodec(current, cap)
                add(
                    BluetoothCodecInfo(
                        codecName = typeName(cap),
                        codecType = cap.codecType,
                        codecConfig = cap,
                        isSelected = selected
                    )
                )
                if (cap.codecType == BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC && selected) {
                    add(BluetoothCodecInfo(context.getString(R.string.header_ldac_quality), isHeader = true))
                    addAll(ldacQualityItems(current, cap))
                }
            }
        }
    }

    private fun ldacQualityItems(
        current: BluetoothCodecConfig?,
        capability: BluetoothCodecConfig
    ): List<BluetoothCodecInfo> {
        val presets = listOf(
            Triple(R.string.ldac_quality_best, "990/909 kbps", 1000L),
            Triple(R.string.ldac_quality_balanced, "660/606 kbps", 1001L),
            Triple(R.string.ldac_quality_connection, "330/303 kbps", 1002L),
            Triple(R.string.ldac_quality_adaptive, context.getString(R.string.ldac_quality_adaptive_sub), 1003L)
        )
        return presets.map { (label, sub, quality) ->
            BluetoothCodecInfo(
                codecName = context.getString(label),
                qualityLabel = sub,
                codecType = capability.codecType,
                codecConfig = capability,
                ldacQuality = quality,
                isSelected = current?.codecSpecific1 == quality
            )
        }
    }

    fun typeName(config: BluetoothCodecConfig): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            config.extendedCodecType?.let { return it.codecName }
        }
        return when (config.codecType) {
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC -> "SBC"
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC -> "AAC"
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX -> "aptX"
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX_HD -> "aptX HD"
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC -> "LDAC"
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_LC3 -> "LC3"
            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                config.codecType == BluetoothCodecConfig.SOURCE_CODEC_TYPE_OPUS) "Opus"
            else context.getString(R.string.codec_unknown, config.codecType)
        }
    }

    fun sameCodec(first: BluetoothCodecConfig, second: BluetoothCodecConfig): Boolean =
        codecKey(first) == codecKey(second)

    private fun codecKey(config: BluetoothCodecConfig): Long {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            config.extendedCodecType?.let { return it.codecId }
        }
        return config.codecType.toLong()
    }

    fun resolveActiveLabel(codecStatus: BluetoothCodecStatus): String? {
        val config = codecStatus.codecConfig ?: return null
        val name = typeName(config)
        if (config.codecType != BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC) return name
        val quality = when (config.codecSpecific1) {
            1000L -> "990/909 kbps"
            1001L -> "660/606 kbps"
            1002L -> "330/303 kbps"
            1003L -> context.getString(R.string.ldac_quality_adaptive_sub)
            else -> null
        }
        return if (quality == null) name else "$name - $quality"
    }
}
