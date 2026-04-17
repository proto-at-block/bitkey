package build.wallet.ui.components.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme

@Composable
fun Divider(
  modifier: Modifier = Modifier,
  color: Color? = null,
  thickness: Dp = 1.dp,
) {
  val resolvedColor =
    color ?: if (LocalDesignSystemUpdatesEnabled.current && LocalTheme.current == Theme.DARK) {
      WalletTheme.colors.foreground30
    } else {
      WalletTheme.colors.foreground10
    }
  Box(
    modifier
      .fillMaxWidth()
      .height(thickness)
      .background(color = resolvedColor)
  )
}
