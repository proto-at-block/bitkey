package build.wallet.ui.app.nfc

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import bitkey.account.HardwareType
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.CENTER
import build.wallet.statemachine.nfc.FwupInstructionsBodyModel
import build.wallet.ui.theme.Theme
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview(name = "FWUP Instructions V2 (Light)")
@Composable
fun FwupInstructionsLightPreview() {
  PreviewWalletTheme(
    theme = Theme.LIGHT,
    designSystemUpdatesEnabled = true
  ) {
    fwupInstructionsPreviewModel().render(Modifier)
  }
}

@Preview(name = "FWUP Instructions V2 (Dark)")
@Composable
fun FwupInstructionsDarkPreview() {
  PreviewWalletTheme(
    theme = Theme.DARK,
    designSystemUpdatesEnabled = true
  ) {
    fwupInstructionsPreviewModel().render(Modifier)
  }
}

private fun fwupInstructionsPreviewModel() =
  FwupInstructionsBodyModel(
    onClose = {},
    headerModel = FormHeaderModel(
      headline = "Update your device",
      sublineModel = LabelModel.LinkSubstringModel.from(
        substringToOnClick = mapOf(
          "release notes" to {}
        ),
        string = "Press the button below and hold your unlocked device to the back of your phone until the update has completed. To learn more about this firmware update, see the release notes.",
        underline = true,
        bold = true
      ),
      alignment = CENTER
    ),
    buttonText = "Update Bitkey",
    onButtonClick = {},
    hardwareType = HardwareType.W3,
    eventTrackerScreenId = null
  )
