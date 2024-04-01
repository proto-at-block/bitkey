package build.wallet.recovery

import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.crypto.PublicKey
import build.wallet.recovery.RecoveryAppAuthPublicKeyProviderError.FailedToReadRecoveryEntity
import build.wallet.recovery.RecoveryAppAuthPublicKeyProviderError.NoRecoveryInProgress
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.flow.first

class RecoveryAppAuthPublicKeyProviderImpl(
  val recoveryDao: RecoveryDao,
) : RecoveryAppAuthPublicKeyProvider {
  override suspend fun getAppPublicKeyForInProgressRecovery(
    scope: AuthTokenScope,
  ): Result<PublicKey<out AppAuthKey>, RecoveryAppAuthPublicKeyProviderError> {
    return binding {
      // Get the recovery status from the Dao
      val recovery =
        recoveryDao.activeRecovery().first()
          .mapError { FailedToReadRecoveryEntity(it) }
          .bind()

      // Return the [AppGlobalAuthPublicKey], if available.
      when (recovery) {
        is Recovery.StillRecovering -> {
          when (scope) {
            AuthTokenScope.Global -> Ok(recovery.appGlobalAuthKey)
            AuthTokenScope.Recovery -> Ok(recovery.appRecoveryAuthKey)
          }
        }
        else -> Err(NoRecoveryInProgress)
      }.bind()
    }
  }
}
