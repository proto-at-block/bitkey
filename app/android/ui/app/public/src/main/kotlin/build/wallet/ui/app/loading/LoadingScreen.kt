package build.wallet.ui.app.loading

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.LoadingBodyModel.Style.Explicit
import build.wallet.statemachine.core.LoadingBodyModel.Style.Implicit
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.loading.LoadingIndicator
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
fun LoadingScreen() {
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
  ) {
    LoadingIndicator()
  }
}

@Composable
fun LoadingScreen(model: LoadingBodyModel) {
  model.onBack?.let {
    BackHandler(onBack = it)
  }

  Column(
    modifier =
      Modifier
        .padding(horizontal = 20.dp)
        .fillMaxSize(),
    horizontalAlignment =
      when (model.style) {
        Explicit -> Alignment.Start
        Implicit -> Alignment.CenterHorizontally
      },
    verticalArrangement =
      when (model.style) {
        Explicit -> Arrangement.Top
        Implicit -> Arrangement.Center
      }
  ) {
    Spacer(Modifier.height(76.dp))
    LoadingIndicator(modifier = Modifier.size(64.dp))
    model.message?.let {
      Spacer(modifier = Modifier.height(17.dp))
      Label(text = it, type = LabelType.Title1)
    }
  }
}

@Preview
@Composable
private fun LoadingScreenNoMessagePreview() {
  PreviewWalletTheme {
    LoadingScreen(
      model =
        LoadingBodyModel(
          message = null,
          onBack = {},
          id = null
        )
    )
  }
}

@Preview
@Composable
private fun LoadingScreenWithMessagePreview() {
  PreviewWalletTheme {
    LoadingScreen(
      model =
        LoadingBodyModel(
          message = "Loading something important...",
          onBack = {},
          id = null
        )
    )
  }
}

@Preview
@Composable
private fun LoadingScreenImplicitPreview() {
  PreviewWalletTheme {
    LoadingScreen(
      model =
        LoadingBodyModel(
          message = null,
          onBack = {},
          style = Implicit,
          id = null
        )
    )
  }
}
