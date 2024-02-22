package build.wallet.f8e.onboarding

import app.cash.turbine.Turbine
import build.wallet.bitkey.account.LiteAccountConfig
import build.wallet.bitkey.app.AppRecoveryAuthPublicKey
import build.wallet.bitkey.f8e.LiteAccountId
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.CreateAccountClientErrorCode
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class CreateLiteAccountServiceMock(
  turbine: (String) -> Turbine<Any>,
) : CreateLiteAccountService {
  private val defaultCreateResult:
    Result<LiteAccountId, F8eError<CreateAccountClientErrorCode>> =
    Ok(LiteAccountId("account-id-fake"))

  val createCalls = turbine("create lite account calls")
  var createResult = defaultCreateResult

  override suspend fun createAccount(
    recoveryKey: AppRecoveryAuthPublicKey,
    config: LiteAccountConfig,
  ): Result<LiteAccountId, F8eError<CreateAccountClientErrorCode>> {
    createCalls.add(recoveryKey)
    return createResult
  }

  fun reset() {
    createResult = defaultCreateResult
  }
}
