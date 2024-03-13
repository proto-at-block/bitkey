package build.wallet.statemachine.recovery.emergencyaccesskit

import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

interface EmergencyAccessKitRecoveryUiStateMachine :
  StateMachine<EmergencyAccessKitRecoveryUiStateMachineProps, ScreenModel>

data class EmergencyAccessKitRecoveryUiStateMachineProps(
  val fullAccountConfig: FullAccountConfig,
  val onExit: () -> Unit,
)
