package build.wallet.statemachine.money.amount

import build.wallet.money.Money

internal fun Money.toAnimatedAmountValue(): Long {
  return rounded().fractionalUnitValue.longValue()
}

internal fun Money.toAnimatedAmountAnimationKey(): Long {
  return currency.textCode.code.hashCode().toLong()
}
