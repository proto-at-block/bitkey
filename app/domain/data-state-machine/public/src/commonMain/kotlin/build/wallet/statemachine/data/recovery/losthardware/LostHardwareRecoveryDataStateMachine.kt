package build.wallet.statemachine.data.recovery.losthardware

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.StateMachine

/** Data State Machine for executing lost hardware recovery. */
interface LostHardwareRecoveryDataStateMachine : StateMachine<LostHardwareRecoveryDataProps, LostHardwareRecoveryData>

data class LostHardwareRecoveryDataProps(
  val account: FullAccount,
)
