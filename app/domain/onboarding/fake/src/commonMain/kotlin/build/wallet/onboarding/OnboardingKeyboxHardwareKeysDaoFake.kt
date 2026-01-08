package build.wallet.onboarding

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class OnboardingKeyboxHardwareKeysDaoFake : OnboardingKeyboxHardwareKeysDao {
  var keys: OnboardingKeyboxHardwareKeys? = null
  var shouldFail = false

  override suspend fun get(): Result<OnboardingKeyboxHardwareKeys?, Error> = Ok(keys)

  override suspend fun set(keys: OnboardingKeyboxHardwareKeys): Result<Unit, Error> {
    return if (shouldFail) {
      Err(Error())
    } else {
      this.keys = keys
      Ok(Unit)
    }
  }

  override suspend fun clear() {
    shouldFail = false
    keys = null
  }
}
