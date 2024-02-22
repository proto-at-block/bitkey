package build.wallet.ui.components.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment.Secondary
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
fun ListSectionHeader(
  modifier: Modifier = Modifier,
  title: String,
) {
  Box(modifier = modifier.fillMaxWidth()) {
    Label(
      modifier =
        Modifier.padding(
          top = 8.dp
        ),
      text = title,
      type = LabelType.Title3,
      treatment = Secondary
    )
  }
}

@Composable
@Preview
internal fun ListSectionHeaderPreview() {
  PreviewWalletTheme {
    ListHeader(title = "Pending")
  }
}
