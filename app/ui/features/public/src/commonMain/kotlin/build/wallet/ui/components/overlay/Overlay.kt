package build.wallet.ui.components.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.ui.compose.blurIf
import build.wallet.ui.theme.WalletTheme

/**
 * A screen that allows rendering [overlayContent] on top of blurred [behindContent] background.
 *
 * Touch events for elements inside [behindContent] are blocked by the [OverlaySurface].
 */
@Composable
fun Overlay(
  modifier: Modifier = Modifier,
  showOverlay: Boolean = false,
  behindContent: @Composable () -> Unit,
  overlayContent: @Composable () -> Unit,
) {
  Box(modifier = modifier) {
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .background(WalletTheme.colors.background)
          .blurIf(condition = showOverlay, blurRadius = 20.dp)
    ) {
      behindContent()
    }
    if (showOverlay) {
      OverlaySurface(modifier = Modifier.fillMaxSize()) {
        overlayContent()
      }
    }
  }
}
