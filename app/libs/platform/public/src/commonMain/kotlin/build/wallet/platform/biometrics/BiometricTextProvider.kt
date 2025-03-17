package build.wallet.platform.biometrics

/**
 * This class provides text as strings related to describing biometric authentication with relevant
 * platform and device hardware information
 *
 * *Note: This used expect/actual under the hood to account for platform differences
 *
 * Example: iOS, Face ID vs Touch ID
 */
interface BiometricTextProvider {
  /**
   * Provides the necessary text for the biometric feature within the settings menu
   */
  fun getSettingsTitleText(): String

  /**
   * Provides the descriptive text for the biometric feature within the settings menu
   */
  fun getSettingsSecondaryText(): String
}
