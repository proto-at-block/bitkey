package build.wallet.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun getScreenSize(): ScreenSize {
  val containerSize = LocalWindowInfo.current.containerSize
  return with(LocalDensity.current) {
    ScreenSize(
      width = containerSize.width.toDp(),
      height = containerSize.height.toDp()
    )
  }
}
