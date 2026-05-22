package build.wallet.ui.app.core.form

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput.ResendCodeContent.Text
import build.wallet.statemachine.core.input.VerificationCodeInputBodyModel
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tooling.PreviewWalletTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

@Preview
@Composable
internal fun PreviewPhoneVerificationCodeInputFormScreen() {
  PreviewWalletTheme {
    FormScreen(
      model =
        VerificationCodeInputBodyModel(
          title = "Verify your touchpoint",
          subtitle = "We sent a code to you",
          expectedCodeLength = 6,
          resendCodeContent = Text(value = "Resend code in 00:15"),
          onValueChange = {},
          onBack = {},
          id = null,
          explainerText = null
        ).body as FormBodyModel
    )
  }
}

@Preview
@Composable
internal fun PreviewEmailVerificationCodeInputFormScreen() {
  PreviewWalletTheme {
    FormScreen(
      model =
        VerificationCodeInputBodyModel(
          title = "Verify your touchpoint",
          subtitle = "We sent a code to you",
          expectedCodeLength = 6,
          resendCodeContent = Text(value = "Resend code in 00:15"),
          onValueChange = {},
          onBack = {},
          id = null,
          explainerText = "If the code doesn't arrive, please check your spam folder."
        ).body as FormBodyModel
    )
  }
}

@Preview(name = "Verification Code Input (Design System V2)")
@Composable
internal fun PreviewVerificationCodeInputFormScreenDesignSystemV2() {
  PreviewWalletTheme {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(WalletTheme.colors.background)
    ) {
      FormScreen(
        model =
          VerificationCodeInputBodyModel(
            title = "Verify your touchpoint",
            subtitle = "We sent a code to you",
            value = "123",
            expectedCodeLength = 6,
            resendCodeContent = Text(value = "Resend code in 00:15"),
            onValueChange = {},
            onBack = {},
            id = null,
            explainerText = null
          ).body as FormBodyModel
      )
    }
  }
}
