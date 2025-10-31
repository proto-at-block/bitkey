package bitkey.onboarding

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyCrossDraft.WithAppKeysAndHardwareKeys
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class UpgradeLiteAccountToFullServiceFake : UpgradeLiteAccountToFullService {
  var upgradeAccountResult: Result<FullAccount, FullAccountCreationError> = Ok(FullAccountMock)

  override suspend fun upgradeAccount(
    liteAccount: LiteAccount,
    keyCrossDraft: WithAppKeysAndHardwareKeys,
  ): Result<FullAccount, FullAccountCreationError> {
    return upgradeAccountResult
  }

  fun reset() {
    upgradeAccountResult = Ok(FullAccountMock)
  }
}
