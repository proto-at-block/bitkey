package build.wallet.ui.components.progress

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.form.FormMainContentModel

@Preview
@Composable
internal fun ThreeStepIndicator() {
  StepperIndicator(
    model = FormMainContentModel.StepperIndicator(
      progress = 0.5f,
      labels = immutableListOf("Step 1", "Step 2", "Step 3")
    )
  )
}

@Preview
@Composable
internal fun SingleStepIndicator() {
  StepperIndicator(
    model = FormMainContentModel.StepperIndicator(
      progress = 0.5f,
      labels = immutableListOf("Step 1")
    )
  )
}
