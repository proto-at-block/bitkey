package build.wallet.onboarding

import build.wallet.db.DbTransactionError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class OnboardingKeyboxHardwareKeysDaoFake : OnboardingKeyboxHardwareKeysDao {
  var keys: OnboardingKeyboxHardwareKeys? = null

  override suspend fun get(): Result<OnboardingKeyboxHardwareKeys?, DbTransactionError> = Ok(keys)

  override suspend fun set(keys: OnboardingKeyboxHardwareKeys): Result<Unit, DbTransactionError> {
    this.keys = keys
    return Ok(Unit)
  }

  override suspend fun clear() {
    keys = null
  }
}
