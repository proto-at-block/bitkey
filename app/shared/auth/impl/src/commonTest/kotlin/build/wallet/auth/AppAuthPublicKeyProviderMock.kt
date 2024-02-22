package build.wallet.auth

import build.wallet.bitkey.app.AppAuthPublicKey
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class AppAuthPublicKeyProviderMock : AppAuthPublicKeyProvider {
  var getAppAuthPublicKeyFromAccountOrRecoveryResult:
    Result<AppAuthPublicKey, AuthError> = Ok(AppGlobalAuthPublicKeyMock)

  override suspend fun getAppAuthPublicKeyFromAccountOrRecovery(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    tokenScope: AuthTokenScope,
  ): Result<AppAuthPublicKey, AuthError> {
    return getAppAuthPublicKeyFromAccountOrRecoveryResult
  }

  fun reset() {
    getAppAuthPublicKeyFromAccountOrRecoveryResult = Ok(AppGlobalAuthPublicKeyMock)
  }
}
