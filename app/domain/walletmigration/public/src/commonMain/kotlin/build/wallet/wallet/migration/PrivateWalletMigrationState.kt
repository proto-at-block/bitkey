package build.wallet.wallet.migration

import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.csek.SealedCsek

/**
 * Tracks the current state of a private wallet migration.
 */
sealed interface PrivateWalletMigrationState {
  /**
   * The current wallet can be migrated to a private wallet.
   */
  object Available : PrivateWalletMigrationState

  /**
   * Covers all states where the migration must be resumed before proceeding with the app.
   */
  sealed interface InProgress : PrivateWalletMigrationState

  /**
   * The app is in-process for creating a new private keyset that can be used.
   */
  sealed interface InKeysetCreation : InProgress {
    /**
     * User has tapped hardware to generate a new Hardware key.
     */
    data class HwKeyCreated(
      val newHwKeys: HwSpendingPublicKey,
    ) : InKeysetCreation

    /**
     * User has created a new app key, in addition to the hardware key.
     */
    data class AppKeyCreated(
      val newHwKeys: HwSpendingPublicKey,
      val newAppKeys: AppSpendingPublicKey,
    ) : InKeysetCreation

    /**
     * Keybox has been activated locally with the new keys.
     *
     * Funds have not yet been swept to this new keybox and the server
     * keyset has not been activated.
     * A backup needs to be performed to ensure that this
     * keybox is recoverable before performing a sweep.
     */
    data class LocalKeyboxActivated(
      val keyset: SpendingKeyset,
    ) : InKeysetCreation
  }

  /**
   * States after the new private keyset has been generated
   * and descriptor backups are made.
   */
  sealed interface InitiationComplete : InProgress {
    /**
     * A new keybox that has been activated with the new spending keys
     */
    val updatedKeybox: Keybox

    /**
     * The new private key that was created in the keybox.
     */
    val newKeyset: SpendingKeyset
  }

  /**
   * The new keyset descriptors have been backed up to F8e.
   */
  data class DescriptorBackupCompleted(
    val newKeyset: SpendingKeyset,
  ) : InProgress

  /**
   * The new server keys have been activated, making the new keyset live.
   *
   * @param updatedKeybox The updated keybox containing the new keyset.
   * @param newKeyset The new spending keyset that has been activated.
   * @param sealedCsek The current cloud encryption key to use for updating backups.
   */
  data class ServerKeysetActivated(
    override val updatedKeybox: Keybox,
    override val newKeyset: SpendingKeyset,
    val sealedCsek: SealedCsek?,
  ) : InitiationComplete

  /**
   * A cloud backup has been performed
   */
  data class CloudBackupCompleted(
    override val updatedKeybox: Keybox,
    override val newKeyset: SpendingKeyset,
  ) : InitiationComplete

  /**
   * The migration has been completed or is not enabled for this user.
   */
  object NotAvailable : PrivateWalletMigrationState
}
