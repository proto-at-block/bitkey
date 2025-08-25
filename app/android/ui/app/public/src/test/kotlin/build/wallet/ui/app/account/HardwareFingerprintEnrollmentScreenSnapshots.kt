package build.wallet.ui.app.account

import androidx.compose.runtime.Composable
import build.wallet.analytics.events.screen.context.PairHardwareEventTrackerScreenIdContext
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.account.create.full.hardware.HardwareFingerprintEnrollmentScreenModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.ui.app.account.create.hardware.PairNewHardwareScreen
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import io.kotest.core.spec.style.FunSpec

class HardwareFingerprintEnrollmentScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("hardware fingerprint enrollment instructions screen") {
    paparazzi.snapshot {
      FingerprintEnrollmentScreen()
    }
  }

  test("hardware fingerprint enrollment instructions screen - with troubleshooting button") {
    paparazzi.snapshot {
      FingerprintEnrollmentScreen(
        headline = "Set up your fingerprint",
        troubleshootingButton = ButtonModel(
          text = "Having trouble?",
          treatment = ButtonModel.Treatment.TertiaryNoUnderlineWhite,
          onClick = StandardClick {},
          size = ButtonModel.Size.Footer
        )
      )
    }
  }
})

@Composable
private fun FingerprintEnrollmentScreen(
  headline: String = "Set up your first fingerprint",
  troubleshootingButton: ButtonModel? = null,
) {
  val model = HardwareFingerprintEnrollmentScreenModel(
    showingIncompleteEnrollmentError = false,
    incompleteEnrollmentErrorOnPrimaryButtonClick = {},
    onBack = {},
    onSaveFingerprint = {},
    onErrorOverlayClosed = {},
    troubleshootingButton = troubleshootingButton,
    isNavigatingBack = false,
    eventTrackerContext = PairHardwareEventTrackerScreenIdContext.ACCOUNT_CREATION,
    presentationStyle = ScreenPresentationStyle.Root,
    headline = headline,
    instructions = "Place your finger on the sensor until you see a blue light. Lift your" +
      " finger and repeat (15-20 times) adjusting your finger position slightly each time," +
      " until the light turns green. Then save your fingerprint."
  )

  PairNewHardwareScreen(
    model =
      (model.body as PairNewHardwareBodyModel)
        // Workaround for KeepScreenOn needing an Activity instead of BridgeContext.
        .copy(keepScreenOn = false)
  )
}
