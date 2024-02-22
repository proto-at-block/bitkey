package build.wallet.statemachine.send.fee

import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.exchange.ExchangeRate
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.core.form.FormMainContentModel.FeeOptionList
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

interface FeeOptionListUiStateMachine : StateMachine<FeeOptionListProps, FeeOptionList>

data class FeeOptionListProps(
  val accountData: ActiveFullAccountLoadedData,
  val fiatCurrency: FiatCurrency,
  val transactionAmount: BitcoinMoney,
  val fees: ImmutableMap<EstimatedTransactionPriority, Fee>,
  val defaultPriority: EstimatedTransactionPriority,
  val exchangeRates: ImmutableList<ExchangeRate>?,
  val onOptionSelected: (EstimatedTransactionPriority) -> Unit,
)
