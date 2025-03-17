package build.wallet.statemachine.send

import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitkey.factor.SigningFactor
import build.wallet.money.BitcoinMoney
import build.wallet.money.Money
import build.wallet.money.exchange.ExchangeRate
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import kotlinx.collections.immutable.ImmutableList

/**
 * State machine for entering a money transfer amount for bitcoin transaction.
 */
interface TransferAmountEntryUiStateMachine : StateMachine<TransferAmountEntryUiProps, ScreenModel>

data class ContinueTransferParams(
  val sendAmount: BitcoinTransactionSendAmount,
)

/**
 * @property onBack - handler for exiting this state machine.
 * @property initialAmount - initial bitcoin transfer amount.
 * @property exchangeRates - exchange rates to use for currency conversion. We do this so we can use
 * a consistent set of exchange rates for the entire send flow.
 * @property onContinueClick - handler for proceeding with the transaction. Takes in a transfer
 * [Money] amount, and a [SigningFactor] which indicates the secondary signing factor (app being
 * the first signing factor).
 */
data class TransferAmountEntryUiProps(
  val onBack: () -> Unit,
  val initialAmount: Money,
  val minAmount: BitcoinMoney? = null,
  val maxAmount: BitcoinMoney? = null,
  val exchangeRates: ImmutableList<ExchangeRate>?,
  val allowSendAll: Boolean = true,
  val onContinueClick: (ContinueTransferParams) -> Unit,
)

sealed interface TransferAmountUiState {
  sealed interface ValidAmountEnteredUiState : TransferAmountUiState {
    /** Amount is within limits and does not require hardware signing. */
    data object AmountBelowBalanceUiState : ValidAmountEnteredUiState

    /** Amount equal or above available funds. This is valid because we will send all. */
    data object AmountEqualOrAboveBalanceUiState : ValidAmountEnteredUiState
  }

  /** Invalid amount entered with Send Max feature flag turned on, not able to proceed. */
  sealed interface InvalidAmountEnteredUiState : TransferAmountUiState {
    /** User entered an amount while having a zero balance */
    data object AmountWithZeroBalanceUiState : InvalidAmountEnteredUiState

    /** Amount is too small to send. */
    data object AmountBelowDustLimitUiState : InvalidAmountEnteredUiState

    /** Amount is too large to send and send all is not available */
    data object InvalidAmountEqualOrAboveBalanceUiState : InvalidAmountEnteredUiState

    /** Amount is less than the minimum allowed amount. */
    data object AmountBelowMinimumUiState : InvalidAmountEnteredUiState

    /** Amount is greater than the maximum allowed amount. */
    data object AmountAboveMaximumUiState : InvalidAmountEnteredUiState
  }
}
