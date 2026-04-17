package build.wallet.ui.app.core.form

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput.ResendCodeContent.Text
import build.wallet.statemachine.core.input.VerificationCodeInputBodyModel

@Preview
@Composable
internal fun PreviewPhoneVerificationCodeInputFormScreen() {
  FormScreen(
    model =
      VerificationCodeInputBodyModel(
        title = "Verify your touchpoint",
        subtitle = "We sent a code to you",
        resendCodeContent = Text(value = "Resend code in 00:15"),
        onValueChange = {},
        onBack = {},
        id = null,
        explainerText = null
      ).body as FormBodyModel
  )
}

@Preview
@Composable
internal fun PreviewEmailVerificationCodeInputFormScreen() {
  FormScreen(
    model =
      VerificationCodeInputBodyModel(
        title = "Verify your touchpoint",
        subtitle = "We sent a code to you",
        resendCodeContent = Text(value = "Resend code in 00:15"),
        onValueChange = {},
        onBack = {},
        id = null,
        explainerText = "If the code doesn't arrive, please check your spam folder."
      ).body as FormBodyModel
  )
}
