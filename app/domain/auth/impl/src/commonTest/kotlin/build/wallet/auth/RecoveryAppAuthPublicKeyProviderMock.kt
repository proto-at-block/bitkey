package build.wallet.auth

import bitkey.auth.AuthTokenScope
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.crypto.PublicKey
import build.wallet.recovery.RecoveryAppAuthPublicKeyProvider
import build.wallet.recovery.RecoveryAppAuthPublicKeyProviderError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class RecoveryAppAuthPublicKeyProviderMock : RecoveryAppAuthPublicKeyProvider {
  var getAppPublicKeyForInProgressRecoveryResult:
    Result<PublicKey<out AppAuthKey>, RecoveryAppAuthPublicKeyProviderError> =
    Ok(
      AppGlobalAuthPublicKeyMock
    )

  override suspend fun getAppPublicKeyForInProgressRecovery(
    scope: AuthTokenScope,
  ): Result<PublicKey<out AppAuthKey>, RecoveryAppAuthPublicKeyProviderError> {
    return getAppPublicKeyForInProgressRecoveryResult
  }

  fun reset() {
    getAppPublicKeyForInProgressRecoveryResult = Ok(AppGlobalAuthPublicKeyMock)
  }
}
