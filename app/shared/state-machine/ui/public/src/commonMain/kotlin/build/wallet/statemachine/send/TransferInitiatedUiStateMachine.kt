package build.wallet.statemachine.send

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.exchange.ExchangeRate
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.StateMachine
import kotlinx.collections.immutable.ImmutableList

/**
 * State machine for showing transfer success state.
 */
interface TransferInitiatedUiStateMachine : StateMachine<TransferInitiatedUiProps, BodyModel>

/**
 * @property onBack - handler for when the state machine exits.
 * @property onDone - handler for when the state machine is closed successfully.
 * @property fiatCurrency: The fiat currency to convert BTC amounts to and from.
 */
data class TransferInitiatedUiProps(
  val onBack: () -> Unit,
  val recipientAddress: BitcoinAddress,
  val transferInitiatedVariant: Variant,
  val estimatedTransactionPriority: EstimatedTransactionPriority,
  val fiatCurrency: FiatCurrency,
  val exchangeRates: ImmutableList<ExchangeRate>?,
  val onDone: () -> Unit,
) {
  /**
   * Represents the different types of transaction information we would show, depending on if we
   * just made a regular transaction broadcast, or a fee bump.
   */
  sealed interface Variant {
    val transferBitcoinAmount: BitcoinMoney

    /**
     * Regular transaction broadcast, shows transfer amount, fee, and total.
     */
    data class Regular(
      override val transferBitcoinAmount: BitcoinMoney,
      val feeBitcoinAmount: BitcoinMoney,
      val totalBitcoinAmount: BitcoinMoney,
    ) : Variant

    /**
     * Replace-by-fee (RBF) transaction broadcast, shows transfer amount, old fees, new fees, and
     * total.
     */
    data class SpeedUp(
      override val transferBitcoinAmount: BitcoinMoney,
      val oldFeeAmount: BitcoinMoney,
      val newFeeAmount: BitcoinMoney,
      val totalBitcoinAmount: BitcoinMoney,
    ) : Variant
  }
}
