package build.wallet.ui.app.settings.device.fingerprints

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.settings.full.device.fingerprints.PromptingForFingerprintFwUpSheetModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class PromptingForFingerprintFwUpSnapshots : FunSpec({

  val paparazzi = paparazziExtension()

  test("prompting for fingerprint fwup") {
    paparazzi.snapshot {
      FormScreen(
        PromptingForFingerprintFwUpSheetModel(
          onCancel = {},
          onUpdate = {}
        ).body as FormBodyModel
      )
    }
  }
})
