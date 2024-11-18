package build.wallet.ui.components.tabbar

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.core.Icon
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
private fun TabBarItemSelectedPreview() {
  PreviewWalletTheme {
    Row {
      TabBarItem(
        icon = Icon.TabIconHome,
        selected = true,
        onClick = {}
      )
    }
  }
}

@Preview
@Composable
private fun TabBarItemNotSelectedPreview() {
  PreviewWalletTheme {
    Row {
      TabBarItem(
        icon = Icon.TabIconHome,
        selected = false,
        onClick = {}
      )
    }
  }
}
