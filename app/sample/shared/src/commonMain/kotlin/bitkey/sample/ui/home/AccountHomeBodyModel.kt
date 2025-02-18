package bitkey.sample.ui.home

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

data class AccountHomeBodyModel(
  val accountId: String,
  val accountName: String,
  val onSettingsClick: () -> Unit,
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
      Label("Account name: $accountName")
      Label("Account ID: $accountId")
      Spacer(modifier = Modifier.height(16.dp))
      Button(text = "Settings", onClick = StandardClick(onSettingsClick))
    }
  }
}
