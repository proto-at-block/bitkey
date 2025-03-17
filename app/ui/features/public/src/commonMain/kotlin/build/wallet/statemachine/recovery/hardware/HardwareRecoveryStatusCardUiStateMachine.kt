package build.wallet.statemachine.recovery.hardware

import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData
import build.wallet.statemachine.moneyhome.card.CardModel

/**
 * State machine that calculates state of the delay + notify "lost hardware" recovery.
 */
interface HardwareRecoveryStatusCardUiStateMachine :
  StateMachine<HardwareRecoveryStatusCardUiProps, CardModel?>

data class HardwareRecoveryStatusCardUiProps(
  val lostHardwareRecoveryData: LostHardwareRecoveryData,
  val onClick: () -> Unit,
)
