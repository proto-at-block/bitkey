package build.wallet.ui.components.label

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import build.wallet.ui.theme.SystemColorMode
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview // (name = "All Labels Light")
@Composable
private fun AllLabelsLightPreview() {
  PreviewWalletTheme {
    AllLabelsPreview()
  }
}

@Preview // (name = "All Labels Dark")
@Composable
private fun AllLabelsDarkPreview() {
  PreviewWalletTheme(systemColorMode = SystemColorMode.DARK) {
    Box(modifier = Modifier.background(color = WalletTheme.colors.foreground)) {
      AllLabelsPreview()
    }
  }
}

@Composable
fun AllLabelsPreview() {
  Box {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      LabelType.entries.forEach { labelType ->
        Row(
          modifier = Modifier.border(width = 1.dp, color = Color.LightGray)
        ) {
          Label(
            text = labelType.name,
            type = labelType
          )
        }
      }
    }
  }
}

@Preview // (name = "Long Content")
@Composable
fun LabelWithLongContentPreview() {
  PreviewWalletTheme {
    Label(text = LONG_CONTENT, type = LabelType.Label3)
  }
}

private const val LONG_CONTENT =
  "A purely peer-to-peer version of electronic cash would allow online " +
    "payments to be sent directly from one party to another without going through a " +
    "financial institution."
