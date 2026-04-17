package build.wallet.ui.app.account.create.onboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.account.create.full.onboard.BuildHardwareDescriptorIntroBodyModel
import build.wallet.statemachine.account.create.full.onboard.BuildHardwareDescriptorIntroV2BodyModel
import build.wallet.ui.theme.Theme
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview(name = "Create Wallet Intro DSV1 (Light)")
@Composable
fun BuildHardwareDescriptorIntroDsv1LightPreview() {
  BuildHardwareDescriptorIntroPreview(
    theme = Theme.LIGHT,
    designSystemUpdatesEnabled = false
  )
}

@Preview(name = "Create Wallet Intro DSV1 (Dark)")
@Composable
fun BuildHardwareDescriptorIntroDsv1DarkPreview() {
  BuildHardwareDescriptorIntroPreview(
    theme = Theme.DARK,
    designSystemUpdatesEnabled = false
  )
}

@Preview(name = "Create Wallet Intro DSV2 (Light)")
@Composable
fun BuildHardwareDescriptorIntroDsv2LightPreview() {
  BuildHardwareDescriptorIntroPreview(
    theme = Theme.LIGHT,
    designSystemUpdatesEnabled = true
  )
}

@Preview(name = "Create Wallet Intro DSV2 (Dark)")
@Composable
fun BuildHardwareDescriptorIntroDsv2DarkPreview() {
  BuildHardwareDescriptorIntroPreview(
    theme = Theme.DARK,
    designSystemUpdatesEnabled = true
  )
}

@Composable
private fun BuildHardwareDescriptorIntroPreview(
  theme: Theme,
  designSystemUpdatesEnabled: Boolean,
) {
  PreviewWalletTheme(
    theme = theme,
    designSystemUpdatesEnabled = designSystemUpdatesEnabled
  ) {
    if (designSystemUpdatesEnabled) {
      BuildHardwareDescriptorIntroV2BodyModel(
        onTapBitkey = {},
        onBack = {}
      ).render(Modifier)
    } else {
      BuildHardwareDescriptorIntroBodyModel(
        onTapBitkey = {},
        onBack = {}
      ).render(Modifier)
    }
  }
}
