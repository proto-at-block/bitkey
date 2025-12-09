package build.wallet.platform.biometrics

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.cinterop.*
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSError
import platform.LocalAuthentication.*
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
@BitkeyInject(AppScope::class)
class BiometricPrompterImpl : BiometricPrompter {
  override var isPrompting: Boolean = false
    private set

  @OptIn(BetaInteropApi::class)
  override fun biometricsAvailability(): Result<Boolean, BiometricError> =
    memScoped {
      val context = LAContext()
      val errorPtr = alloc<ObjCObjectVar<NSError?>>().apply { value = null }
      val canEvaluate =
        context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = errorPtr.ptr)
      if (canEvaluate) {
        Ok(true)
      } else {
        Err(errorPtr.value.toAvailabilityError())
      }
    }

  override suspend fun enrollBiometrics(): Result<Unit, BiometricError> =
    evaluatePolicy(errorMapper = { error -> error.toEnrollmentError() })

  override suspend fun promptForAuth(): Result<Unit, BiometricError> =
    evaluatePolicy(errorMapper = { BiometricError.AuthenticationFailed() })

  private suspend fun evaluatePolicy(
    errorMapper: (NSError?) -> BiometricError,
  ): Result<Unit, BiometricError> =
    suspendCancellableCoroutine { continuation ->
      val context = LAContext()
      isPrompting = true
      context.evaluatePolicy(
        policy = LAPolicyDeviceOwnerAuthentication,
        localizedReason = LOCALIZED_REASON
      ) { success, error ->
        if (continuation.isActive) {
          val result =
            if (success) {
              Ok(Unit)
            } else {
              Err(errorMapper(error))
            }
          isPrompting = false
          continuation.resume(result)
        }
      }
      continuation.invokeOnCancellation {
        context.invalidate()
        isPrompting = false
      }
    }

  private fun NSError?.toAvailabilityError(): BiometricError =
    when (laErrorCode) {
      LAErrorBiometryNotEnrolled -> BiometricError.NoBiometricEnrolled()
      LAErrorBiometryLockout -> BiometricError.BiometricsLocked()
      LAErrorBiometryNotAvailable -> BiometricError.HardwareUnavailable()
      else -> BiometricError.HardwareUnavailable()
    }

  private fun NSError?.toEnrollmentError(): BiometricError =
    when (laErrorCode) {
      LAErrorAuthenticationFailed -> BiometricError.AuthenticationFailed()
      LAErrorBiometryNotAvailable -> BiometricError.HardwareUnavailable()
      LAErrorBiometryNotEnrolled -> BiometricError.NoBiometricEnrolled()
      LAErrorBiometryLockout -> BiometricError.BiometricsLocked()
      else -> BiometricError.HardwareUnavailable()
    }

  private val NSError?.laErrorCode: Long?
    get() = this?.takeIf { it.domain == LAErrorDomain }?.code

  companion object {
    private const val LOCALIZED_REASON = "To use secure features"
  }
}
