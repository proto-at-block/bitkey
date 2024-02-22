package build.wallet.ui.app.dev

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.label.Label
import build.wallet.ui.model.Model
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
internal fun UiModelNotImplemented(model: Model) {
  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .border(width = 5.dp, color = Color.Red)
        .padding(5.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Label(
        text = "\uD83D\uDEA7 Can’t display ${model::class.simpleName}.",
        type = LabelType.Label2
      )
      Label(
        text = "Forgot to update AppUiModelMap?",
        type = LabelType.Label2
      )
    }
  }
}

@Preview
@Composable
private fun NotImplementedModelPreview() {
  PreviewWalletTheme {
    UiModelNotImplemented(SomeModel)
  }
}

private object SomeModel : Model()
