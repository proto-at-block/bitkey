package build.wallet.platform.biometrics

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.*
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@BitkeyInject(ActivityScope::class)
class BiometricPrompterImpl(private val activity: FragmentActivity) : BiometricPrompter {
  override var isPrompting: Boolean = false
    private set

  private val biometricManager by lazy {
    BiometricManager.from(activity)
  }

  override fun biometricsAvailability(): BiometricsResult<Boolean> {
    return when (biometricManager.canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL)) {
      BiometricManager.BIOMETRIC_SUCCESS -> BiometricsResult.Ok(true)
      BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricsResult.Err(BiometricError.NoHardware())
      BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricsResult.Err(BiometricError.HardwareUnavailable())
      BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricsResult.Err(BiometricError.NoBiometricEnrolled())
      else -> BiometricsResult.Err(BiometricError.HardwareUnavailable())
    }
  }

  override suspend fun enrollBiometrics(): BiometricsResult<Unit> {
    // biometrics aren't enrolled on android
    return BiometricsResult.Ok(Unit)
  }

  override suspend fun promptForAuth(): BiometricsResult<Unit> =
    suspendCancellableCoroutine { continuation ->
      isPrompting = true
      val executor = ContextCompat.getMainExecutor(activity)
      val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
          override fun onAuthenticationError(
            errorCode: Int,
            errString: CharSequence,
          ) {
            if (continuation.isActive) {
              isPrompting = false
              continuation.resume(BiometricsResult.Err(BiometricError.AuthenticationFailed()))
            }
          }

          override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            if (continuation.isActive) {
              isPrompting = false
              continuation.resume(BiometricsResult.Ok(Unit))
            }
          }
        }
      )
      continuation.invokeOnCancellation { prompt.cancelAuthentication() }
      val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Biometric login for Bitkey")
        .setSubtitle("Log in using your biometric credential")
        .setConfirmationRequired(false)
        .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
        .build()

      prompt.authenticate(promptInfo)
    }
}
