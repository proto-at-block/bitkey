package build.wallet.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.platform.config.AppVariant
import build.wallet.platform.device.DevicePlatform
import build.wallet.ui.compose.thenIf
import build.wallet.ui.theme.WalletTheme

/**
 * Keeps app content out of a status bar that sits beside the window, not above it.
 *
 * On the unfolded iPhone Duo, iOS puts the status bar in a vertical strip on the side of the
 * display. It reports a horizontal safe-area inset and no top inset. Screens only reserve top
 * insets, so without this, toolbars and content draw underneath the clock and system indicators.
 *
 * When that layout is detected, [content] is inset horizontally and the gutter is painted with a
 * per-variant [gutterBrush]. The app's corners facing the gutter are rounded so it reads as a card
 * sitting beside the status bar rather than being cut off by it. The horizontal insets are then
 * consumed so descendants don't pad them a second time.
 *
 * This only applies on iOS. On Android the status bar is always above the window, so a missing
 * top inset there means the bar is hidden (or the window is freeform), not beside the app.
 * Everywhere else this is a pass-through.
 */
@Composable
internal fun StatusBarGutter(
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit,
) {
  val gutterSide = sideStatusBarGutter()
  val hasSideStatusBar = gutterSide != null
  val horizontalInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)

  Box(
    modifier = modifier
      .fillMaxSize()
      .thenIf(hasSideStatusBar) {
        Modifier.background(gutterBrush(LocalAppVariant.current))
      }
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .thenIf(hasSideStatusBar) {
          Modifier
            .windowInsetsPadding(horizontalInsets)
            .consumeWindowInsets(horizontalInsets)
            .clip(gutterSide.cardShape())
        },
      content = content
    )
  }
}

private enum class GutterSide { Left, Right }

/**
 * Returns which side the status bar gutter is on, or null when the status bar is above the window
 * as usual or the platform isn't iOS.
 */
@Composable
private fun sideStatusBarGutter(): GutterSide? {
  if (LocalDeviceInfo.current.devicePlatform != DevicePlatform.IOS) return null
  val density = LocalDensity.current
  val layoutDirection = LocalLayoutDirection.current
  if (WindowInsets.statusBars.getTop(density) > 0) return null
  val safeDrawing = WindowInsets.safeDrawing
  return when {
    safeDrawing.getRight(density, layoutDirection) > 0 -> GutterSide.Right
    safeDrawing.getLeft(density, layoutDirection) > 0 -> GutterSide.Left
    else -> null
  }
}

/**
 * Rounds only the corners facing the gutter. The opposite side is already rounded by the display.
 */
private fun GutterSide?.cardShape(): RoundedCornerShape =
  when (this) {
    GutterSide.Right ->
      RoundedCornerShape(topEnd = GutterCardCornerRadius, bottomEnd = GutterCardCornerRadius)
    GutterSide.Left ->
      RoundedCornerShape(topStart = GutterCardCornerRadius, bottomStart = GutterCardCornerRadius)
    null -> RoundedCornerShape(0.dp)
  }

/** Matches the status banner's card corner radius in [build.wallet.ui.components.screen.Screen]. */
private val GutterCardCornerRadius: Dp = 32.dp

/**
 * Gutter fill by app variant. Internal builds are loud so it's obvious which build you're holding;
 * the production gutter is black so it blends into the display bezel and never reads as an alert.
 */
@Composable
private fun gutterBrush(appVariant: AppVariant): Brush =
  when (appVariant) {
    AppVariant.Development -> Brush.verticalGradient(RainbowGutterColors)
    AppVariant.Team, AppVariant.Alpha -> SolidColor(WalletTheme.colors.warningForeground)
    AppVariant.Customer, AppVariant.Emergency -> SolidColor(Color.Black)
  }

private val RainbowGutterColors = listOf(
  Color(0xFFFF3B30),
  Color(0xFFFF9500),
  Color(0xFFFFCC00),
  Color(0xFF34C759),
  Color(0xFF007AFF),
  Color(0xFF5856D6),
  Color(0xFFAF52DE)
)
