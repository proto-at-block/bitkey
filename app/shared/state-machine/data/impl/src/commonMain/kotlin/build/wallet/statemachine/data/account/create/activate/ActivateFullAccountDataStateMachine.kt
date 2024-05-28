package build.wallet.statemachine.data.account.create.activate

import build.wallet.bitkey.keybox.Keybox
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.account.CreateFullAccountData

/**
 * Data state machine for managing activation of a Full Account, the final step in account creation.
 *
 * Responsible for adding "getting started" tasks and transitioning the keybox
 * from onboarding to active.
 */
interface ActivateFullAccountDataStateMachine :
  StateMachine<ActivateFullAccountDataProps, CreateFullAccountData.ActivateKeyboxDataFull>

data class ActivateFullAccountDataProps(
  val keybox: Keybox,
  val onDeleteKeyboxAndExitOnboarding: () -> Unit,
)
