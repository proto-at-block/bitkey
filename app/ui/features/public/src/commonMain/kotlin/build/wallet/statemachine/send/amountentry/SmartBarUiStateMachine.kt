package build.wallet.statemachine.send.amountentry

import androidx.compose.runtime.Immutable
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.send.TransferAmountUiState

/**
 * Renders contextual information beneath the amount on the transfer screen — currently a
 * tappable "Send Max" hint when the entered amount meets or exceeds the available balance.
 */
data class SmartBarModel(
  val title: LabelModel.StringWithStyledSubstringModel,
  val onClick: () -> Unit,
)

interface SmartBarUiStateMachine : StateMachine<SmartBarUiProps, SmartBarModel?>

@Immutable
data class SmartBarUiProps(
  val transferAmountState: TransferAmountUiState,
  val onSendMaxClick: () -> Unit,
)
