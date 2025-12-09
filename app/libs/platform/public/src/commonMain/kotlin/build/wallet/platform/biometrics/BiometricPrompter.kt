package build.wallet.platform.biometrics

import com.github.michaelbull.result.Result

/**
 * Defines the actions necessary for interacting with the native biometrics prompts of respective
 * platforms
 */
interface BiometricPrompter {
  val isPrompting: Boolean

  /**
   * Determines the availability of enabling biometric auth on the platform. Returns [Ok] when it is
   * available - otherwise a descriptive [BiometricError] is returned
   */
  fun biometricsAvailability(): Result<Boolean, BiometricError>

  /**
   * Invoked once the caller wants to enroll Bitkey application to use biometric auth.
   *
   * *Note* This is only necessary on iOS
   */
  suspend fun enrollBiometrics(): Result<Unit, BiometricError>

  /**
   * Prompts the caller to authenticate via biometric login once the setting is enabled
   */
  suspend fun promptForAuth(): Result<Unit, BiometricError>
}

/**
 * Errors that can occur when checking for biometrics, enrolling, and prompting to auth
 */
sealed class BiometricError : Error() {
  /**
   * Returned whenever a biometric operation is queried and the device does not have the
   * necessary phone hardware
   */
  class NoHardware : BiometricError()

  /**
   * Returned when there is an error retrieving the Hardware state for an operation
   */
  class HardwareUnavailable : BiometricError()

  /**
   * Returned when the biometric auth is not setup on the device
   */
  class NoBiometricEnrolled : BiometricError()

  /**
   * Returned when too many auth attempts have occurred and biometric is not currently possible
   */
  class BiometricsLocked : BiometricError()

  /**
   * Returned when an auth prompt was presented but authentication failed
   */
  class AuthenticationFailed : BiometricError()
}
