package build.wallet.statemachine.cloud.health

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * UI state machine for viewing and managing health of Cloud backups (mobile key and EAK).
 */
interface CloudBackupHealthDashboardUiStateMachine :
  StateMachine<CloudBackupHealthDashboardProps, ScreenModel>

data class CloudBackupHealthDashboardProps(
  val account: FullAccount,
  val onExit: () -> Unit,
)
