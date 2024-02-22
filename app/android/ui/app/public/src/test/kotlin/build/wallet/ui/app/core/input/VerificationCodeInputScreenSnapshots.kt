package build.wallet.ui.app.core.input

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput.ResendCodeContent.Button
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput.ResendCodeContent.Text
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput.SkipForNowContent.Hidden
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput.SkipForNowContent.Showing
import build.wallet.statemachine.core.input.VerificationCodeInputBodyModel
import build.wallet.ui.app.core.form.FormScreen
import com.airbnb.lottie.LottieTask
import io.kotest.core.spec.style.FunSpec
import java.util.concurrent.Executor

class VerificationCodeInputScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("verification input screen empty") {
    paparazzi.snapshot {
      FormScreen(
        model =
          VerificationCodeInputBodyModel(
            title = "Verify some touchpoint",
            subtitle = "We sent a code to you",
            resendCodeContent = Text("Resend code in 00:25"),
            skipForNowContent =
              Showing(
                text = "Can’t receive the code?",
                onSkipForNow = {}
              ),
            onValueChange = {},
            onBack = {},
            explainerText = null,
            id = null
          ).body as FormBodyModel
      )
    }
  }

  test("verification input screen with text") {
    paparazzi.snapshot {
      FormScreen(
        model =
          VerificationCodeInputBodyModel(
            title = "Verify some touchpoint",
            subtitle = "We sent a code to you",
            value = "12345",
            resendCodeContent = Button(onSendCodeAgain = {}, isLoading = false),
            skipForNowContent = Hidden,
            onValueChange = {},
            onBack = {},
            explainerText = null,
            id = null
          ).body as FormBodyModel
      )
    }
  }

  test("verification input screen with loading resend button") {
    // Needed for snapshotting the loading lottie animation
    LottieTask.EXECUTOR = Executor(Runnable::run)
    paparazzi.snapshot {
      FormScreen(
        model =
          VerificationCodeInputBodyModel(
            title = "Verify some touchpoint",
            subtitle = "We sent a code to you",
            value = "12345",
            resendCodeContent = Button(onSendCodeAgain = {}, isLoading = true),
            skipForNowContent = Hidden,
            onValueChange = {},
            onBack = {},
            explainerText = null,
            id = null
          ).body as FormBodyModel
      )
    }
  }

  test("verification input screen empty with email explainer") {
    paparazzi.snapshot {
      FormScreen(
        model =
          VerificationCodeInputBodyModel(
            title = "Verify some touchpoint",
            subtitle = "We sent a code to you",
            resendCodeContent = Text("Resend code in 00:25"),
            skipForNowContent =
              Showing(
                text = "Can’t receive the code?",
                onSkipForNow = {}
              ),
            onValueChange = {},
            onBack = {},
            explainerText = "If the code doesn’t arrive, please check your spam folder.",
            id = null
          ).body as FormBodyModel
      )
    }
  }
})
