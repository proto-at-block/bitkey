package build.wallet.platform.biometrics

import kotlinx.cinterop.ExperimentalForeignApi
import platform.LocalAuthentication.LABiometryTypeFaceID
import platform.LocalAuthentication.LABiometryTypeTouchID
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics

@OptIn(ExperimentalForeignApi::class)
actual class BiometricTextProviderImpl : BiometricTextProvider {
  actual override fun getSettingsTitleText(): String {
    val context = LAContext()
      .apply {
        // policy has to be evaluated in order to determine biometry type
        canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error = null)
      }
    return when (context.biometryType) {
      LABiometryTypeTouchID -> "Touch ID"
      LABiometryTypeFaceID -> "Face ID"
      else -> "Biometrics"
    }
  }

  actual override fun getSettingsSecondaryText(): String {
    val context = LAContext()
      .apply {
        // policy has to be evaluated in order to determine biometry type
        canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error = null)
      }
    return when (context.biometryType) {
      LABiometryTypeTouchID -> "Use Touch ID to unlock app"
      LABiometryTypeFaceID -> "Use Face ID to unlock app"
      else -> "Use Biometrics to unlock app"
    }
  }
}
