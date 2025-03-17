package build.wallet.money.input

import build.wallet.amount.Amount
import build.wallet.money.currency.Currency

class MoneyInputFormatterMock : MoneyInputFormatter {
  override fun displayText(
    inputAmount: Amount,
    inputAmountCurrency: Currency,
  ): MoneyInputDisplayText {
    return MoneyInputDisplayText(
      displayText = "MoneyInputFormatter.displayText"
    )
  }
}
