package build.wallet.ui.app.account

import build.wallet.analytics.events.screen.context.PairHardwareEventTrackerScreenIdContext
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.account.create.full.hardware.ActivationInstructionsV2BodyModel
import build.wallet.ui.app.account.create.hardware.PairNewHardwareScreen
import io.kotest.core.spec.style.FunSpec

class ActivationInstructionsV2ScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("activation instructions v2 screen") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      PairNewHardwareScreen(
        model =
          ActivationInstructionsV2BodyModel(
            onBack = {},
            onContinue = {},
            isNavigatingBack = false,
            eventTrackerContext = PairHardwareEventTrackerScreenIdContext.ACCOUNT_CREATION
          )
      )
    }
  }

  test("activation instructions v2 screen - loading") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      PairNewHardwareScreen(
        model =
          ActivationInstructionsV2BodyModel(
            onBack = {},
            onContinue = null, // Loading state
            isNavigatingBack = false,
            eventTrackerContext = PairHardwareEventTrackerScreenIdContext.ACCOUNT_CREATION
          )
      )
    }
  }
})
