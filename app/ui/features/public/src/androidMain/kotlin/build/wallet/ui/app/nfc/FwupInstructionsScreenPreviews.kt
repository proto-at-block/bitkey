package build.wallet.ui.app.nfc

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.nfc.FwupInstructionsBodyModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
internal fun FwupInstructionsScreenPreview() {
  PreviewWalletTheme {
    FwupInstructionsScreen(
      model =
        FwupInstructionsBodyModel(
          onClose = {},
          headerModel =
            FormHeaderModel(
              headline = "Headline",
              subline = "Some subline",
              alignment = FormHeaderModel.Alignment.CENTER
            ),
          buttonText = "Click me",
          onButtonClick = {},
          eventTrackerScreenId = null,
          eventTrackerContext = null
        )
    )
  }
}
