package build.wallet.auth

import app.cash.turbine.Turbine
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.account.LiteAccountConfig
import build.wallet.bitkey.keybox.LiteAccountMock
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class LiteAccountCreatorMock(
  turbine: (String) -> Turbine<Any>,
) : LiteAccountCreator {
  private val defaultCreateAccountResult: Result<LiteAccount, LiteAccountCreationError> =
    Ok(LiteAccountMock)

  val createAccountCalls = turbine("createAccount calls")
  var createAccountResult = defaultCreateAccountResult

  override suspend fun createAccount(
    config: LiteAccountConfig,
  ): Result<LiteAccount, LiteAccountCreationError> {
    createAccountCalls.add(Unit)
    return createAccountResult
  }

  fun reset() {
    createAccountResult = defaultCreateAccountResult
  }
}
