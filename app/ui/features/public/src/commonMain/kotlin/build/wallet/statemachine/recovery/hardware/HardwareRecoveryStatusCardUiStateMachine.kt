package build.wallet.statemachine.recovery.hardware

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.moneyhome.card.CardModel

/**
 * State machine that calculates state of the delay + notify "lost hardware" recovery.
 */
interface HardwareRecoveryStatusCardUiStateMachine :
  StateMachine<HardwareRecoveryStatusCardUiProps, CardModel?>

data class HardwareRecoveryStatusCardUiProps(
  val account: FullAccount,
  val onClick: () -> Unit,
)
