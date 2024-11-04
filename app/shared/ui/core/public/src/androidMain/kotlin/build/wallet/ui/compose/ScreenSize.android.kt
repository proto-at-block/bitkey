package build.wallet.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

@Composable
actual fun getScreenSize(): ScreenSize {
  val configuration = LocalConfiguration.current
  return ScreenSize(
    width = configuration.screenWidthDp.dp,
    height = configuration.screenHeightDp.dp
  )
}
