package build.wallet.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

data class ScreenSize(
  val width: Dp,
  val height: Dp,
)

@Composable
expect fun getScreenSize(): ScreenSize
