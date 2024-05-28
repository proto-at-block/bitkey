package build.wallet.statemachine.moneyhome.card.gettingstarted

import build.wallet.availability.AppFunctionalityStatus
import build.wallet.bitkey.socrec.RecoveryContact
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.ui.model.alert.ButtonAlertModel

/**
 * State machine which renders a [CardModel] represent "Getting Started" in [MoneyHomeStateMachine].
 * The model is null when there is no card to show
 */
interface GettingStartedCardUiStateMachine : StateMachine<GettingStartedCardUiProps, CardModel?>

/**
 * @property trustedContacts List of current Trusted Contact relationships or invitations, which,
 *  when non empty, causes the [InviteTrustedContact] to be marked as [Complete].
 * @property onAddBitcoin Incomplete [AddBitcoin] task row clicked
 * @property onEnableSpendingLimit Incomplete [EnableSpendingLimits] task row clicked
 * @property onInviteTrustedContact Incomplete [InviteTrustedContact] task row clicked
 */
data class GettingStartedCardUiProps(
  val accountData: ActiveFullAccountLoadedData,
  val appFunctionalityStatus: AppFunctionalityStatus,
  val trustedContacts: List<RecoveryContact>,
  val onAddBitcoin: () -> Unit,
  val onEnableSpendingLimit: () -> Unit,
  val onInviteTrustedContact: () -> Unit,
  val onAddAdditionalFingerprint: () -> Unit,
  val onShowAlert: (ButtonAlertModel) -> Unit,
  val onDismissAlert: () -> Unit,
)
