package build.wallet.ui.components.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ScreenColorMode
import build.wallet.statemachine.core.ScreenPresentationStyle

/**
 * A composable that configures screen theme using given style.
 *
 * We always draw content behind the system bars so the transparent color is used.
 * However, based on screen's representation style, we add or skip system bars padding -
 * that's done by [Screen].
 *
 * Here, based on the style, we set appropriate system bar icon colors.
 */
@Composable
fun ScreenTheme(
  bodyModel: BodyModel,
  colorMode: ScreenColorMode,
  presentationStyle: ScreenPresentationStyle,
  statusBannerBackgroundColor: Color?,
  content: @Composable (ScreenStyle) -> Unit,
) {
  val style = screenStyle(bodyModel, colorMode, presentationStyle)
  ConfigureSystemUi(style, statusBannerBackgroundColor)

  content(style)
}

@Composable
expect fun ConfigureSystemUi(
  style: ScreenStyle,
  statusBannerBackgroundColor: Color?,
)
