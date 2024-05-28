package build.wallet.ui.app.settings.device.fingerprints

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.settings.full.device.fingerprints.EditingFingerprintBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class EditingFingerprintScreenSnapshots : FunSpec({

  val paparazzi = paparazziExtension()

  test("adding new fingerprint") {
    paparazzi.snapshot {
      FormScreen(
        model =
          EditingFingerprintBodyModel(
            index = 1,
            label = "",
            textFieldValue = "",
            onDelete = {},
            onSave = {},
            onValueChange = {},
            onBackPressed = {},
            isExistingFingerprint = false,
            attemptToDeleteLastFingerprint = false
          )
      )
    }
  }

  test("editing existing fingerprint") {
    paparazzi.snapshot {
      FormScreen(
        model =
          EditingFingerprintBodyModel(
            index = 0,
            label = "Left thumb",
            textFieldValue = "Right thumb",
            onDelete = {},
            onSave = {},
            onValueChange = {},
            onBackPressed = {},
            isExistingFingerprint = true,
            attemptToDeleteLastFingerprint = false
          )
      )
    }
  }

  test("editing existing fingerprint save disabled") {
    paparazzi.snapshot {
      FormScreen(
        model =
          EditingFingerprintBodyModel(
            index = 0,
            label = "Left thumb",
            textFieldValue = "Left thumb",
            onDelete = {},
            onSave = {},
            onValueChange = {},
            onBackPressed = {},
            isExistingFingerprint = true,
            attemptToDeleteLastFingerprint = false
          )
      )
    }
  }

  test("trying to delete last fingerprint") {
    paparazzi.snapshot {
      FormScreen(
        model =
          EditingFingerprintBodyModel(
            index = 0,
            label = "Left thumb",
            textFieldValue = "Left thumb",
            onDelete = {},
            onSave = {},
            onValueChange = {},
            onBackPressed = {},
            isExistingFingerprint = true,
            attemptToDeleteLastFingerprint = true
          )
      )
    }
  }
})
