package build.wallet.ui.components.tabbar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun TabBarPill(
  modifier: Modifier = Modifier,
  tabs: @Composable () -> Unit,
)
