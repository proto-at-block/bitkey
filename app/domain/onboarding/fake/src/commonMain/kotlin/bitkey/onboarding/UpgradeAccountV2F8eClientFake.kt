package bitkey.onboarding

import app.cash.turbine.Turbine
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.CreateAccountClientErrorCode
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.keybox.KeyCrossDraft
import build.wallet.f8e.onboarding.UpgradeAccountV2F8eClient
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class UpgradeAccountV2F8eClientFake(
  turbine: (String) -> Turbine<Any>,
) : UpgradeAccountV2F8eClient {
  val upgradeAccountCalls = turbine("upgrade v2 calls")
  var upgradeAccountResult:
    Result<UpgradeAccountV2F8eClient.Success, F8eError<CreateAccountClientErrorCode>> =
    Ok(
      UpgradeAccountV2F8eClient.Success(
        f8eSpendingKeyset = F8eSpendingKeysetMock,
        fullAccountId = FullAccountIdMock
      )
    )

  override suspend fun upgradeAccount(
    liteAccount: LiteAccount,
    keyCrossDraft: KeyCrossDraft.WithAppKeysAndHardwareKeys,
  ): Result<UpgradeAccountV2F8eClient.Success, F8eError<CreateAccountClientErrorCode>> {
    upgradeAccountCalls.add(Unit)
    return upgradeAccountResult
  }

  fun reset() {
    upgradeAccountResult =
      Ok(
        UpgradeAccountV2F8eClient.Success(
          f8eSpendingKeyset = F8eSpendingKeysetMock,
          fullAccountId = FullAccountIdMock
        )
      )
  }
}
