@file:Suppress("TestFunctionName")

package build.wallet.ui.app.nfc

import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.nfc.HardwareConfirmationResultBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class HardwareConfirmationResultScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("confirmation result screen") {
    paparazzi.snapshot {
      FormScreen(
        model = HardwareConfirmationResultBodyModel(
          headline = "Review transaction on Bitkey",
          subline = "Before sending, use your Bitkey device to review the transaction details.",
          buttonText = "Got it",
          onAcknowledge = {},
          eventTrackerScreenId = NfcEventTrackerScreenId.NFC_CONFIRMATION_PENDING
        )
      )
    }
  }
})
