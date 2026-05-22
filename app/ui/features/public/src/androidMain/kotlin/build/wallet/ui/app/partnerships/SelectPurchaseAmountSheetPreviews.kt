package build.wallet.ui.app.partnerships

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.compose.collections.immutableListOf
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.Money
import build.wallet.money.display.BitcoinDisplayUnit
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.partnerships.purchase.selectPurchaseAmountModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.tokens.lightStyleDictionaryColors
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun SelectPurchaseAmountSheetDesignSystemV2Preview() {
  PreviewWalletTheme(
    modifier = Modifier.fillMaxSize(),
    backgroundColor = lightStyleDictionaryColors.subtleBackground
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.Bottom
    ) {
      FormScreen(
        model =
          selectPurchaseAmountModel(
            purchaseAmounts =
              immutableListOf(
                FiatMoney.usd(10.0),
                FiatMoney.usd(25.0),
                FiatMoney.usd(50.0),
                FiatMoney.usd(100.0),
                FiatMoney.usd(200.0)
              ),
            selectedAmount = FiatMoney.usd(100.0),
            isDesignSystemV2Enabled = true,
            moneyDisplayFormatter = PreviewMoneyDisplayFormatter,
            onSelectAmount = {},
            onSelectCustomAmount = {},
            onNext = {},
            onExit = {}
          ).body as FormBodyModel
      )
    }
  }
}

private object PreviewMoneyDisplayFormatter : MoneyDisplayFormatter {
  override fun format(amount: Money): String =
    when (amount) {
      is FiatMoney -> "$${amount.value.toPlainString()}"
      is BitcoinMoney -> amount.value.toString()
    }

  override fun formatCompact(amount: FiatMoney): String =
    "$${amount.value.toPlainString().removeSuffix(".0")}"

  override fun formatWithUnit(
    amount: BitcoinMoney,
    unit: BitcoinDisplayUnit,
  ): String = amount.value.toString()
}
