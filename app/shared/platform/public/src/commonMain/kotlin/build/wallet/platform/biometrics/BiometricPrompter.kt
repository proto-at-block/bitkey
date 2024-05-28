package build.wallet.platform.biometrics

import com.github.michaelbull.result.Result

/**
 * Defines the actions necessary for interacting with the native biometrics prompts of respective
 * platforms
 */
interface BiometricPrompter {
  /**
   * Determines the availability of enabling biometric auth on the platform. Returns [Ok] when it is
   * available - otherwise a descriptive [BiometricError] is returned
   */
  fun biometricsAvailability(): BiometricsResult<Boolean>

  /**
   * Invoked once the caller wants to enroll Bitkey application to use biometric auth.
   *
   * *Note* This is only necessary on iOS
   */
  suspend fun enrollBiometrics(): BiometricsResult<Unit>

  /**
   * Prompts the caller to authenticate via biometric login once the setting is enabled
   *
   * TODO: W-8188 Implement and call to prompt on launch
   */
  fun promptForAuth()
}

/**
 * A custom Result type to wrap [com.github.michaelbull.result.Result] for better iOS compat in
 * Swift
 */
sealed class BiometricsResult<out V : Any> {
  abstract val result: Result<V, BiometricError>

  /**
   * Wraps [Ok] with [BiometricError] as an error type.
   */
  data class Ok<V : Any>(val value: V) : BiometricsResult<V>() {
    override val result: Result<V, BiometricError> = com.github.michaelbull.result.Ok(value)
  }

  /**
   * Wraps [Err] with [BiometricError] as an error type.
   */
  data class Err<out V : Any>(val error: BiometricError) : BiometricsResult<V>() {
    override val result: Result<V, BiometricError> = com.github.michaelbull.result.Err(error)
  }
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
