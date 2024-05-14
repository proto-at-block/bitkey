package build.wallet.statemachine.send.fee

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.money.exchange.ExchangeRate
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

interface FeeSelectionUiStateMachine : StateMachine<FeeSelectionUiProps, BodyModel>

/**
 * @property fiatCurrency: The fiat currency to convert BTC amounts to and from.
 */
data class FeeSelectionUiProps(
  val accountData: ActiveFullAccountLoadedData,
  val recipientAddress: BitcoinAddress,
  val sendAmount: BitcoinTransactionSendAmount,
  val exchangeRates: ImmutableList<ExchangeRate>?,
  val onBack: () -> Unit,
  val onContinue: (
    EstimatedTransactionPriority,
    ImmutableMap<EstimatedTransactionPriority, Fee>,
  ) -> Unit,
)
