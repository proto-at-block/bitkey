package bitkey.sample.ui.error

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.label.Label
import build.wallet.ui.model.StandardClick

data class ErrorBodyModel(
  val message: String,
  override val onBack: () -> Unit,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel() {
  @Composable
  override fun render(modifier: Modifier) {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Label("Error: $message")
      Spacer(modifier = Modifier.height(16.dp))
      Button(text = "Done", onClick = StandardClick(onBack))
    }
  }
}
