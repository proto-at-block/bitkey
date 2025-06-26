package build.wallet.statemachine.recovery.emergencyexitkit

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

interface EmergencyExitKitRecoveryUiStateMachine :
  StateMachine<EmergencyExitKitRecoveryUiStateMachineProps, ScreenModel>

data class EmergencyExitKitRecoveryUiStateMachineProps(
  val onExit: () -> Unit,
)
