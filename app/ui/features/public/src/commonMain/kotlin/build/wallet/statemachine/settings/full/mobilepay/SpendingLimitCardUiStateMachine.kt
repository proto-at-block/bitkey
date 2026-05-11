package build.wallet.statemachine.settings.full.mobilepay

import build.wallet.limit.SpendingLimit
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.statemachine.core.StateMachine

interface SpendingLimitCardUiStateMachine : StateMachine<SpendingLimitCardUiProps, SpendingLimitCardModel>

data class SpendingLimitCardUiProps(
  val spendingLimit: SpendingLimit,
  val spentAmount: FiatMoney,
  val remainingAmount: FiatMoney,
  val spentBitcoinAmount: BitcoinMoney,
  val remainingBitcoinAmount: BitcoinMoney,
)
