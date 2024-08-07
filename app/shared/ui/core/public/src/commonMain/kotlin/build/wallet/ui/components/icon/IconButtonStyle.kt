package build.wallet.ui.components.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

/**
 * Style configuration for [IconButton].
 */
data class IconButtonStyle(
  val size: Dp,
  val backgroundStyle: BackgroundStyle,
) {
  sealed class BackgroundStyle {
    data class ShapedBackground(
      val shape: Shape,
      val color: Color,
    ) : BackgroundStyle()

    data object NoBackground : BackgroundStyle()
  }
}
