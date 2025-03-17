package build.wallet.auth

import bitkey.auth.AuthTokenScope
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.f8e.AccountId
import build.wallet.crypto.PublicKey
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class AppAuthPublicKeyProviderMock : AppAuthPublicKeyProvider {
  var getAppAuthPublicKeyFromAccountOrRecoveryResult:
    Result<PublicKey<out AppAuthKey>, AuthError> = Ok(AppGlobalAuthPublicKeyMock)

  override suspend fun getAppAuthPublicKeyFromAccountOrRecovery(
    accountId: AccountId,
    tokenScope: AuthTokenScope,
  ): Result<PublicKey<out AppAuthKey>, AuthError> {
    return getAppAuthPublicKeyFromAccountOrRecoveryResult
  }

  fun reset() {
    getAppAuthPublicKeyFromAccountOrRecoveryResult = Ok(AppGlobalAuthPublicKeyMock)
  }
}
