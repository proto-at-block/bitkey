package build.wallet.ui.app.partnerships

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue.Expanded
import androidx.compose.ui.Modifier
import build.wallet.compose.collections.immutableListOf
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.Money
import build.wallet.money.display.BitcoinDisplayUnit
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.partnerships.purchase.selectPurchaseAmountModel
import build.wallet.ui.components.sheet.Sheet
import build.wallet.ui.theme.WalletTheme
import io.kotest.core.spec.style.FunSpec

class SelectPurchaseAmountSheetSnapshots : FunSpec({
  val paparazzi = paparazziExtension(maxPercentDifference = 0.1)

  test("select purchase amount sheet - design system v2") {
    paparazzi.snapshot {
      Column(
        Modifier
          .fillMaxSize()
          .background(WalletTheme.colors.background)
      ) {
        Sheet(
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
              moneyDisplayFormatter = SnapshotMoneyDisplayFormatter,
              onSelectAmount = {},
              onSelectCustomAmount = {},
              onNext = {},
              onExit = {}
            ),
          sheetState =
            SheetState(
              skipPartiallyExpanded = true,
              positionalThreshold = { 0.0f },
              velocityThreshold = { 0.0f },
              initialValue = Expanded
            )
        )
      }
    }
  }
})

private object SnapshotMoneyDisplayFormatter : MoneyDisplayFormatter {
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
