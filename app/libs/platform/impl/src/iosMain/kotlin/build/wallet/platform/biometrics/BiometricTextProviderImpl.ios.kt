package build.wallet.platform.biometrics

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import kotlinx.cinterop.ExperimentalForeignApi
import platform.LocalAuthentication.LABiometryTypeFaceID
import platform.LocalAuthentication.LABiometryTypeTouchID
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics

@BitkeyInject(AppScope::class)
@OptIn(ExperimentalForeignApi::class)
class BiometricTextProviderImpl : BiometricTextProvider {
  override fun getSettingsTitleText(): String {
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

  override fun getSettingsSecondaryText(): String {
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

  override fun getAppSecurityDescriptionText(): String {
    val context = LAContext()
      .apply {
        // policy has to be evaluated in order to determine biometry type
        canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error = null)
      }
    return when (context.biometryType) {
      LABiometryTypeTouchID -> "Unlock the app using Touch ID."
      LABiometryTypeFaceID -> "Unlock the app using Face ID."
      else -> "Unlock the app using biometrics."
    }
  }
}
