package build.wallet.f8e.onboarding

import app.cash.turbine.Turbine
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.keybox.KeyCrossDraft
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.CreateAccountClientErrorCode
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class UpgradeAccountF8eClientMock(
  turbine: (String) -> Turbine<Any>,
) : UpgradeAccountF8eClient {
  var upgradeAccountCalls = turbine("create calls")
  var upgradeAccountResult:
    Result<UpgradeAccountF8eClient.Success, F8eError<CreateAccountClientErrorCode>> =
    Ok(UpgradeAccountF8eClient.Success(F8eSpendingKeysetMock, FullAccountIdMock))

  override suspend fun upgradeAccount(
    liteAccount: LiteAccount,
    keyCrossDraft: KeyCrossDraft.WithAppKeysAndHardwareKeys,
  ): Result<UpgradeAccountF8eClient.Success, F8eError<CreateAccountClientErrorCode>> {
    upgradeAccountCalls.add(Unit)
    return upgradeAccountResult
  }

  fun reset() {
    upgradeAccountResult = Ok(UpgradeAccountF8eClient.Success(F8eSpendingKeysetMock, FullAccountIdMock))
  }
}
