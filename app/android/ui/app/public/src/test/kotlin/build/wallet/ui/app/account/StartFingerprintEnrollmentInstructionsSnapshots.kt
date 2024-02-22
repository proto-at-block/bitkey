package build.wallet.ui.app.account

import build.wallet.analytics.events.screen.context.PairHardwareEventTrackerScreenIdContext
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.account.create.full.hardware.StartFingerprintEnrollmentInstructionsBodyModel
import build.wallet.ui.app.account.create.hardware.PairNewHardwareScreen
import io.kotest.core.spec.style.FunSpec

class StartFingerprintEnrollmentInstructionsSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("pairing instructions screen") {
    paparazzi.snapshot {
      PairNewHardwareScreen(
        model =
          StartFingerprintEnrollmentInstructionsBodyModel(
            onBack = {},
            onButtonClick = {},
            isNavigatingBack = false,
            eventTrackerScreenIdContext = PairHardwareEventTrackerScreenIdContext.ACCOUNT_CREATION
          )
      )
    }
  }
})
