package build.wallet.ui.app.fwup

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.fwup.FwupNfcBodyModel
import build.wallet.ui.app.nfc.FwupNfcScreenInternal
import io.kotest.core.spec.style.FunSpec

class FwupNfcSnapshots : FunSpec({
  val paparazzi = paparazziExtension(maxPercentDifference = 0.8)

  test("fwup nfc progress with zero progress") {
    paparazzi.snapshot {
      FwupNfcScreenInternal(
        model =
          FwupNfcBodyModel(
            onCancel = {},
            status =
              FwupNfcBodyModel.Status.InProgress(fwupProgress = 0f),
            eventTrackerScreenInfo = null
          )
      )
    }
  }

  test("fwup nfc progress with some progress") {
    paparazzi.snapshot {
      FwupNfcScreenInternal(
        model =
          FwupNfcBodyModel(
            onCancel = {},
            status =
              FwupNfcBodyModel.Status.InProgress(fwupProgress = 33f),
            eventTrackerScreenInfo = null
          )
      )
    }
  }

  test("fwup nfc lost connection") {
    paparazzi.snapshot {
      FwupNfcScreenInternal(
        model =
          FwupNfcBodyModel(
            onCancel = {},
            status =
              FwupNfcBodyModel.Status.LostConnection(fwupProgress = 5f),
            eventTrackerScreenInfo = null
          )
      )
    }
  }

  test("fwup nfc success") {
    paparazzi.snapshot {
      FwupNfcScreenInternal(
        model =
          FwupNfcBodyModel(
            onCancel = null,
            status = FwupNfcBodyModel.Status.Success(),
            eventTrackerScreenInfo = null
          )
      )
    }
  }
})
