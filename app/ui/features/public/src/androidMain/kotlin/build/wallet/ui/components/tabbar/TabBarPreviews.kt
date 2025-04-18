package build.wallet.ui.components.tabbar

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.core.Icon
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun TabBarWithHomeAndSecurityHubPreview() {
  PreviewWalletTheme {
    TabBar {
      Tab(
        icon = Icon.SmallIconWalletFilled,
        selected = true,
        onClick = { }
      )
      Tab(
        icon = Icon.SmallIconShield,
        selected = false,
        onClick = { }
      )
    }
  }
}

@Preview
@Composable
fun TabBarWithHomeAndSecurityHubBadgedPreview() {
  PreviewWalletTheme {
    TabBar {
      Tab(
        icon = Icon.SmallIconWalletFilled,
        selected = true,
        onClick = { }
      )
      Tab(
        icon = Icon.SmallIconShield,
        selected = false,
        badged = true,
        onClick = { }
      )
    }
  }
}

@Composable
fun TabBarWithHomeAndSecurityHub() {
  TabBar {
    Tab(
      icon = Icon.SmallIconWalletFilled,
      selected = true,
      onClick = { }
    )
    Tab(
      icon = Icon.SmallIconShield,
      selected = false,
      onClick = { }
    )
  }
}

@Composable
fun TabBarWithHomeAndSecurityHubBadged() {
  TabBar {
    Tab(
      icon = Icon.SmallIconWalletFilled,
      selected = true,
      onClick = { }
    )
    Tab(
      icon = Icon.SmallIconShield,
      selected = false,
      badged = true,
      onClick = { }
    )
  }
}
