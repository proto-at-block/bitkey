package build.wallet.ui.app.core.form

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput.ResendCodeContent.Button
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput.ResendCodeContent.Text
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.forms.TextField
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment.Secondary
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.button.ButtonModel.Treatment.Tertiary
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.tokens.LabelType

@Composable
fun VerificationCodeInput(model: VerificationCodeInput) {
  val isDesignSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current

  Column {
    if (isDesignSystemV2Enabled) {
      SegmentedVerificationCodeInput(
        modifier = Modifier.fillMaxWidth(),
        model = model.fieldModel,
        expectedCodeLength = model.expectedCodeLength,
        testTag = "verification-code-input-field"
      )
    } else {
      TextField(
        modifier = Modifier.fillMaxWidth(),
        model = model.fieldModel,
        testTag = "verification-code-input-field"
      )
    }
    Spacer(modifier = Modifier.height(24.dp))
    when (val resendCodeContent = model.resendCodeContent) {
      is Text ->
        Label(
          text = resendCodeContent.value,
          type = if (isDesignSystemV2Enabled) LabelType.Body3Mono else LabelType.Body3Regular,
          treatment = Secondary
        )
      is Button ->
        Button(
          model =
            ButtonModel(
              text = resendCodeContent.value.text,
              treatment = Tertiary,
              isLoading = resendCodeContent.value.isLoading,
              size = Compact,
              onClick = resendCodeContent.value.onClick
            )
        )
    }
  }
}
