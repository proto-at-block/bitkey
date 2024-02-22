package build.wallet.ui.components.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.label.Label
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
fun ListHeader(
  modifier: Modifier = Modifier,
  title: String,
) {
  Box(modifier = modifier.fillMaxWidth()) {
    Label(
      modifier =
        Modifier.padding(
          top = 16.dp,
          bottom = 8.dp
        ),
      text = title,
      type = LabelType.Title2
    )
  }
}

@Composable
@Preview
internal fun ListHeaderPreview() {
  PreviewWalletTheme {
    ListHeader(title = "Recent activity")
  }
}
