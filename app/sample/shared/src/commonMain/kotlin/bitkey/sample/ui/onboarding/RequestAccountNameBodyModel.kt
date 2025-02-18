package bitkey.sample.ui.onboarding

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

data class RequestAccountNameBodyModel(
  val onEnterAccountName: (String) -> Unit,
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
      Label("Enter your account name.")
      Button(
        text = "Continue",
        onClick = StandardClick {
          // prefill fake name
          onEnterAccountName("jack")
        }
      )
      Spacer(modifier = Modifier.height(16.dp))
      Button(
        text = "Go back",
        onClick = StandardClick(onBack)
      )
    }
  }
}
