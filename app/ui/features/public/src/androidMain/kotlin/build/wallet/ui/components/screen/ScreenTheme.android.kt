package build.wallet.ui.components.screen

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme

@Composable
actual fun ConfigureSystemUi(style: ScreenStyle) {
  val activity = LocalContext.current as? ComponentActivity
  val theme = LocalTheme.current

  DisposableEffect(style) {
    val isDarkStatusBar = !style.useDarkSystemBarIcons
    activity?.enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.auto(
        android.graphics.Color.TRANSPARENT,
        android.graphics.Color.TRANSPARENT
      ) { isDarkStatusBar },
      navigationBarStyle = SystemBarStyle.auto(
        android.graphics.Color.TRANSPARENT,
        android.graphics.Color.TRANSPARENT
      ) { theme == Theme.DARK }
    )
    // enableEdgeToEdge sets isNavigationBarContrastEnforced = true in light mode,
    // which causes the system to add a scrim over the navigation bar area.
    // Disable it so the Compose background color shows through.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      activity?.window?.isNavigationBarContrastEnforced = false
    }

    onDispose {}
  }
}
