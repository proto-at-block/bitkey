package build.wallet.recovery

import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.csek.SealedSsek

/**
 * Represents progress made towards a recovery locally. Each member represents
 * a new phase of completed progress and the data captured along the way.
 * Used to update local recovery progress via [bitkey.recovery.RecoveryStatusService].
 */
sealed interface LocalRecoveryAttemptProgress {
  /**
   * User requested recovery. We generated keys but haven't told the server yet.
   */
  data class CreatedPendingKeybundles(
    val fullAccountId: FullAccountId,
    val appKeyBundle: AppKeyBundle,
    val hwKeyBundle: HwKeyBundle,
    val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    val lostFactor: PhysicalFactor,
  ) : LocalRecoveryAttemptProgress

  /**
   * Recovery delay is completed. About to rotate auth keys.
   */
  data class AttemptingCompletion(
    val sealedCsek: SealedCsek,
    val sealedSsek: SealedSsek,
  ) : LocalRecoveryAttemptProgress

  /**
   * Attempt at rotating auth keys failed, but not in the natural flow of the app.
   * This handles the scenario where we know we were attempting to complete a recovery, but we
   * lost all in-memory representation of the attempt (via app close/crash) and when resuming,
   * there was no server recovery left. This means it either worked, or someone else canceled our recovery.
   * To see if it worked, we try and auth with our destination auth key. If that fails, we know
   * our recovery attempt failed in a way that's unrecoverable due to its cancellation. Therefore we
   * revert our state back to prior to completion, which puts us in the expected state when someone
   * cancels an in-progress recovery that is ready to complete.
   */
  data object CompletionAttemptFailedDueToServerCancellation : LocalRecoveryAttemptProgress

  /**
   * Successfully completed of recovery on the server via rotation of auth keys has successfully completed.
   */
  data object RotatedAuthKeys : LocalRecoveryAttemptProgress

  /**
   * Successfully created new spending keys on the server.
   */
  data class CreatedSpendingKeys(
    val f8eSpendingKeyset: F8eSpendingKeyset,
  ) : LocalRecoveryAttemptProgress

  /**
   * Successfully activated the spending keys on the server.
   */
  data class ActivatedSpendingKeys(
    val f8eSpendingKeyset: F8eSpendingKeyset,
  ) : LocalRecoveryAttemptProgress

  /**
   * Successfully uploaded encrypted descriptor backups to F8e after creating spending keys.
   */
  data class UploadedDescriptorBackups(
    val spendingKeysets: List<SpendingKeyset>,
  ) : LocalRecoveryAttemptProgress

  /**
   * Successfully sealed and backed up the DDK with new hardware key
   */
  data object DdkBackedUp : LocalRecoveryAttemptProgress

  /**
   * Successfully backed up the new auth and spending keys to the cloud.
   */
  data object BackedUpToCloud : LocalRecoveryAttemptProgress

  /**
   * A sweep has been begun, but has not yet completed.
   */
  data object SweepingFunds : LocalRecoveryAttemptProgress

  /**
   * Acknowledged sweep completion and exited recovery.
   *
   * Updating the `RecoveryStatusService` with this value will remove the local recovery attempt
   * from the dao.
   */
  data class CompletedRecovery(
    val keyboxToActivate: Keybox,
  ) : LocalRecoveryAttemptProgress
}
