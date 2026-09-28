package app.mosaicos.blutilities.ui

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothCodecConfig
import android.bluetooth.BluetoothCodecStatus
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import app.mosaicos.blutilities.R
import app.mosaicos.blutilities.bluetooth.A2dpCodecApi
import app.mosaicos.blutilities.bluetooth.A2dpProfileManager
import app.mosaicos.blutilities.bluetooth.BluetoothPermissionHelper
import app.mosaicos.blutilities.bluetooth.CdmHelper
import app.mosaicos.blutilities.bluetooth.CodecListBuilder
import app.mosaicos.blutilities.databinding.DialogBluetoothQualityBinding
import app.mosaicos.blutilities.model.BluetoothCodecInfo
import app.mosaicos.blutilities.ui.adapter.CodecListAdapter
import com.google.android.material.color.DynamicColors

private fun pickerContext(context: Context): Context = DynamicColors.wrapContextIfAvailable(
    ContextThemeWrapper(context, R.style.Theme_Blutilities_Dialog)
)

class CodecPickerDialog(
    hostContext: Context,
    private val host: Host
) : ComponentDialog(pickerContext(hostContext), R.style.Theme_Blutilities_NoOverride) {
    interface Host {
        fun onPermissionNeeded()
        fun onAssociationNeeded(address: String)
    }

    private data class CodecState(
        val name: String,
        val status: BluetoothCodecStatus? = null,
        val rows: List<BluetoothCodecInfo> = emptyList(),
        val needsAssociation: Boolean = false,
        val error: Int? = null
    )

    private lateinit var binding: DialogBluetoothQualityBinding
    private val handler = Handler(Looper.getMainLooper())
    private val codecBuilder = CodecListBuilder(context)
    private val codecAdapter = CodecListAdapter(::applyCodecSelection)
    private var proxy: BluetoothA2dp? = null
    private var device: BluetoothDevice? = null
    private var resumed = false
    private var dismissing = false
    private var canSelect = false
    private var submitting = false
    private var revision = 0
    private var scrimAnimator: ValueAnimator? = null
    private var pendingSelection: BluetoothCodecInfo? = null
    private var pendingDevice: BluetoothDevice? = null
    private var verificationAttempts = 0
    private val verifySelection = Runnable { profileManager.refresh() }
    private val operationTimeout = Runnable {
        revision++
        pendingSelection = null
        showMessage(R.string.bt_service_unavailable)
    }
    private val profileManager: A2dpProfileManager = A2dpProfileManager(
        context,
        onConnected = { current, target ->
            proxy = current
            device = target
            if (target == null) showMessage(R.string.no_device_connected)
            else populateCodecList(current, target)
        },
        onUnavailable = { message ->
            proxy = null
            device = null
            showMessage(message)
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogBluetoothQualityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCanceledOnTouchOutside(false)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            WindowCompat.setDecorFitsSystemWindows(this, false)
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        binding.recyclerCodecs.layoutManager = LinearLayoutManager(context)
        binding.recyclerCodecs.adapter = codecAdapter
        binding.btnClose.setOnClickListener { animateOutAndDismiss() }
        binding.root.setOnClickListener { animateOutAndDismiss() }
        binding.cardContent.setOnClickListener { }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = animateOutAndDismiss()
        })
        binding.root.background.mutate().alpha = 0
        binding.cardContent.alpha = 0f
        binding.cardContent.translationY = 32f * context.resources.displayMetrics.density
    }

    override fun onStart() {
        super.onStart()
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        animateScrim(153, 300)
        binding.cardContent.animate().alpha(1f).translationY(0f)
            .setDuration(300).setInterpolator(DecelerateInterpolator(2f)).start()
    }

    fun resume() {
        if (dismissing) return
        if (resumed) {
            profileManager.refresh()
            return
        }
        resumed = true
        showMessage(R.string.bt_loading)
        if (BluetoothPermissionHelper.hasPermission(context)) profileManager.connect()
        else host.onPermissionNeeded()
    }

    fun pause() {
        resumed = false
        revision++
        handler.removeCallbacksAndMessages(null)
        pendingSelection = null
        submitting = false
        canSelect = false
        pendingDevice = null
        proxy = null
        device = null
        profileManager.disconnect()
    }

    override fun onStop() {
        pause()
        scrimAnimator?.removeAllListeners()
        scrimAnimator?.cancel()
        scrimAnimator = null
        binding.cardContent.animate().cancel()
        super.onStop()
    }

    fun showMessage(message: Int) {
        canSelect = false
        submitting = false
        revision++
        handler.removeCallbacksAndMessages(null)
        pendingSelection = null
        codecAdapter.submitList(
            listOf(BluetoothCodecInfo(context.getString(message), isHeader = true))
        )
    }

    private fun populateCodecList(current: BluetoothA2dp, target: BluetoothDevice) {
        if (submitting) return
        canSelect = false
        val request = ++revision
        handler.removeCallbacks(operationTimeout)
        handler.postDelayed(operationTimeout, 10_000)
        profileManager.execute({ readCodecState(current, target) }) { result ->
            if (!resumed || revision != request) return@execute
            handler.removeCallbacks(operationTimeout)
            val state = result.getOrElse {
                showMessage(
                    if (it is SecurityException) R.string.bt_permission_help
                    else R.string.codec_read_failed
                )
                return@execute
            }
            binding.tvTitle.text = context.getString(R.string.title_bt_quality, state.name)
            if (state.needsAssociation) {
                showMessage(R.string.bt_waiting_for_consent)
                host.onAssociationNeeded(target.address)
            } else if (state.error != null) {
                showMessage(state.error)
            } else {
                val selection = pendingSelection
                if (selection != null) verifyCodecSelection(selection, target, state.status)
                else codecAdapter.submitList(state.rows) {
                    if (resumed && revision == request) canSelect = true
                }
            }
        }
    }

    private fun readCodecState(current: BluetoothA2dp, target: BluetoothDevice): CodecState {
        val name = target.name ?: context.getString(R.string.title_bt_quality_default)
        val result = A2dpCodecApi.getCodecStatus(current, target)
        val status = result.getOrNull()
        val error = result.exceptionOrNull()
        if (error != null && A2dpCodecApi.failure(error) != A2dpCodecApi.Failure.DENIED) {
            return CodecState(name, error = errorMessage(error))
        }
        if (!BluetoothPermissionHelper.hasPermission(context)) {
            return CodecState(name, error = R.string.bt_permission_help)
        }
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_PRIVILEGED) !=
            PackageManager.PERMISSION_GRANTED) {
            if (!CdmHelper.isAvailable(context)) return CodecState(name, error = R.string.cdm_unavailable)
            val associated = CdmHelper.isAssociated(context, target.address)
            if (associated.isFailure) return CodecState(name, error = R.string.cdm_failed)
            if (!associated.getOrDefault(false)) return CodecState(name, needsAssociation = true)
        }
        if (status == null) return CodecState(name, error = R.string.codec_access_denied)
        val rows = codecBuilder.build(status)
        return CodecState(name, status, rows, error = if (rows.isEmpty()) R.string.codec_read_failed else null)
    }

    private fun applyCodecSelection(info: BluetoothCodecInfo) {
        if (info.isHeader || dismissing || !resumed || !canSelect || pendingSelection != null) return
        if (info.codecType == BluetoothCodecConfig.SOURCE_CODEC_TYPE_INVALID) {
            showMessage(R.string.codec_system_unavailable)
            return
        }
        val current = proxy
        val target = device
        if (current == null || target == null) {
            showMessage(R.string.no_device_connected)
            return
        }
        val request = ++revision
        pendingSelection = info
        submitting = true
        canSelect = false
        pendingDevice = target
        verificationAttempts = 0
        codecAdapter.submitList(
            listOf(BluetoothCodecInfo(context.getString(R.string.codec_verifying), isHeader = true))
        )
        handler.postDelayed(operationTimeout, 10_000)
        profileManager.execute({ isCurrent ->
            if (!BluetoothPermissionHelper.hasPermission(context)) throw SecurityException()
            if (profileManager.selectDevice(current) != target) throw A2dpProfileManager.MultipleDevicesException()
            val status = A2dpCodecApi.getCodecStatus(current, target).getOrThrow()
            val capability = status.codecsSelectableCapabilities.firstOrNull {
                info.codecConfig?.let { chosen -> codecBuilder.sameCodec(it, chosen) } == true
            } ?: error("Codec is no longer selectable")
            check(isCurrent())
            A2dpCodecApi.setCodecConfigPreference(current, target, buildCodecConfig(info, capability)).getOrThrow()
        }) { result ->
            if (!resumed || revision != request) return@execute
            handler.removeCallbacks(operationTimeout)
            submitting = false
            if (result.isFailure) showMessage(errorMessage(result.exceptionOrNull()!!))
            else profileManager.refresh()
        }
    }

    private fun verifyCodecSelection(
        selection: BluetoothCodecInfo,
        target: BluetoothDevice,
        status: BluetoothCodecStatus?
    ) {
        if (target != pendingDevice) {
            showMessage(R.string.codec_device_changed)
            return
        }
        val current = status?.codecConfig
        val chosen = selection.codecConfig
        if (current != null && chosen != null && codecBuilder.sameCodec(current, chosen) &&
            (selection.ldacQuality == null || current.codecSpecific1 == selection.ldacQuality)) {
            pendingSelection = null
            Toast.makeText(
                context, context.getString(R.string.codec_applied, selection.codecName.trim()), Toast.LENGTH_SHORT
            ).show()
            animateOutAndDismiss()
        } else if (++verificationAttempts >= 4) {
            showMessage(R.string.codec_not_confirmed)
        } else {
            handler.removeCallbacks(verifySelection)
            handler.postDelayed(verifySelection, 750)
        }
    }

    private fun buildCodecConfig(
        info: BluetoothCodecInfo,
        capability: BluetoothCodecConfig
    ): BluetoothCodecConfig = BluetoothCodecConfig.Builder().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
            capability.extendedCodecType != null) {
            setExtendedCodecType(capability.extendedCodecType)
        } else {
            setCodecType(capability.codecType)
        }
        setCodecPriority(BluetoothCodecConfig.CODEC_PRIORITY_HIGHEST)
        info.ldacQuality?.let { setCodecSpecific1(it) }
    }.build()

    private fun errorMessage(error: Throwable): Int {
        if (error is A2dpProfileManager.MultipleDevicesException) return R.string.codec_device_changed
        return when (A2dpCodecApi.failure(error)) {
            A2dpCodecApi.Failure.UNAVAILABLE -> R.string.codec_api_unavailable
            A2dpCodecApi.Failure.DENIED -> R.string.codec_access_denied
            A2dpCodecApi.Failure.NO_STATUS -> R.string.codec_read_failed
            A2dpCodecApi.Failure.FAILED -> R.string.codec_apply_failed
        }
    }

    private fun animateScrim(alpha: Int, duration: Long, onEnd: (() -> Unit)? = null) {
        scrimAnimator?.removeAllListeners()
        scrimAnimator?.cancel()
        val scrim = binding.root.background
        scrimAnimator = ValueAnimator.ofInt(scrim.alpha, alpha).apply {
            this.duration = duration
            addUpdateListener { scrim.alpha = it.animatedValue as Int }
            if (onEnd != null) addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = onEnd()
            })
            start()
        }
    }

    private fun animateOutAndDismiss() {
        if (dismissing) return
        dismissing = true
        pause()
        binding.cardContent.animate().alpha(0f)
            .translationY(32f * context.resources.displayMetrics.density)
            .setDuration(200).setInterpolator(AccelerateInterpolator(1.5f)).start()
        animateScrim(0, 200) { dismiss() }
    }
}
