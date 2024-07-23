package build.wallet.ui.components.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.textStyle
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import androidx.compose.material.LinearProgressIndicator as MaterialLinearProgressIndicator

@Composable
fun StepperIndicator(model: FormMainContentModel.StepperIndicator) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.fillMaxWidth()
  ) {
    MaterialLinearProgressIndicator(
      modifier = Modifier.height(12.dp).fillMaxWidth(),
      progress = model.progress,
      color = WalletTheme.colors.bitkeyPrimary,
      backgroundColor = WalletTheme.colors.foreground10,
      strokeCap = StrokeCap.Round
    )
    Row(
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
    ) {
      model.labels.forEach { label ->
        Text(
          text = label,
          style = WalletTheme.textStyle(
            type = LabelType.Body4Regular,
            treatment = LabelTreatment.Secondary,
            textColor = WalletTheme.colors.secondaryForeground
          )
        )
      }
    }
  }
}

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
