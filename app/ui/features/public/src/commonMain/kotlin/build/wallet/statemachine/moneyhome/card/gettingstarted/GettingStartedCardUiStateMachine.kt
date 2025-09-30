package build.wallet.statemachine.moneyhome.card.gettingstarted

import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.ui.model.alert.ButtonAlertModel

/**
 * State machine which renders a [CardModel] represent "Getting Started" in [MoneyHomeStateMachine].
 * The model is null when there is no card to show
 */
interface GettingStartedCardUiStateMachine : StateMachine<GettingStartedCardUiProps, CardModel?>

/**
 * @property onAddBitcoin Incomplete [AddBitcoin] task row clicked
 * @property onEnableSpendingLimit Incomplete [EnableSpendingLimits] task row clicked
 */
data class GettingStartedCardUiProps(
  val onAddBitcoin: () -> Unit,
  val onEnableSpendingLimit: () -> Unit,
  val onShowAlert: (ButtonAlertModel) -> Unit,
  val onDismissAlert: () -> Unit,
)
