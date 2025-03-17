package build.wallet.statemachine.export

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * UI flow that allows a customer to export their wallet descriptor and transaction history.
 */
interface ExportToolsUiStateMachine : StateMachine<ExportToolsUiProps, ScreenModel>

data class ExportToolsUiProps(
  val onBack: () -> Unit,
)
