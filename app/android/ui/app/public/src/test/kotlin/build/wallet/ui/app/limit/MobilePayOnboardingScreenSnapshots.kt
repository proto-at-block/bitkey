package build.wallet.ui.app.limit

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.limit.MobilePayOnboardingScreenModel
import build.wallet.statemachine.limit.SpendingLimitsCopy
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class MobilePayOnboardingScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Mobile pay onboarding sheet model screen") {
    paparazzi.snapshot {
      FormScreen(
        MobilePayOnboardingScreenModel(
          onContinue = {},
          onSetUpLater = {},
          onClosed = {},
          headerHeadline = SpendingLimitsCopy.get(false).onboardingModal.headline,
          headerSubline = SpendingLimitsCopy.get(false).onboardingModal.subline,
          primaryButtonString = SpendingLimitsCopy.get(false).onboardingModal.primaryButtonString
        )
      )
    }
  }

  test("Mobile pay onboarding sheet model screen - revamp on") {
    paparazzi.snapshot {
      FormScreen(
        MobilePayOnboardingScreenModel(
          onContinue = {},
          onSetUpLater = {},
          onClosed = {},
          headerHeadline = SpendingLimitsCopy.get(true).onboardingModal.headline,
          headerSubline = SpendingLimitsCopy.get(true).onboardingModal.subline,
          primaryButtonString = SpendingLimitsCopy.get(true).onboardingModal.primaryButtonString
        )
      )
    }
  }
})
