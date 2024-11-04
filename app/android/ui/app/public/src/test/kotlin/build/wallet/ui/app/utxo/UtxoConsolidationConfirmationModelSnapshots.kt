package build.wallet.ui.app.utxo

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.money.formatter.AmountDisplayText
import build.wallet.statemachine.utxo.UtxoConsolidationConfirmationModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class UtxoConsolidationConfirmationModelSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("confirmation screen - without unconfirmed transactions callout") {
    paparazzi.snapshot {
      FormScreen(
        UtxoConsolidationConfirmationModel(
          balanceTitle = "Wallet balance",
          balanceAmountDisplayText = AmountDisplayText(
            primaryAmountText = "$15,000",
            secondaryAmountText = "26,259,461 sats"
          ),
          utxoCount = "20",
          consolidationCostDisplayText = AmountDisplayText(
            primaryAmountText = "$37.42",
            secondaryAmountText = "65,000 sats"
          ),
          estimatedConsolidationTime = "~24 hours",
          showUnconfirmedTransactionsCallout = false,
          onBack = {},
          onContinue = {},
          onConsolidationTimeClick = {},
          onConsolidationCostClick = {}
        )
      )
    }
  }

  test("confirmation screen - with unconfirmed transactions callout") {
    paparazzi.snapshot {
      FormScreen(
        UtxoConsolidationConfirmationModel(
          balanceTitle = "Wallet balance",
          balanceAmountDisplayText = AmountDisplayText(
            primaryAmountText = "$15,000",
            secondaryAmountText = "26,259,461 sats"
          ),
          utxoCount = "20",
          consolidationCostDisplayText = AmountDisplayText(
            primaryAmountText = "$37.42",
            secondaryAmountText = "65,000 sats"
          ),
          estimatedConsolidationTime = "~24 hours",
          showUnconfirmedTransactionsCallout = true,
          onBack = {},
          onContinue = {},
          onConsolidationTimeClick = {},
          onConsolidationCostClick = {}
        )
      )
    }
  }
})
