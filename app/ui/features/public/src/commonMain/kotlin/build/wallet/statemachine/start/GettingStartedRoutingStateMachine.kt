package build.wallet.statemachine.start

import build.wallet.cloud.backup.CloudBackup
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.AccountData.StartIntent

/**
 * Getting Started experience that is routed based on the state of users'
 * backups as well as a navigation intent.
 */
interface GettingStartedRoutingStateMachine : StateMachine<GettingStartedRoutingProps, ScreenModel>

/**
 * Props for the Getting Started Routing state machine.
 *
 * Because the user may get routed to recovery *or* lite account onboarding
 * depending on the backup state, all props need to be present at the start.
 */
data class GettingStartedRoutingProps(
  val startIntent: StartIntent,
  val inviteCode: String? = null,
  val onStartLiteAccountCreation: (String?) -> Unit,
  val onStartLiteAccountRecovery: (CloudBackup) -> Unit,
  val onStartCloudRecovery: (CloudBackup) -> Unit,
  val onStartLostAppRecovery: () -> Unit,
  val onImportEmergencyExitKit: () -> Unit,
  val onExit: () -> Unit,
)
