package build.wallet.platform.biometrics

class BiometricPrompterFake : BiometricPrompter {
  override fun biometricsAvailability(): BiometricsResult<Boolean> {
    return BiometricsResult.Ok(true)
  }

  override suspend fun enrollBiometrics(): BiometricsResult<Unit> {
    return BiometricsResult.Ok(Unit)
  }

  override fun promptForAuth() = Unit
}
