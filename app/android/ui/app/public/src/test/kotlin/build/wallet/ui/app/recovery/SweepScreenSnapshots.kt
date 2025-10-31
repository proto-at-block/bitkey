package build.wallet.ui.app.recovery

import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.money.amount.MoneyAmountModel
import build.wallet.statemachine.recovery.sweep.SweepFundsPromptBodyModel
import build.wallet.statemachine.recovery.sweep.SweepFundsPromptContext
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class SweepScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  val defaultMoneyAmount = MoneyAmountModel(
    primaryAmount = "10,000 sats",
    secondaryAmount = "$100.00"
  )

  fun sweepFundsPromptBodyModel(
    sweepContext: SweepFundsPromptContext,
    onBack: (() -> Unit)? = {},
  ) = SweepFundsPromptBodyModel(
    id = null,
    sweepContext = sweepContext,
    transferAmount = defaultMoneyAmount,
    fee = defaultMoneyAmount,
    onShowNetworkFeesInfo = {},
    onBack = onBack,
    onHelpClick = {},
    onSubmit = {}
  )

  test("sweep screen - inactive wallet context") {
    paparazzi.snapshot {
      FormScreen(
        model = sweepFundsPromptBodyModel(SweepFundsPromptContext.InactiveWallet)
      )
    }
  }

  test("sweep screen - recovery context") {
    paparazzi.snapshot {
      FormScreen(
        model = sweepFundsPromptBodyModel(
          SweepFundsPromptContext.Recovery(PhysicalFactor.App)
        )
      )
    }
  }
})
