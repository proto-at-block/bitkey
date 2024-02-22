package build.wallet.ui.app.account

import build.wallet.analytics.events.screen.context.PairHardwareEventTrackerScreenIdContext
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.account.create.full.hardware.ActivationInstructionsBodyModel
import build.wallet.ui.app.account.create.hardware.PairNewHardwareScreen
import io.kotest.core.spec.style.FunSpec

class ActivationInstructionsFormScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("activation instructions screen") {
    paparazzi.snapshot {
      PairNewHardwareScreen(
        model =
          ActivationInstructionsBodyModel(
            onBack = {},
            onContinue = {},
            isNavigatingBack = false,
            eventTrackerScreenIdContext = PairHardwareEventTrackerScreenIdContext.ACCOUNT_CREATION
          )
      )
    }
  }
})
