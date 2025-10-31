package build.wallet.ui.components.coachmark

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.ui.model.coachmark.CoachmarkLabelTreatment
import build.wallet.ui.model.list.CoachmarkLabelModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
internal fun CoachmarkLabelPreviews() {
  PreviewWalletTheme {
    Box(modifier = Modifier.padding(16.dp)) {
      Column {
        CoachmarkLabel(
          CoachmarkLabelModel(
            text = "New",
            treatment = CoachmarkLabelTreatment.Light
          )
        )
        Spacer(Modifier.height(8.dp))
        CoachmarkLabel(
          CoachmarkLabelModel(
            text = "New",
            treatment = CoachmarkLabelTreatment.Dark
          )
        )
        Spacer(Modifier.height(8.dp))
        CoachmarkLabel(
          CoachmarkLabelModel(
            text = "New",
            treatment = CoachmarkLabelTreatment.Disabled
          )
        )
      }
    }
  }
}
