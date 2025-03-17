package build.wallet.statemachine.data.recovery.losthardware

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.recovery.Recovery
import build.wallet.statemachine.core.StateMachine

/** Data State Machine for executing lost hardware recovery. */
interface LostHardwareRecoveryDataStateMachine : StateMachine<LostHardwareRecoveryProps, LostHardwareRecoveryData>

data class LostHardwareRecoveryProps(
  val account: FullAccount,
  val hardwareRecovery: Recovery.StillRecovering?,
) {
  init {
    hardwareRecovery?.let {
      require(it.factorToRecover == Hardware)
    }
  }
}
