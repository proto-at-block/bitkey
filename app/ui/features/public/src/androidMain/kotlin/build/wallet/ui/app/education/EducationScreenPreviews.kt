package build.wallet.ui.app.education

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.Progress
import build.wallet.statemachine.education.EducationBodyModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun PreviewExplainerScreen() {
  PreviewWalletTheme {
    EducationScreen(
      model =
        EducationBodyModel(
          progressPercentage = Progress.Half,
          onDismiss = {},
          title = "This is the title of the education screen",
          subtitle = "This is the subtitle of the education screen",
          primaryButton =
            ButtonModel(
              text = "Primary Button",
              size = ButtonModel.Size.Footer,
              onClick = StandardClick {}
            ),
          secondaryButton =
            ButtonModel(
              text = "Secondary Button",
              size = ButtonModel.Size.Footer,
              treatment = ButtonModel.Treatment.Secondary,
              onClick = StandardClick {}
            ),
          onClick = {},
          onBack = {}
        )
    )
  }
}
