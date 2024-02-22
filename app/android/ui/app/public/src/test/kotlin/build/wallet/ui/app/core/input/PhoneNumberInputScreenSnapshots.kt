package build.wallet.ui.app.core.input

import androidx.compose.runtime.Composable
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.input.PhoneNumberInputScreenModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import io.kotest.core.spec.style.FunSpec

class PhoneNumberInputScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("phone number input screen empty") {
    paparazzi.snapshot {
      PhoneNumberInputPreview()
    }
  }

  test("phone number input screen with text") {
    paparazzi.snapshot {
      PhoneNumberInputPreview(
        value = "+1 (555) 555-5555"
      )
    }
  }
})

@Composable
private fun PhoneNumberInputPreview(value: String = "") =
  FormScreen(
    model =
      PhoneNumberInputScreenModel(
        title = "Enter your phone number",
        textFieldValue = value,
        textFieldPlaceholder = "+1 (555) 555-5555",
        textFieldSelection = 0..0,
        onTextFieldValueChange = { _, _ -> },
        primaryButton =
          ButtonModel(
            text = "Continue",
            treatment = Primary,
            size = Footer,
            onClick = Click.StandardClick { }
          ),
        onClose = {},
        onSkip = {}
      ).body as FormBodyModel
  )
