package build.wallet.recovery

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.auth.AccountAuthTokens
import build.wallet.auth.AccountAuthTokensMock
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.recovery.LostAppRecoveryAuthenticator.DelayNotifyLostAppAuthError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class LostAppRecoveryAuthenticatorMock(
  turbine: (String) -> Turbine<Any>,
) : LostAppRecoveryAuthenticator {
  var authenticationResult: Result<AccountAuthTokens, DelayNotifyLostAppAuthError> =
    Ok(AccountAuthTokensMock)
  val authenticateCalls = turbine("LostAppRecoveryAuthenticatorMock authenticate calls")

  override suspend fun authenticate(
    fullAccountConfig: FullAccountConfig,
    fullAccountId: FullAccountId,
    authResponseSessionToken: String,
    hardwareAuthSignature: String,
    hardwareAuthPublicKey: HwAuthPublicKey,
  ): Result<AccountAuthTokens, DelayNotifyLostAppAuthError> {
    authenticateCalls += Unit
    return authenticationResult
  }

  fun reset() {
    authenticationResult = Ok(AccountAuthTokensMock)
  }
}
