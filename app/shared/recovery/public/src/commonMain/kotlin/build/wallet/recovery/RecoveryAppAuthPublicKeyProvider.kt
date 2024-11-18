package build.wallet.recovery

import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.crypto.PublicKey
import com.github.michaelbull.result.Result

/**
 * Provider for [AppAuthPublicKey] when an account is in a state of recovery.
 */
interface RecoveryAppAuthPublicKeyProvider {
  /**
   * If there is an active recovery in progress, returns the [AppRecoveryAuthPublicKey] or
   * the [AppGlobalAuthPublicKey], based on the scope, stored on the recovery object.
   * Otherwise, returns an error.
   */
  suspend fun getAppPublicKeyForInProgressRecovery(
    scope: AuthTokenScope,
  ): Result<PublicKey<out AppAuthKey>, RecoveryAppAuthPublicKeyProviderError>
}

/**
 * Errors returned by [RecoveryAppAuthPublicKeyProvider].
 */
sealed class RecoveryAppAuthPublicKeyProviderError : Error() {
  /** Failure when reading the database. Unexpected. */
  data class FailedToReadRecoveryEntity(
    override val cause: Error,
  ) : RecoveryAppAuthPublicKeyProviderError()

  /** Failure when expecting an in-progress recovery. Unexpected. */
  data object NoRecoveryInProgress : RecoveryAppAuthPublicKeyProviderError()

  /** Failure when expecting [AuthTokenScope.Recovery] and there is none stored. */
  data object NoRecoveryAuthKey : RecoveryAppAuthPublicKeyProviderError()
}
