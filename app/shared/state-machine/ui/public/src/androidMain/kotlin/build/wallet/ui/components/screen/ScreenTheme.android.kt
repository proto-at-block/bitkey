package build.wallet.ui.components.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import com.google.accompanist.systemuicontroller.rememberSystemUiController

@Composable
actual fun ConfigureSystemUi(
  style: ScreenStyle,
  statusBannerBackgroundColor: Color?,
) {
  val systemUiController = rememberSystemUiController()
  DisposableEffect(systemUiController, style, statusBannerBackgroundColor) {
    // Update all of the system bar colors and use dark icons if we're in light theme
    systemUiController.setStatusBarColor(
      // If there's a status banner, use the color of the status banner
      // so it seamlessly blends into the status bar. Otherwise, transparent.
      //
      // Note: it would be better to handle this with optionally applying
      // status bar padding modifier or not to the screen, so if more cases
      // emerge that need to bleed into the status bar, update to that.
      color = statusBannerBackgroundColor ?: Color.Transparent,
      darkIcons = style.useDarkSystemBarIcons
    )
    systemUiController.setNavigationBarColor(
      color = Color.Transparent,
      darkIcons = style.useDarkSystemBarIcons,
      navigationBarContrastEnforced = false
    )

    onDispose {}
  }
}
