package build.wallet.ui.components.tabbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun TabBarWithHomeAndSecurityHubPreview() {
  PreviewWalletTheme {
    TabBarWithHomeAndSecurityHub()
  }
}

@Preview
@Composable
fun TabBarWithHomeAndSecurityHubDesignSystemPreview() {
  TabBarWithHomeAndSecurityHubDesignSystem()
}

@Preview
@Composable
fun TabBarWithHomeAndSecurityHubBadgedDesignSystemPreview() {
  TabBarWithHomeAndSecurityHubBadgedDesignSystem()
}

@Preview
@Composable
fun TabBarWithHomeAndSecurityHubBadgedPreview() {
  PreviewWalletTheme {
    TabBarWithHomeAndSecurityHubBadged()
  }
}

@Composable
fun TabBarWithHomeAndSecurityHub() {
  TabBar(selectedIndex = 0, tabCount = 2) {
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
      Tab(
        icon = Icon.WalletFilled,
        selected = true,
        onClick = { },
        modifier = Modifier.offset(x = 3.dp)
      )
    }
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
      Tab(
        icon = Icon.Shield,
        selected = false,
        onClick = { },
        modifier = Modifier.offset(x = (-3).dp)
      )
    }
  }
}

@Composable
fun TabBarWithHomeAndSecurityHubBadged() {
  TabBar(selectedIndex = 0, tabCount = 2) {
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
      Tab(
        icon = Icon.WalletFilled,
        selected = true,
        onClick = { },
        modifier = Modifier.offset(x = 3.dp)
      )
    }
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
      Tab(
        icon = Icon.Shield,
        selected = false,
        badged = true,
        onClick = { },
        modifier = Modifier.offset(x = (-3).dp)
      )
    }
  }
}

@Composable
fun TabBarWithHomeAndSecurityHubDesignSystem() {
  PreviewWalletTheme {
    TabBarWithHomeAndSecurityHub()
  }
}

@Composable
fun TabBarWithHomeAndSecurityHubBadgedDesignSystem() {
  PreviewWalletTheme {
    TabBarWithHomeAndSecurityHubBadged()
  }
}
