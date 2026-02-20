package build.wallet.statemachine.root

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.SoftwareAccount
import build.wallet.cloud.backup.CloudBackup
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * UI state machine that handles the no active account state.
 *
 * This state machine owns both the data layer and the presentation layer, handling:
 * - Loading states
 * - Age verification
 * - Account access options
 * - Recovery flows
 * - Emergency exit kit recovery
 */
interface NoActiveAccountUiStateMachine :
  StateMachine<NoActiveAccountUiProps, ScreenModel>

data class NoActiveAccountUiProps(
  val goToLiteAccountCreation: () -> Unit,
  val onSoftwareWalletCreated: (SoftwareAccount) -> Unit,
  val onStartLiteAccountRecovery: (CloudBackup) -> Unit,
  val onStartLiteAccountCreation: (inviteCode: String?, startIntent: StartIntent) -> Unit,
  val onCreateFullAccount: () -> Unit,
  val onViewFullAccount: (FullAccount) -> Unit,
)
