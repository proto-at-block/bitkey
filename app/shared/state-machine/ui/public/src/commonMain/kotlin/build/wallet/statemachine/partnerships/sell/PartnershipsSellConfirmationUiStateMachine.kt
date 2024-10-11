package build.wallet.statemachine.partnerships.sell

import build.wallet.bitkey.account.FullAccount
import build.wallet.money.exchange.ExchangeRate
import build.wallet.partnerships.PartnerInfo
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import kotlinx.collections.immutable.ImmutableList

interface PartnershipsSellConfirmationUiStateMachine : StateMachine<PartnershipsSellConfirmationProps, ScreenModel>

data class PartnershipsSellConfirmationProps(
  val account: FullAccount,
  val confirmedPartnerSale: ConfirmedPartnerSale,
  val exchangeRates: ImmutableList<ExchangeRate>?,
  val onBack: () -> Unit,
  val onDone: (PartnerInfo?) -> Unit,
)
