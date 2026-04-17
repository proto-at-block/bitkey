package build.wallet.ui.components.tabbar

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun TabBarPill(
  modifier: Modifier = Modifier,
  selectedIndex: Int,
  tabCount: Int,
  tabs: @Composable RowScope.() -> Unit,
)
