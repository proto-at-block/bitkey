package build.wallet.statemachine.send

import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.invoice.ParsedPaymentData
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.SpeedUpTransactionDetails
import build.wallet.limit.SpendingLimit
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData
import kotlinx.collections.immutable.ImmutableMap

interface SendUiStateMachine : StateMachine<SendUiProps, ScreenModel>

/**
 * @property entryPoint - where the send flow is being launched from (pay or send button).
 * @property keybox - keybox to use for signing transfer transaction.
 * @property onExit - handler for exiting this state machine.
 * @property fiatCurrency: The fiat currency to convert BTC amounts to and from.
 * @property validInvoiceInClipboard payment information in clipboard (if any)
 * @property onDone handler for what the "Done" button does in the transfer confirmation screen.
 */
data class SendUiProps(
  val entryPoint: SendEntryPoint,
  val accountData: ActiveFullAccountLoadedData,
  val fiatCurrency: FiatCurrency,
  val validInvoiceInClipboard: ParsedPaymentData?,
  val onExit: () -> Unit,
  val onDone: () -> Unit,
)

/**
 * Represents the different ways that the customer can enter the send flow.
 */
sealed interface SendEntryPoint {
  /**
   * Customer enters send flow "Send" in the Money Home screen
   */
  data object SendButton : SendEntryPoint

  /**
   * Customer enters send flow when attempting to speed up a transaction.
   *
   * @property speedUpTransactionDetails Details about the transaction to speed up.
   * @property fiatMoney The fiat value of the transaction to speed up.
   * @property spendingLimit The user's current spending limit, if it exists.
   * @property newFeeRate The new, desired fee rate to bump the transaction to.
   * @property fees Map representing the full list of estimated fees.
   */
  data class SpeedUp(
    val speedUpTransactionDetails: SpeedUpTransactionDetails,
    val fiatMoney: FiatMoney,
    val spendingLimit: SpendingLimit?,
    val newFeeRate: FeeRate,
    val fees: ImmutableMap<EstimatedTransactionPriority, Fee>,
  ) : SendEntryPoint
}
