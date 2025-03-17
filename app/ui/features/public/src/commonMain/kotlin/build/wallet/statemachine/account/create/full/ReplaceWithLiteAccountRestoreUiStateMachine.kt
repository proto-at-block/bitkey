package build.wallet.statemachine.account.create.full

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * UI state machine for deleting an onboarding full account, and then restoring a lite account
 * and upgrading it to a full account.
 */
interface ReplaceWithLiteAccountRestoreUiStateMachine :
  StateMachine<ReplaceWithLiteAccountRestoreUiProps, ScreenModel>

data class ReplaceWithLiteAccountRestoreUiProps(
  val keyboxToReplace: Keybox,
  val liteAccountCloudBackup: CloudBackupV2,
  val onAccountUpgraded: (FullAccount) -> Unit,
  val onBack: () -> Unit,
)
