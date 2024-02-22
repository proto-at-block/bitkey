package build.wallet.ui.app.core.input

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.input.EmailInputScreenModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import io.kotest.core.spec.style.FunSpec

class EmailInputScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("email input without test") {
    paparazzi.snapshot {
      FormScreen(
        model =
          EmailInputScreenModel(
            title = "Enter your email address",
            primaryButton =
              ButtonModel(
                text = "Continue",
                treatment = Primary,
                size = Footer,
                onClick = Click.StandardClick { }
              ),
            onValueChange = {},
            onClose = {},
            onSkip = {}
          ).body as FormBodyModel
      )
    }
  }

  test("email input with email") {
    paparazzi.snapshot {
      FormScreen(
        model =
          EmailInputScreenModel(
            title = "Enter your email address",
            value = "llcoolj@defjam.com",
            primaryButton =
              ButtonModel(
                text = "Continue",
                treatment = Primary,
                size = Footer,
                onClick = Click.StandardClick { }
              ),
            onValueChange = {},
            onClose = {},
            onSkip = {}
          ).body as FormBodyModel
      )
    }
  }
})
