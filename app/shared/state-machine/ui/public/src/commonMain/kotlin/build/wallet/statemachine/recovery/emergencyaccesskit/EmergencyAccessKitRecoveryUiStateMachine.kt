package build.wallet.statemachine.recovery.emergencyaccesskit

import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

interface EmergencyAccessKitRecoveryUiStateMachine :
  StateMachine<EmergencyAccessKitRecoveryUiStateMachineProps, ScreenModel>

data class EmergencyAccessKitRecoveryUiStateMachineProps(
  val keyboxConfig: KeyboxConfig,
  val onExit: () -> Unit,
)
