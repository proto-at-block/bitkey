package build.wallet.ui.app.account

import build.wallet.analytics.events.screen.context.PairHardwareEventTrackerScreenIdContext
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.account.create.full.hardware.HardwareFingerprintEnrollmentScreenModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.ui.app.account.create.hardware.PairNewHardwareScreen
import io.kotest.core.spec.style.FunSpec

class HardwareFingerprintEnrollmentScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("hardware fingerprint enrollment instructions screen") {
    paparazzi.snapshot {
      val model =
        HardwareFingerprintEnrollmentScreenModel(
          showingIncompleteEnrollmentError = false,
          incompleteEnrollmentErrorOnPrimaryButtonClick = {},
          onBack = {},
          onSaveFingerprint = {},
          onErrorOverlayClosed = {},
          isNavigatingBack = false,
          eventTrackerScreenIdContext = PairHardwareEventTrackerScreenIdContext.ACCOUNT_CREATION,
          presentationStyle = ScreenPresentationStyle.Root,
          headline = "Set up your fingerprint",
          instructions = "Place your finger on the sensor until you see a blue light." +
            " Repeat this until the device has a solid green light." +
            " Once done, press the button below to save your fingerprint."
        )
      PairNewHardwareScreen(
        model =
          (model.body as PairNewHardwareBodyModel)
            // Workaround for KeepScreenOn needing an Activity instead of BridgeContext.
            .copy(keepScreenOn = false)
      )
    }
  }
})
