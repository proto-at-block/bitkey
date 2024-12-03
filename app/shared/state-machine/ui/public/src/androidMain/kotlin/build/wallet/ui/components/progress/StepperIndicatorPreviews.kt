package build.wallet.ui.components.progress

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormMainContentModel.StepperIndicator
import build.wallet.statemachine.core.form.FormMainContentModel.StepperIndicator.StepStyle.*
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
internal fun StepIndicator() {
  PreviewWalletTheme {
    StepperIndicator(
      model = StepperIndicator(
        steps = immutableListOf(
          StepperIndicator.Step(
            style = COMPLETED,
            label = "Submitted",
            icon = IconImage.LocalImage(Icon.SmallIconCheck)
          ),
          StepperIndicator.Step(
            style = PENDING,
            label = "Processing",
            icon = IconImage.Loader
          ),
          StepperIndicator.Step(
            style = UPCOMING,
            label = "Complete",
            icon = null
          )
        )
      )
    )
  }
}
