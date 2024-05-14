package bitkey.sample.ui.settings.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.label.Label
import build.wallet.ui.model.StandardClick

@Composable
fun AccountSettingsScreen(model: AccountSettingsBodyModel) {
  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Label("Account Settings")
    Button(
      text = "Delete Account",
      isLoading = model.deletingAccount,
      onClick = StandardClick(model.onDeleteAccountClick)
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(text = "Go back", onClick = StandardClick(model.onBack))
  }
}
