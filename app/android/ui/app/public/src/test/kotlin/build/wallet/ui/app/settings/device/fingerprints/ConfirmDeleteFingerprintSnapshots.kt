package build.wallet.ui.app.settings.device.fingerprints

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.settings.full.device.fingerprints.ConfirmDeleteFingerprintBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class ConfirmDeleteFingerprintSnapshots : FunSpec({

  val paparazzi = paparazziExtension()

  test("deleting fingerprint confirmation") {
    paparazzi.snapshot {
      FormScreen(
        model =
          ConfirmDeleteFingerprintBodyModel(
            onDelete = {},
            onCancel = {}
          )
      )
    }
  }
})
