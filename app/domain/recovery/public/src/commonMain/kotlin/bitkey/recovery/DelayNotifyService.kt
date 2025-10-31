package bitkey.recovery

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.challange.SignedChallenge.HardwareSignedChallenge
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.crypto.PublicKey
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Result

/**
 * High–level API for orchestrating Delay & Notify (D&N) account recovery flows.
 */
interface DelayNotifyService {
  /**
   * Cancels an in-progress Delay & Notify recovery.
   *
   * Depending on the request type, this will cancel either a lost app and cloud recovery
   * or a lost hardware recovery. The operation is performed against the backend and
   * updates local recovery status accordingly.
   */
  suspend fun cancelDelayNotify(request: DelayNotifyCancellationRequest): Result<Unit, Error>

  /**
   * Activates a previously created f8e spending keyset during recovery.
   * This is idempotent - calling it for an already active keyset will succeed.
   * Only one key can be activated per D&N.
   */
  suspend fun activateSpendingKeyset(
    keyset: F8eSpendingKeyset,
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, Error>

  /**
   * Creates a new f8e spending keyset without activating it during recovery.
   *
   * Also handles device token registration and updates local recovery progress.
   */
  suspend fun createSpendingKeyset(
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ): Result<F8eSpendingKeyset, Error>

  /**
   * Complete rotation of auth keys for recovery.
   *
   * The signed challenge will be used by f8e to approve completion of recovery.
   */
  suspend fun rotateAuthKeys(
    hardwareSignedChallenge: HardwareSignedChallenge,
    sealedCsek: SealedCsek,
    sealedSsek: SealedSsek,
  ): Result<Unit, Error>

  /**
   * Rotates the auth tokens during recovery to the new auth keys.
   */
  suspend fun rotateAuthTokens(): Result<Unit, Throwable>

  /**
   * Verifies that authentication works with the new app global and recovery auth keys
   * after they have been rotated during recovery.
   *
   * This is used to verify that the recovery completion was successful.
   */
  suspend fun verifyAuthKeysAfterRotation(): Result<Unit, Error>

  /**
   * Regenerates trusted contact certificates using the updated auth keys after recovery.
   *
   * This operation:
   * 1. Fetches the latest trusted contacts from f8e
   * 2. Re-authenticates and regenerates certificates with new auth keys
   * 3. Re-syncs and stores relationships locally
   *
   * @param oldAppGlobalAuthKey The previous app global auth key (before recovery), or null if not available
   */
  suspend fun regenerateTrustedContactCertificates(
    oldAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>?,
  ): Result<Unit, Error>

  /**
   * Removes all protected customer relationships during recovery.
   *
   * This is typically called when we cannot restore the delegated decryption key,
   * so we need to remove all protected customers since we can no longer decrypt
   * their data.
   */
  suspend fun removeTrustedContacts(): Result<Unit, Error>
}
