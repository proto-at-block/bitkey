package bitkey.sample.ui.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import bitkey.sample.ui.model.LoadingBodyModel
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.loading.LoadingIndicator

@Composable
fun LoadingScreen(model: LoadingBodyModel) {
  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Label(model.message)
    LoadingIndicator(
      modifier = Modifier.size(64.dp)
    )
  }
}
