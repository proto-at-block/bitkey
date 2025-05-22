package build.wallet.recovery

import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.challange.SignedChallenge.HardwareSignedChallenge
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.cloud.backup.csek.SealedCsek
import com.github.michaelbull.result.Result

interface RecoveryAuthCompleter {
  /**
   * Complete rotation of auth keys for recovery.
   *
   * @param fullAccountId account retrieved from f8e during recovery initiation phase.
   * @param challenge challenge to be signed by [destinationAppGlobalAuthPubKey]'s
   * private key. The signed challenge will be used by f8e to approve completion of recovery.
   * @param destinationAppGlobalAuthPubKey app's new [AppGlobalAuthPublicKey].
   *
   * @return f8e tokens after successful authentication.
   */
  suspend fun rotateAuthKeys(
    fullAccountId: FullAccountId,
    hardwareSignedChallenge: HardwareSignedChallenge,
    destinationAppAuthPubKeys: AppAuthPublicKeys,
    sealedCsek: SealedCsek,
  ): Result<Unit, Throwable>

  /**
   * Rotates the auth tokens for the given [FullAccountId] to the given [AppAuthPublicKeys]
   * and optionally removes all Recovery Contacts.
   *
   * @param f8eEnvironment The environment to use for the auth token rotation
   * @param fullAccountId The [FullAccountId] to rotate the auth tokens for
   * @param destinationAppAuthPubKeys The [AppAuthPublicKeys] to rotate the auth tokens to
   */
  suspend fun rotateAuthTokens(
    fullAccountId: FullAccountId,
    destinationAppAuthPubKeys: AppAuthPublicKeys,
  ): Result<Unit, Throwable>
}
