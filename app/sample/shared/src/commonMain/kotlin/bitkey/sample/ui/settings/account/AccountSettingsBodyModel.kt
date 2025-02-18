package bitkey.sample.ui.settings.account

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

data class AccountSettingsBodyModel(
  val deletingAccount: Boolean,
  val onDeleteAccountClick: () -> Unit,
  override val onBack: () -> Unit,
) : BodyModel() {
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null

  @Composable
  override fun render(modifier: Modifier) {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Label("Account Settings")
      Button(
        text = "Delete Account",
        isLoading = deletingAccount,
        onClick = StandardClick(onDeleteAccountClick)
      )
      Spacer(modifier = Modifier.height(16.dp))
      Button(text = "Go back", onClick = StandardClick(onBack))
    }
  }
}
