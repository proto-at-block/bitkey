package build.wallet.statemachine.recovery.losthardware

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData
import build.wallet.statemachine.recovery.losthardware.initiate.InstructionsStyle

/** UI State Machine for navigating lost hardware recovery. */
interface LostHardwareRecoveryUiStateMachine : StateMachine<LostHardwareRecoveryProps, ScreenModel>

data class LostHardwareRecoveryProps(
  val account: FullAccount,
  val lostHardwareRecoveryData: LostHardwareRecoveryData,
  val screenPresentationStyle: ScreenPresentationStyle,
  val instructionsStyle: InstructionsStyle,
  val onFoundHardware: () -> Unit,
  val onExit: () -> Unit,
)
