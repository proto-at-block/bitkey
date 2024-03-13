package build.wallet.statemachine.settings.full.feedback

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.support.SupportTicketData
import build.wallet.support.SupportTicketForm

/**
 * State machine showing the Feedback form once its structure is loaded.
 */
interface FeedbackFormUiStateMachine : StateMachine<FeedbackFormUiProps, ScreenModel>

data class FeedbackFormUiProps(
  val f8eEnvironment: F8eEnvironment,
  val accountId: AccountId,
  val formStructure: SupportTicketForm,
  val initialData: SupportTicketData,
  val addAttachmentsEnabled: Boolean,
  val onBack: () -> Unit,
)
