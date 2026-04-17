package build.wallet.ui.app.account

import build.wallet.analytics.events.screen.context.PairHardwareEventTrackerScreenIdContext
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.account.create.full.hardware.CompleteTwoTapBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class CompleteTwoTapScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("complete two tap screen") {
    paparazzi.snapshot {
      FormScreen(
        model =
          CompleteTwoTapBodyModel(
            onBack = {},
            onContinue = {},
            onHelpClick = {},
            eventTrackerContext = PairHardwareEventTrackerScreenIdContext.ACCOUNT_CREATION
          )
      )
    }
  }

  test("complete two tap screen - design system v2") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      FormScreen(
        model =
          CompleteTwoTapBodyModel(
            onBack = {},
            onContinue = {},
            onHelpClick = {},
            eventTrackerContext = PairHardwareEventTrackerScreenIdContext.ACCOUNT_CREATION,
            isHardwareFake = true
          )
      )
    }
  }
})
