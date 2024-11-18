package build.wallet.ui.components.callout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.ui.model.callout.CalloutModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun CalloutPreviews() {
  val multipleLines = listOf(false, true)
  val treatments = CalloutModel.Treatment.entries
  val configs = treatments.flatMap { treatment ->
    multipleLines.map { multipleLines ->
      CalloutPreviewConfig(
        multipleLines = multipleLines,
        treatment = treatment
      )
    }
  }

  PreviewWalletTheme {
    LazyVerticalGrid(
      modifier = Modifier
        .fillMaxWidth()
        .background(color = Color.White)
        .padding(all = 10.dp),
      columns = GridCells.Fixed(2),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      items(configs) { config ->
        Callout(
          model = CalloutModel(
            title = "Title",
            subtitle = when (config.multipleLines) {
              true -> StringModel("Subtitle line one\nSubtitle line two")
              false -> StringModel("Subtitle")
            },
            treatment = config.treatment,
            leadingIcon = Icon.SmallIconCheck,
            trailingIcon = Icon.SmallIconArrowRight
          )
        )
      }
    }
  }
}

private data class CalloutPreviewConfig(
  val multipleLines: Boolean,
  val treatment: CalloutModel.Treatment,
)
