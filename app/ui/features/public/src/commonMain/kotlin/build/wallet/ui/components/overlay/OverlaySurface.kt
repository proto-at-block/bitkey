package build.wallet.ui.components.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import build.wallet.ui.theme.WalletTheme
import androidx.compose.material3.Surface as MaterialSurface

/**
 * Renders [content] on top of a surface while blocking touch events for elements behind
 * the surface.
 *
 * To make the content behind the surface appear blurred, see [Modifier.blur].
 */
@Composable
internal fun OverlaySurface(
  modifier: Modifier = Modifier,
  surfaceColor: Color = WalletTheme.colors.mask,
  content: @Composable () -> Unit,
) {
  MaterialSurface(
    modifier = modifier,
    color = surfaceColor
  ) {
    Box {
      content()
    }
  }
}
