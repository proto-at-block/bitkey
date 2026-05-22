package build.wallet.ui.app.core.input

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput.ResendCodeContent.Button
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput.ResendCodeContent.Text
import build.wallet.statemachine.core.input.VerificationCodeInputBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class VerificationCodeInputScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("verification input screen with resend countdown text") {
    paparazzi.snapshot {
      FormScreen(
        model =
          VerificationCodeInputBodyModel(
            title = "Verify some touchpoint",
            subtitle = "We sent a code to you",
            value = "12345",
            expectedCodeLength = 6,
            resendCodeContent = Text(value = "Resend code in 00:15"),
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
            expectedCodeLength = 6,
            resendCodeContent = Button(onSendCodeAgain = {}, isLoading = true),
            onValueChange = {},
            onBack = {},
            explainerText = null,
            id = null
          ).body as FormBodyModel
      )
    }
  }

  test("verification input screen with resend countdown text - design system v2") {
    paparazzi.snapshot {
      FormScreen(
        model =
          VerificationCodeInputBodyModel(
            title = "Verify some touchpoint",
            subtitle = "We sent a code to you",
            value = "123456",
            expectedCodeLength = 6,
            resendCodeContent = Text(value = "Resend code in 00:15"),
            onValueChange = {},
            onBack = {},
            explainerText = null,
            id = null
          ).body as FormBodyModel
      )
    }
  }

  test("verification input screen with resend button - design system v2") {
    paparazzi.snapshot {
      FormScreen(
        model =
          VerificationCodeInputBodyModel(
            title = "Verify some touchpoint",
            subtitle = "We sent a code to you",
            value = "123",
            expectedCodeLength = 6,
            resendCodeContent = Button(onSendCodeAgain = {}, isLoading = false),
            onValueChange = {},
            onBack = {},
            explainerText = null,
            id = null
          ).body as FormBodyModel
      )
    }
  }

  test("verification input screen with explainer - design system v2") {
    paparazzi.snapshot {
      FormScreen(
        model =
          VerificationCodeInputBodyModel(
            title = "Verify some touchpoint",
            subtitle = "We sent a code to you",
            value = "123",
            expectedCodeLength = 6,
            resendCodeContent = Text(value = "Resend code in 00:15"),
            onValueChange = {},
            onBack = {},
            explainerText = "If the code doesn't arrive, please check your spam folder.",
            id = null
          ).body as FormBodyModel
      )
    }
  }

  test("verification input screen with four digit code - design system v2") {
    paparazzi.snapshot {
      FormScreen(
        model =
          VerificationCodeInputBodyModel(
            title = "Verify some touchpoint",
            subtitle = "We sent a code to you",
            value = "1234",
            expectedCodeLength = 4,
            resendCodeContent = Text(value = "Resend code in 00:15"),
            onValueChange = {},
            onBack = {},
            explainerText = null,
            id = null
          ).body as FormBodyModel
      )
    }
  }
})
