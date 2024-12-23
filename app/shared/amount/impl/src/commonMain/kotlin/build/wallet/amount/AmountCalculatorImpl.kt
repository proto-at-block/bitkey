package build.wallet.amount

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class AmountCalculatorImpl(
  private val decimalNumberCalculator: DecimalNumberCalculator,
  private val wholeNumberCalculator: WholeNumberCalculator,
) : AmountCalculator {
  override fun delete(amount: Amount): Amount {
    return when (amount) {
      is Amount.DecimalNumber -> {
        decimalNumberCalculator.delete(amount)
      }
      is Amount.WholeNumber -> {
        wholeNumberCalculator.delete(amount)
      }
    }
  }

  override fun add(
    amount: Amount,
    digit: Int,
  ): Amount {
    return when (amount) {
      is Amount.DecimalNumber -> {
        decimalNumberCalculator.append(amount, digit)
      }
      is Amount.WholeNumber -> {
        wholeNumberCalculator.append(amount, digit)
      }
    }
  }

  override fun decimal(amount: Amount): Amount {
    return when (amount) {
      is Amount.DecimalNumber -> decimalNumberCalculator.decimal(amount)
      is Amount.WholeNumber -> amount // no decimals to deal with!
    }
  }
}
