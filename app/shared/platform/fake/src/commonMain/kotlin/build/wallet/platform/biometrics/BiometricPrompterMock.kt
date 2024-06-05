package build.wallet.platform.biometrics

class BiometricPrompterMock : BiometricPrompter {
  override var isPrompting: Boolean = false

  var availabilityError: BiometricError? = null
  var enrollError: BiometricError? = null
  var promptError: BiometricError? = null

  override fun biometricsAvailability(): BiometricsResult<Boolean> {
    return availabilityError?.let { BiometricsResult.Err(it) } ?: BiometricsResult.Ok(true)
  }

  override suspend fun enrollBiometrics(): BiometricsResult<Unit> {
    return enrollError?.let { BiometricsResult.Err(it) } ?: BiometricsResult.Ok(Unit)
  }

  override suspend fun promptForAuth(): BiometricsResult<Unit> {
    isPrompting = true
    return promptError?.let {
      BiometricsResult.Err<Unit>(it).also {
        isPrompting = false
      }
    } ?: BiometricsResult.Ok(Unit).also {
      isPrompting = false
    }
  }

  fun reset() {
    availabilityError = null
    enrollError = null
    promptError = null
  }
}
