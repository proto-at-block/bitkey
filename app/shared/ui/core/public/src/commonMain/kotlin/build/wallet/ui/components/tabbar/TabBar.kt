package build.wallet.ui.components.tabbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.TabBarModel
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.theme.WalletTheme

@Composable
fun TabBar(
  modifier: Modifier = Modifier,
  model: TabBarModel,
) {
  with(model) {
    val items by remember(firstItem, secondItem) {
      derivedStateOf { listOf(firstItem, secondItem) }
    }
    TabBar(modifier) {
      items.onEach {
        TabBarItem(
          selected = it.selected,
          icon = it.icon,
          onClick = it.onClick,
          testTag = it.testTag
        )
      }
    }
  }
}

@Composable
fun TabBar(
  modifier: Modifier = Modifier,
  items: @Composable RowScope.() -> Unit,
) {
  Column(
    modifier =
      modifier
        .background(color = WalletTheme.colors.background)
        .fillMaxWidth()
  ) {
    Divider(Modifier.fillMaxWidth())
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(60.dp)
          .padding(horizontal = 16.dp)
          .selectableGroup(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceEvenly
    ) {
      items()
    }
  }
}
