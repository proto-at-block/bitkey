package build.wallet.statemachine.moneyhome.card.gettingstarted

import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.model.alert.ButtonAlertModel

/**
 * State machine which renders a [GettingStartedSectionModel] hosted directly by Money Home.
 * The model is null when there is no section to show.
 */
interface GettingStartedCardUiStateMachine :
  StateMachine<GettingStartedCardUiProps, GettingStartedSectionModel?>

/**
 * @property onAddBitcoin Incomplete [AddBitcoin] task row clicked
 * @property onEnableSpendingLimit Incomplete [EnableSpendingLimits] task row clicked
 */
data class GettingStartedCardUiProps(
  val onAddBitcoin: () -> Unit,
  val onEnableSpendingLimit: () -> Unit,
  val onUpdateFirmware: () -> Unit,
  val showUpdateFirmwareTile: Boolean,
  val onShowAlert: (ButtonAlertModel) -> Unit,
  val onDismissAlert: () -> Unit,
)
