package build.wallet.statemachine.recovery.cloud

import build.wallet.bitkey.account.LiteAccount
import build.wallet.cloud.backup.CloudBackup
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

interface LiteAccountCloudBackupRestorationUiStateMachine :
  StateMachine<LiteAccountCloudBackupRestorationUiProps, ScreenModel>

data class LiteAccountCloudBackupRestorationUiProps(
  val cloudBackup: CloudBackup,
  val onLiteAccountRestored: (LiteAccount) -> Unit,
  val onExit: () -> Unit,
)
