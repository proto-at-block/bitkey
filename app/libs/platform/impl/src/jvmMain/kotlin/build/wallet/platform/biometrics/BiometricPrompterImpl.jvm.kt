package build.wallet.platform.biometrics

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

@BitkeyInject(AppScope::class)
class BiometricPrompterImpl : BiometricPrompter {
  override val isPrompting: Boolean = false

  // no-op on jvm, just return error
  override fun biometricsAvailability(): Result<Boolean, BiometricError> =
    Err(BiometricError.HardwareUnavailable())

  // no-op on jvm, just return ok
  override suspend fun enrollBiometrics(): Result<Unit, BiometricError> = Ok(Unit)

  // no-op on jvm, just return ok
  override suspend fun promptForAuth(): Result<Unit, BiometricError> = Ok(Unit)
}
