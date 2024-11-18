package build.wallet.ui.components.tabbar

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.core.Icon
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun TabBarTwoItemsPreview() {
  PreviewWalletTheme {
    TabBar(
      items = {
        TabBarItem(
          icon = Icon.TabIconHome,
          selected = true,
          onClick = {}
        )
        TabBarItem(
          icon = Icon.TabIconProfile,
          selected = false,
          onClick = {}
        )
      }
    )
  }
}

@Preview
@Composable
fun TabBarThreeItemsPreview() {
  PreviewWalletTheme {
    TabBar(
      items = {
        TabBarItem(
          icon = Icon.TabIconHome,
          selected = false,
          onClick = {}
        )
        TabBarItem(
          icon = Icon.TabIconProfile,
          selected = true,
          onClick = {}
        )
        TabBarItem(
          icon = Icon.TabIconProfile,
          selected = false,
          onClick = {}
        )
      }
    )
  }
}

@Preview
@Composable
fun TabBarFourItemsPreview() {
  PreviewWalletTheme {
    TabBar(
      items = {
        TabBarItem(
          icon = Icon.TabIconHome,
          selected = false,
          onClick = {}
        )
        TabBarItem(
          icon = Icon.TabIconHome,
          selected = true,
          onClick = {}
        )
        TabBarItem(
          icon = Icon.TabIconProfile,
          selected = false,
          onClick = {}
        )
        TabBarItem(
          icon = Icon.TabIconProfile,
          selected = false,
          onClick = {}
        )
      }
    )
  }
}
