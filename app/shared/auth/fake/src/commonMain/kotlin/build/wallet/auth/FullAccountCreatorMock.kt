package build.wallet.auth

import app.cash.turbine.Turbine
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.KeyCrossDraft.WithAppKeysAndHardwareKeys
import build.wallet.bitkey.keybox.KeyboxMock
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class FullAccountCreatorMock(
  private val defaultResult: Result<FullAccount, AccountCreationError> =
    Ok(
      FullAccount(
        accountId = FullAccountId("account-id"),
        config = FullAccountConfig.fromKeyboxConfig(KeyboxMock.config),
        keybox = KeyboxMock
      )
    ),
  turbine: (String) -> Turbine<Any>,
) : FullAccountCreator {
  val createAccountCalls = turbine("createAccount calls")
  var createAccountResult = defaultResult

  override suspend fun createAccount(
    keyCrossDraft: WithAppKeysAndHardwareKeys,
  ): Result<FullAccount, AccountCreationError> {
    createAccountCalls.add(keyCrossDraft)
    return createAccountResult
  }

  fun reset() {
    createAccountResult = defaultResult
  }
}
