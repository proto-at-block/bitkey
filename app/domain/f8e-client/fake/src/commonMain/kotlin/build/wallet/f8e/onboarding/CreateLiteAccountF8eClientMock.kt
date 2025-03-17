package build.wallet.f8e.onboarding

import app.cash.turbine.Turbine
import bitkey.account.LiteAccountConfig
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.CreateAccountClientErrorCode
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.f8e.LiteAccountId
import build.wallet.crypto.PublicKey
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class CreateLiteAccountF8eClientMock(
  turbine: (String) -> Turbine<Any>,
) : CreateLiteAccountF8eClient {
  private val defaultCreateResult:
    Result<LiteAccountId, F8eError<CreateAccountClientErrorCode>> =
    Ok(LiteAccountId("account-id-fake"))

  val createCalls = turbine("create lite account calls")
  var createResult = defaultCreateResult

  override suspend fun createAccount(
    recoveryKey: PublicKey<AppRecoveryAuthKey>,
    config: LiteAccountConfig,
  ): Result<LiteAccountId, F8eError<CreateAccountClientErrorCode>> {
    createCalls.add(recoveryKey)
    return createResult
  }

  fun reset() {
    createResult = defaultCreateResult
  }
}
