package build.wallet.ui.components.form

import androidx.compose.ui.text.input.TextFieldValue
import app.cash.paparazzi.DeviceConfig
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.ui.components.forms.TextField
import io.kotest.core.spec.style.FunSpec

class TextFieldDesignSystemV2Snapshots : FunSpec({
  val paparazzi = paparazziExtension(DeviceConfig.PIXEL_6)

  test("no text and no focus") {
    paparazzi.snapshot {
      TextField(
        placeholderText = "Email Address",
        value = TextFieldValue(""),
        onValueChange = {}
      )
    }
  }
})
