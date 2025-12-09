package build.wallet.platform.biometrics

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class BiometricPrompterMock : BiometricPrompter {
  override var isPrompting: Boolean = false

  var availabilityError: BiometricError? = null
  var enrollError: BiometricError? = null
  var promptError: BiometricError? = null

  override fun biometricsAvailability(): Result<Boolean, BiometricError> =
    availabilityError?.let { Err(it) } ?: Ok(true)

  override suspend fun enrollBiometrics(): Result<Unit, BiometricError> =
    enrollError?.let { Err(it) } ?: Ok(Unit)

  override suspend fun promptForAuth(): Result<Unit, BiometricError> {
    isPrompting = true
    return promptError?.let { error ->
      Err(error).also {
        isPrompting = false
      }
    } ?: Ok(Unit).also {
      isPrompting = false
    }
  }

  fun reset() {
    availabilityError = null
    enrollError = null
    promptError = null
  }
}
