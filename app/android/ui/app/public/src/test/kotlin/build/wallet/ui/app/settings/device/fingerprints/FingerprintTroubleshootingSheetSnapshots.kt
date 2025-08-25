package build.wallet.ui.app.settings.device.fingerprints

import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.settings.full.device.fingerprints.FingerprintTroubleshootingSheetModel
import build.wallet.ui.app.paparazzi.snapshotSheet
import io.kotest.core.spec.style.FunSpec

class FingerprintTroubleshootingSheetSnapshots : FunSpec({

  val paparazzi = paparazziExtension()

  test("fingerprint troubleshooting sheet") {
    paparazzi.snapshotSheet(
      FingerprintTroubleshootingSheetModel(
        onContinue = {},
        onClosed = {},
        eventTrackerContext = NfcEventTrackerScreenIdContext.ENROLLING_NEW_FINGERPRINT
      )
    )
  }
})
