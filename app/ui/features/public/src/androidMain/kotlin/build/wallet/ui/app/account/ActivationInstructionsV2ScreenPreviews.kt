package build.wallet.ui.app.account

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.analytics.events.screen.context.PairHardwareEventTrackerScreenIdContext
import build.wallet.statemachine.account.create.full.hardware.ActivationInstructionsV2BodyModel
import build.wallet.ui.app.account.create.hardware.PairNewHardwareScreen
import build.wallet.ui.theme.Theme
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview(name = "Activation Instructions V2 (Light)")
@Composable
fun ActivationInstructionsV2ScreenLightPreview() {
  PreviewWalletTheme(
    theme = Theme.LIGHT,
  ) {
    PairNewHardwareScreen(
      model = activationInstructionsV2PreviewModel()
    )
  }
}

@Preview(name = "Activation Instructions V2 (Dark)")
@Composable
fun ActivationInstructionsV2ScreenDarkPreview() {
  PreviewWalletTheme(
    theme = Theme.DARK,
  ) {
    PairNewHardwareScreen(
      model = activationInstructionsV2PreviewModel()
    )
  }
}

@Preview(name = "Activation Instructions V2 (Layout Debug)")
@Composable
fun ActivationInstructionsV2ScreenDebugPreview() {
  PreviewWalletTheme(
    theme = Theme.LIGHT,
  ) {
    PairNewHardwareScreen(
      model = activationInstructionsV2PreviewModel(),
      debugHeroLayout = true
    )
  }
}

private fun activationInstructionsV2PreviewModel() =
  ActivationInstructionsV2BodyModel(
    onBack = {},
    onContinue = {},
    onHelpClick = {},
    isNavigatingBack = false,
    eventTrackerContext = PairHardwareEventTrackerScreenIdContext.ACCOUNT_CREATION
  )
