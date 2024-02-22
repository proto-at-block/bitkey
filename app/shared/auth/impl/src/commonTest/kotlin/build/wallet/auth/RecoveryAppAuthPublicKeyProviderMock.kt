package build.wallet.auth

import build.wallet.bitkey.app.AppAuthPublicKey
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.recovery.RecoveryAppAuthPublicKeyProvider
import build.wallet.recovery.RecoveryAppAuthPublicKeyProviderError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class RecoveryAppAuthPublicKeyProviderMock : RecoveryAppAuthPublicKeyProvider {
  var getAppPublicKeyForInProgressRecoveryResult:
    Result<AppAuthPublicKey, RecoveryAppAuthPublicKeyProviderError> =
    Ok(
      AppGlobalAuthPublicKeyMock
    )

  override suspend fun getAppPublicKeyForInProgressRecovery(
    scope: AuthTokenScope,
  ): Result<AppAuthPublicKey, RecoveryAppAuthPublicKeyProviderError> {
    return getAppPublicKeyForInProgressRecoveryResult
  }

  fun reset() {
    getAppPublicKeyForInProgressRecoveryResult = Ok(AppGlobalAuthPublicKeyMock)
  }
}
