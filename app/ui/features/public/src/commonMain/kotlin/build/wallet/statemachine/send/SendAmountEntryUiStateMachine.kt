package build.wallet.statemachine.send

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.PsbtsForSendAmount
import build.wallet.money.Money
import build.wallet.money.exchange.ExchangeRate
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import kotlinx.collections.immutable.ImmutableList

/**
 * State machine for entering a send amount in the send flow.
 *
 * This wraps the [TransferAmountEntryUiStateMachine] to provide
 * send-specific customizations and feature gating.
 */
interface SendAmountEntryUiStateMachine : StateMachine<SendAmountEntryUiProps, ScreenModel>

/**
 * @property recipientAddress - the bitcoin address to send to.
 * @property onBack - handler for exiting this state machine.
 * @property initialAmount - initial bitcoin transfer amount.
 * @property exchangeRates - exchange rates to use for currency conversion. We do this so we can use
 * a consistent set of exchange rates for the entire send flow.
 * @property onContinueClick - handler for proceeding with the transaction without pre-built PSBTs.
 * @property onContinueWithPreBuiltPsbts - handler for proceeding with pre-built PSBTs when the
 *   feature flag is enabled.
 */
data class SendAmountEntryUiProps(
  val recipientAddress: BitcoinAddress,
  val onBack: () -> Unit,
  val initialAmount: Money,
  val exchangeRates: ImmutableList<ExchangeRate>?,
  val allowSendAll: Boolean = true,
  val onContinueClick: (BitcoinTransactionSendAmount) -> Unit,
  val onContinueWithPreBuiltPsbts: (
    BitcoinTransactionSendAmount,
    PsbtsForSendAmount,
  ) -> Unit = { _, _ -> },
)
