package bitkey.onboarding

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyCrossDraft.WithAppKeysAndHardwareKeys
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class CreateFullAccountServiceFake : CreateFullAccountService {
  var createAccountResult: Result<FullAccount, FullAccountCreationError> = Ok(FullAccountMock)

  override suspend fun createAccount(
    keyCrossDraft: WithAppKeysAndHardwareKeys,
  ): Result<FullAccount, FullAccountCreationError> {
    return createAccountResult
  }

  fun reset() {
    createAccountResult = Ok(FullAccountMock)
  }
}
