package build.wallet.ui.components.tabbar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.icon.IconButton
import build.wallet.ui.compose.resId
import build.wallet.ui.model.icon.IconBackgroundType.Transient
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize.Small
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tooling.PreviewWalletTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun RowScope.TabBarItem(
  modifier: Modifier = Modifier,
  selected: Boolean,
  icon: Icon,
  onClick: () -> Unit,
  testTag: String? = null,
) {
  Box(
    modifier =
      modifier
        .fillMaxSize()
        .weight(1F)
        .resId(testTag)
        .clickable(interactionSource = MutableInteractionSource(), indication = null) { onClick() },
    contentAlignment = Alignment.Center
  ) {
    IconButton(
      iconModel =
        IconModel(
          icon = icon,
          iconSize = Small,
          iconBackgroundType = Transient
        ),
      color =
        if (selected) {
          WalletTheme.colors.foreground
        } else {
          WalletTheme.colors.foreground30
        },
      onClick = onClick
    )
  }
}

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
