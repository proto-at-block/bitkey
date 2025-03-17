package build.wallet.onboarding

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class OnboardingKeyboxHardwareKeysDaoFake : OnboardingKeyboxHardwareKeysDao {
  var keys: OnboardingKeyboxHardwareKeys? = null

  override suspend fun get(): Result<OnboardingKeyboxHardwareKeys?, Error> = Ok(keys)

  override suspend fun set(keys: OnboardingKeyboxHardwareKeys): Result<Unit, Error> {
    this.keys = keys
    return Ok(Unit)
  }

  override suspend fun clear() {
    keys = null
  }
}
