package build.wallet.statemachine.data.mobilepay

import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitkey.account.FullAccount
import build.wallet.money.currency.FiatCurrency
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.transactions.FullAccountTransactionsData.FullAccountTransactionsLoadedData

interface MobilePayDataStateMachine : StateMachine<MobilePayProps, MobilePayData>

data class MobilePayProps(
  val account: FullAccount,
  val spendingWallet: SpendingWallet,
  val transactionsData: FullAccountTransactionsLoadedData,
  val fiatCurrency: FiatCurrency,
)
