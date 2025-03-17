package build.wallet.statemachine.partnerships.expected

import build.wallet.bitkey.account.Account
import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.PartnerRedirectionMethod
import build.wallet.partnerships.PartnershipEvent
import build.wallet.partnerships.PartnershipTransactionId
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import kotlinx.datetime.LocalDateTime

/**
 * Modal screen to display information about a new expected transaction.
 *
 * This screen is used when we are redirected back from a partner application
 * to inform the user that their transaction is being processed and should
 * be seen in the app in the near future.
 */
interface ExpectedTransactionNoticeUiStateMachine : StateMachine<ExpectedTransactionNoticeProps, ScreenModel>

data class ExpectedTransactionNoticeProps(
  val account: Account,
  val partner: PartnerId?,
  val event: PartnershipEvent?,
  val partnerTransactionId: PartnershipTransactionId?,
  val receiveTime: LocalDateTime,
  val onViewInPartnerApp: (PartnerRedirectionMethod) -> Unit,
  val onBack: () -> Unit,
)
