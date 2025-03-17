package build.wallet.statemachine.settings.full.mobilepay

import build.wallet.limit.SpendingLimit
import build.wallet.money.FiatMoney
import build.wallet.statemachine.core.StateMachine

interface SpendingLimitCardUiStateMachine : StateMachine<SpendingLimitCardUiProps, SpendingLimitCardModel>

data class SpendingLimitCardUiProps(
  val spendingLimit: SpendingLimit,
  val remainingAmount: FiatMoney,
)
