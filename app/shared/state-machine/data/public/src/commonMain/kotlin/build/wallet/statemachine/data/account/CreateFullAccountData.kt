package build.wallet.statemachine.data.account

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.onboarding.CreateFullAccountContext

/**
 * States representing the process of creating a brand new Full Account.
 *
 * [CreateFullAccountData] is divided into three main states:
 * - CreateKeybox: Create keys and pair with HW and server
 * - OnboardKeybox: Back up data and set up notifications
 * - ActivateKeybox: Keybox transitions from onboarding -> active
 */
sealed interface CreateFullAccountData {
  /**
   * Indicates that we are in process of creating a new full account.
   */
  data class CreatingAccountData(
    val context: CreateFullAccountContext,
    val rollback: () -> Unit,
  ) : CreateFullAccountData

  /**
   * The account is being backup to cloud and notifications are being setup.
   * The onboarding needs to be complete before account activation.
   */
  data class OnboardingAccountData(
    val keybox: Keybox,
    val isSkipCloudBackupInstructions: Boolean,
    val onFoundLiteAccountWithDifferentId: (cloudBackup: CloudBackupV2) -> Unit,
    val onOverwriteFullAccountCloudBackupWarning: () -> Unit,
    val onOnboardingComplete: () -> Unit,
  ) : CreateFullAccountData

  /**
   * The account is being activated, transitioning from the onboarding account to the active one.
   */
  data class ActivatingAccountData(
    val keybox: Keybox,
  ) : CreateFullAccountData

  /**
   * We found a full account cloud backup, but a new keybox is being onboarded. The user is given
   * the option to overwrite it or cancel onboarding the new keybox.
   */
  data class OverwriteFullAccountCloudBackupData(
    val keybox: Keybox,
    val onOverwrite: () -> Unit,
    val rollback: () -> Unit,
  ) : CreateFullAccountData

  /**
   * We found a lite account backup. The keybox is being offboarded, the full account deleted, and
   * then the lite account restored to be upgraded to a full account.
   */
  data class ReplaceWithLiteAccountRestoreData(
    val keyboxToReplace: Keybox,
    val liteAccountCloudBackup: CloudBackupV2,
    val onAccountUpgraded: (FullAccount) -> Unit,
    val onBack: () -> Unit,
  ) : CreateFullAccountData
}
