package build.wallet.ui.app.account

import build.wallet.analytics.events.screen.context.PairHardwareEventTrackerScreenIdContext
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.account.create.full.hardware.FingerprintEnrollmentHelpBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class FingerprintEnrollmentHelpScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("fingerprint enrollment help screen") {
    paparazzi.snapshot {
      FormScreen(
        model = FingerprintEnrollmentHelpBodyModel(
          onBack = {},
          eventTrackerContext = PairHardwareEventTrackerScreenIdContext.ACCOUNT_CREATION
        )
      )
    }
  }

})
