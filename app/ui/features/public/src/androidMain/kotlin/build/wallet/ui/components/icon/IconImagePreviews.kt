package build.wallet.ui.components.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.core.Icon
import build.wallet.ui.model.icon.*

@Preview
@Composable
internal fun IconImageWithCircleBackground() {
  IconImage(
    model = IconModel(
      icon = Icon.SmallIconPhone,
      iconTint = IconTint.Primary,
      iconSize = IconSize.Large,
      iconBackgroundType = IconBackgroundType.Circle(
        circleSize = IconSize.Avatar,
        color = IconBackgroundType.Circle.CircleColor.PrimaryBackground20
      )
    )
  )
}

@Preview
@Composable
internal fun IconImageWithSquareBackgroundPreview() {
  IconImage(
    model = IconModel(
      icon = Icon.SmallIconArrowRight,
      iconSize = IconSize.Accessory,
      iconBackgroundType = IconBackgroundType.Square(
        size = IconSize.Large,
        color = IconBackgroundType.Square.Color.Default,
        cornerRadius = 12
      )
    ),
    color = Color.White
  )
}

@Preview
@Composable
internal fun IconImageWithAlphaPreview() {
  IconImage(
    model = IconModel(
      icon = Icon.BitkeyDevice3D,
      iconSize = IconSize.XLarge,
      iconOpacity = 0.5f
    )
  )
}

@Preview
@Composable
internal fun IconImageFromUrlPreview() {
  IconImage(
    iconImage =
      IconImage.UrlImage(
        url = "https://upload.wikimedia.org/wikipedia/commons/c/c5/Square_Cash_app_logo.svg",
        fallbackIcon = Icon.Bitcoin
      ),
    size = IconSize.Small
  )
}
