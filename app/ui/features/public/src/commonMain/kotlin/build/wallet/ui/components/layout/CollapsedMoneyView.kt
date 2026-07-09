package build.wallet.ui.components.layout

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.hidden_hero_asterisk
import build.wallet.ui.components.label.shimmer
import build.wallet.ui.compose.thenIf
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource

private val lightModeHiddenMoneyTint = Color(0xFFC4C3C0)

/**
 * Displays a redacted label with shimmering effect.
 *
 * @param shimmer true when the shimmer animation should be played.
 */
@Composable
fun CollapsedMoneyView(
  height: Dp,
  modifier: Modifier = Modifier,
  shimmer: Boolean = true,
) {
  val shouldUseLightModeHiddenMoneyTint =
    LocalTheme.current == Theme.LIGHT

  Image(
    painter = painterResource(Res.drawable.hidden_hero_asterisk),
    contentDescription = "value is hidden",
    contentScale = ContentScale.FillHeight,
    alignment = Alignment.Center,
    colorFilter = if (shouldUseLightModeHiddenMoneyTint) {
      ColorFilter.tint(lightModeHiddenMoneyTint)
    } else {
      null
    },
    modifier =
      modifier
        .height(height)
        .wrapContentWidth(align = Alignment.CenterHorizontally)
        .thenIf(shimmer) { Modifier.shimmer() }
  )
}
