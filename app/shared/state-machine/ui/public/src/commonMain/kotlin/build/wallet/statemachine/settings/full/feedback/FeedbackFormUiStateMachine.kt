package build.wallet.statemachine.settings.full.feedback

import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.support.SupportTicketData
import build.wallet.support.SupportTicketForm

/**
 * State machine showing the Feedback form once its structure is loaded.
 */
interface FeedbackFormUiStateMachine : StateMachine<FeedbackFormUiProps, ScreenModel>

data class FeedbackFormUiProps(
  val keyboxConfig: KeyboxConfig,
  val accountId: AccountId,
  val formStructure: SupportTicketForm,
  val initialData: SupportTicketData,
  val onBack: () -> Unit,
)
