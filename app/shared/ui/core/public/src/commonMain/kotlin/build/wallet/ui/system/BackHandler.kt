package build.wallet.ui.system

import androidx.compose.runtime.Composable

@Composable
expect fun BackHandler(
  enabled: Boolean = true,
  onBack: () -> Unit,
)
