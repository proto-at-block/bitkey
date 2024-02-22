package build.wallet.ui.components.form

import app.cash.paparazzi.DeviceConfig
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.ui.components.forms.TextFieldNoTextAndNoFocusPreview
import build.wallet.ui.components.forms.TextFieldWithTextAndNoFocusPreview
import io.kotest.core.spec.style.FunSpec

// TODO(W-881) : add snapshots with focused cursor.
class TextFieldSnapshots : FunSpec({
  val paparazzi = paparazziExtension(DeviceConfig.PIXEL_6)

  test("no text and no focus") {
    paparazzi.snapshot {
      TextFieldNoTextAndNoFocusPreview()
    }
  }

  test("with text and no focus") {
    paparazzi.snapshot {
      TextFieldWithTextAndNoFocusPreview()
    }
  }
})
