package build.wallet.statemachine.cloud.health

import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.backup.health.CloudBackupStatus
import build.wallet.cloud.backup.health.MobileKeyBackupStatus
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.StateMachine

/**
 * State Machine for repairing the App Key and EEK backups.
 *
 * Depending on the problem with the backup,
 * the state machine will ask the customer to take different actions to fix the problem.
 */
interface RepairCloudBackupStateMachine :
  StateMachine<RepairMobileKeyBackupProps, ScreenModel>

/**
 * @param mobileKeyBackupStatus current status.
 */
data class RepairMobileKeyBackupProps(
  val account: FullAccount,
  val mobileKeyBackupStatus: MobileKeyBackupStatus.ProblemWithBackup,
  val presentationStyle: ScreenPresentationStyle,
  val onExit: () -> Unit,
  val onRepaired: (status: CloudBackupStatus) -> Unit,
)
