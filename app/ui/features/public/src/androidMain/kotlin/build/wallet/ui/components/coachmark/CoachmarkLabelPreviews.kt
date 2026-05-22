package build.wallet.ui.components.coachmark

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.ui.model.coachmark.CoachmarkLabelTreatment
import build.wallet.ui.model.list.CoachmarkLabelModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview(name = "Coachmark Label")
@Composable
internal fun CoachmarkLabelPreview() {
  PreviewWalletTheme {
    CoachmarkLabelPreviewContent()
  }
}

@Preview(name = "Coachmark Label DSV2")
@Composable
internal fun CoachmarkLabelDesignSystemV2Preview() {
  PreviewWalletTheme {
    CoachmarkLabelPreviewContent()
  }
}

@Composable
private fun CoachmarkLabelPreviewContent() {
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
