package build.wallet.auth

import app.cash.turbine.Turbine
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.KeyCrossDraft.WithAppKeysAndHardwareKeys
import build.wallet.bitkey.keybox.KeyboxMock
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class LiteToFullAccountUpgraderMock(
  turbine: (String) -> Turbine<Any>,
) : LiteToFullAccountUpgrader {
  val upgradeAccountCalls = turbine("upgradeAccount calls")
  var upgradeAccountResult =
    Ok(
      FullAccount(
        accountId = FullAccountId("account-id"),
        config = KeyboxMock.config,
        keybox = KeyboxMock
      )
    )

  override suspend fun upgradeAccount(
    liteAccount: LiteAccount,
    keyCrossDraft: WithAppKeysAndHardwareKeys,
  ): Result<FullAccount, AccountCreationError> {
    upgradeAccountCalls.add(keyCrossDraft)
    return upgradeAccountResult
  }

  fun reset() {
    upgradeAccountResult =
      Ok(
        FullAccount(
          accountId = FullAccountId("account-id"),
          config = KeyboxMock.config,
          keybox = KeyboxMock
        )
      )
  }
}
