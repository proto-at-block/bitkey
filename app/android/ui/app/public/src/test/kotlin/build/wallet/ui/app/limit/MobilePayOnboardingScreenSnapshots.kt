package build.wallet.ui.app.limit

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.limit.MobilePayOnboardingScreenModel
import build.wallet.ui.app.paparazzi.snapshotSheet
import io.kotest.core.spec.style.FunSpec

class MobilePayOnboardingScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Mobile pay onboarding sheet model screen") {
    paparazzi.snapshotSheet(
      MobilePayOnboardingScreenModel(
        onContinue = {},
        onSetUpLater = {},
        onClosed = {},
        headerHeadline = "Transfer without hardware",
        headerSubline = "Spend up to a set daily limit without your Bitkey device.",
        primaryButtonString = "Got it"
      )
    )
  }
})
