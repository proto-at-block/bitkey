package build.wallet.ui.app.core.form

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput.ResendCodeContent.Button
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput.ResendCodeContent.Text
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput.SkipForNowContent.Hidden
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput.SkipForNowContent.Showing
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.forms.TextField
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment.Secondary
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.button.ButtonModel.Treatment.Tertiary
import build.wallet.ui.tokens.LabelType

@Composable
fun VerificationCodeInput(model: VerificationCodeInput) {
  Column {
    TextField(
      modifier = Modifier.fillMaxWidth(),
      model = model.fieldModel
    )
    Spacer(modifier = Modifier.height(24.dp))
    when (val resendCodeContent = model.resendCodeContent) {
      is Text ->
        Label(
          text = resendCodeContent.value,
          type = LabelType.Body3Regular,
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
    when (val skipForNowContent = model.skipForNowContent) {
      is Hidden -> Unit
      is Showing -> {
        Spacer(modifier = Modifier.height(24.dp))
        Row {
          Label(
            modifier = Modifier.align(CenterVertically),
            text = skipForNowContent.text,
            type = LabelType.Body3Regular
          )
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            model =
              ButtonModel(
                text = skipForNowContent.button.text,
                treatment = Tertiary,
                size = Compact,
                onClick = skipForNowContent.button.onClick
              )
          )
        }
      }
    }
  }
}
