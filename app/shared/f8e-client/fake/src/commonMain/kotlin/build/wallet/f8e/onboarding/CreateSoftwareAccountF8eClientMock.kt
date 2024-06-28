package build.wallet.f8e.onboarding

import app.cash.turbine.Turbine
import build.wallet.bitkey.account.SoftwareAccountConfig
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.f8e.SoftwareAccountId
import build.wallet.crypto.PublicKey
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.CreateAccountClientErrorCode
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class CreateSoftwareAccountF8eClientMock(
  turbine: (String) -> Turbine<Any>,
) : CreateSoftwareAccountF8eClient {
  private val defaultCreateResult:
    Result<SoftwareAccountId, F8eError<CreateAccountClientErrorCode>> =
    Ok(SoftwareAccountId("account-id"))

  val createCalls = turbine("create software account calls")
  var createResult = defaultCreateResult

  override suspend fun createAccount(
    authKey: PublicKey<AppGlobalAuthKey>,
    recoveryAuthKey: PublicKey<AppRecoveryAuthKey>,
    accountConfig: SoftwareAccountConfig,
  ): Result<SoftwareAccountId, F8eError<CreateAccountClientErrorCode>> {
    createCalls.add(Unit)
    return createResult
  }

  fun reset() {
    createResult = defaultCreateResult
  }
}
