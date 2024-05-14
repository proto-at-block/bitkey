package bitkey.sample.ui.onboarding

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
fun RequestAccountNameScreen(model: RequestAccountNameBodyModel) {
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
        model.onEnterAccountName("jack")
      }
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(
      text = "Go back",
      onClick = StandardClick(model.onBack)
    )
  }
}
