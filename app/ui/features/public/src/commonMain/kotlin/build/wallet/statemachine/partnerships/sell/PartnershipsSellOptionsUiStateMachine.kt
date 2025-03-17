package build.wallet.statemachine.partnerships.sell

import build.wallet.money.BitcoinMoney
import build.wallet.money.exchange.ExchangeRate
import build.wallet.partnerships.PartnerRedirectionMethod
import build.wallet.partnerships.PartnershipTransaction
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import kotlinx.collections.immutable.ImmutableList

interface PartnershipsSellOptionsUiStateMachine :
  StateMachine<PartnershipsSellOptionsUiProps, ScreenModel>

data class PartnershipsSellOptionsUiProps(
  val sellAmount: BitcoinMoney,
  val exchangeRates: ImmutableList<ExchangeRate>?,
  val onBack: () -> Unit,
  val onPartnerRedirected: (PartnerRedirectionMethod, PartnershipTransaction) -> Unit,
)
