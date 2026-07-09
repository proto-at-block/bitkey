package build.wallet.ui.app.account.create.onboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.account.create.full.onboard.BuildHardwareDescriptorIntroBodyModel
import build.wallet.ui.theme.Theme
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview(name = "Create Wallet Intro (Light)")
@Composable
fun BuildHardwareDescriptorIntroLightPreview() {
  BuildHardwareDescriptorIntroPreview(
    theme = Theme.LIGHT,
  )
}

@Preview(name = "Create Wallet Intro (Dark)")
@Composable
fun BuildHardwareDescriptorIntroDarkPreview() {
  BuildHardwareDescriptorIntroPreview(
    theme = Theme.DARK,
  )
}

@Composable
private fun BuildHardwareDescriptorIntroPreview(
  theme: Theme,
) {
  PreviewWalletTheme(
    theme = theme,
  ) {
    BuildHardwareDescriptorIntroBodyModel(
      onTapBitkey = {},
      onBack = {}
    ).render(Modifier)
  }
}
