package build.wallet.ui.components.screen

import androidx.compose.runtime.Composable
import build.wallet.statemachine.core.BodyModel
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
  presentationStyle: ScreenPresentationStyle,
  content: @Composable (ScreenStyle) -> Unit,
) {
  val style = screenStyle(bodyModel, presentationStyle)
  ConfigureSystemUi(style)

  content(style)
}

@Composable
expect fun ConfigureSystemUi(style: ScreenStyle)
