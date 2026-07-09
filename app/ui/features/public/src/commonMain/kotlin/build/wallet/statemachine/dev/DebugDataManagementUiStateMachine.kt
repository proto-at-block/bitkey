package build.wallet.statemachine.dev

import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.StateMachine

interface DebugDataManagementUiStateMachine : StateMachine<DebugDataManagementProps, BodyModel>

data class DebugDataManagementProps(
  val screen: DebugDataManagementScreen,
  val onBack: () -> Unit,
)

enum class DebugDataManagementScreen {
  ManualKeyDeletion,
  RecoveryScenarioPresets,
}
