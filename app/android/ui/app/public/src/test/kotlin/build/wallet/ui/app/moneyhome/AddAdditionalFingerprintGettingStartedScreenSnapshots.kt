package build.wallet.ui.app.moneyhome

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.settings.full.device.fingerprints.AddAdditionalFingerprintGettingStartedModel
import build.wallet.ui.app.paparazzi.snapshotSheet
import io.kotest.core.spec.style.FunSpec

class AddAdditionalFingerprintGettingStartedScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("add additional fingerprint getting started screen") {
    paparazzi.snapshotSheet(
      AddAdditionalFingerprintGettingStartedModel(
        onClosed = {},
        onContinue = {},
        onSetUpLater = {}
      )
    )
  }
})
