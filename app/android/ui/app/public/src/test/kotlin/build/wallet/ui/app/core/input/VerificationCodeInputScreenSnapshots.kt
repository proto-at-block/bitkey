package build.wallet.ui.app.core.input

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput.ResendCodeContent.Button
import build.wallet.statemachine.core.input.VerificationCodeInputBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class VerificationCodeInputScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("verification input screen with text") {
    paparazzi.snapshot {
      FormScreen(
        model =
          VerificationCodeInputBodyModel(
            title = "Verify some touchpoint",
            subtitle = "We sent a code to you",
            value = "12345",
            resendCodeContent = Button(onSendCodeAgain = {}, isLoading = false),
            onValueChange = {},
            onBack = {},
            explainerText = null,
            id = null
          ).body as FormBodyModel
      )
    }
  }

  test("verification input screen with loading resend button") {
    paparazzi.snapshot {
      FormScreen(
        model =
          VerificationCodeInputBodyModel(
            title = "Verify some touchpoint",
            subtitle = "We sent a code to you",
            value = "12345",
            resendCodeContent = Button(onSendCodeAgain = {}, isLoading = true),
            onValueChange = {},
            onBack = {},
            explainerText = null,
            id = null
          ).body as FormBodyModel
      )
    }
  }
})
