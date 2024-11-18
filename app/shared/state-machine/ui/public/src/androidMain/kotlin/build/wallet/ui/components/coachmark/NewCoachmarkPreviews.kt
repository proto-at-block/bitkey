package build.wallet.ui.components.coachmark

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.ui.model.coachmark.NewCoachmarkTreatment
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
internal fun NewLabelPreviews() {
  PreviewWalletTheme {
    Box(modifier = Modifier.padding(16.dp)) {
      Column {
        NewCoachmark(NewCoachmarkTreatment.Light)
        Spacer(Modifier.height(8.dp))
        NewCoachmark(NewCoachmarkTreatment.Dark)
        Spacer(Modifier.height(8.dp))
        NewCoachmark(NewCoachmarkTreatment.Disabled)
      }
    }
  }
}
