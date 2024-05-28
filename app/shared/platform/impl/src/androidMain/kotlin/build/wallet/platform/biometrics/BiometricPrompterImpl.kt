package build.wallet.platform.biometrics

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import build.wallet.platform.PlatformContext

class BiometricPrompterImpl(platformContext: PlatformContext) : BiometricPrompter {
  private val biometricManager by lazy {
    BiometricManager.from(platformContext.appContext)
  }

  override fun biometricsAvailability(): BiometricsResult<Boolean> {
    return when (biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)) {
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

  override fun promptForAuth() {
    // TODO W-xxxx show android auth prompt
  }
}
