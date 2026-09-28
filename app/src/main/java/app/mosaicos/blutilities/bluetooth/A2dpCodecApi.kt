package app.mosaicos.blutilities.bluetooth

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothCodecConfig
import android.bluetooth.BluetoothCodecStatus
import android.bluetooth.BluetoothDevice
import android.util.Log
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

object A2dpCodecApi {
    enum class Failure { UNAVAILABLE, DENIED, NO_STATUS, FAILED }

    class AccessException(val failure: Failure) : Exception(failure.name)

    private val getCodecStatusMethod by lazy {
        lookup("getCodecStatus", BluetoothDevice::class.java)
    }
    private val setCodecConfigPreferenceMethod by lazy {
        lookup("setCodecConfigPreference", BluetoothDevice::class.java, BluetoothCodecConfig::class.java)
    }
    private val getActiveDeviceMethod by lazy { lookup("getActiveDevice") }

    private fun lookup(name: String, vararg parameters: Class<*>): Result<Method> =
        attempt(name) { BluetoothA2dp::class.java.getMethod(name, *parameters) }

    fun getCodecStatus(proxy: BluetoothA2dp, device: BluetoothDevice): Result<BluetoothCodecStatus> =
        attempt("getCodecStatus") {
            getCodecStatusMethod.getOrThrow().invoke(proxy, device) as? BluetoothCodecStatus
                ?: throw AccessException(Failure.NO_STATUS)
        }

    fun getActiveDevice(proxy: BluetoothA2dp): Result<BluetoothDevice?> =
        attempt("getActiveDevice") {
            getActiveDeviceMethod.getOrThrow().invoke(proxy) as? BluetoothDevice
        }

    // A void return only confirms submission, so the caller must verify the resulting codec.
    fun setCodecConfigPreference(
        proxy: BluetoothA2dp,
        device: BluetoothDevice,
        config: BluetoothCodecConfig
    ): Result<Unit> = attempt("setCodecConfigPreference") {
        setCodecConfigPreferenceMethod.getOrThrow().invoke(proxy, device, config)
        Unit
    }

    fun failure(error: Throwable): Failure = when (error) {
        is AccessException -> error.failure
        is InvocationTargetException -> failure(error.targetException)
        is SecurityException -> Failure.DENIED
        is ReflectiveOperationException, is LinkageError -> Failure.UNAVAILABLE
        else -> Failure.FAILED
    }

    private fun <T> attempt(operation: String, block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: Exception) {
        val cause = if (error is InvocationTargetException) error.targetException else error
        Log.w("A2dpCodecApi", "$operation failed: ${cause.javaClass.simpleName} (${failure(cause)})")
        Result.failure(AccessException(failure(cause)))
    } catch (error: LinkageError) {
        Log.w("A2dpCodecApi", "$operation unavailable: ${error.javaClass.simpleName}")
        Result.failure(AccessException(Failure.UNAVAILABLE))
    }
}
