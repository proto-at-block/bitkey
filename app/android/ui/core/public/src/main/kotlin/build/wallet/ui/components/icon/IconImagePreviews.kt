package build.wallet.ui.components.icon

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.ui.model.icon.*
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tooling.PreviewWalletTheme

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

@Preview
@Composable
internal fun IconsSmallPreview() {
  IconsForPreview(size = IconSize.Small)
}

@Preview
@Composable
internal fun IconsRegularPreview() {
  IconsForPreview(size = IconSize.Regular)
}

@Preview
@Composable
internal fun IconsTintedPreview() {
  IconsForPreview(
    size = IconSize.Regular,
    color = WalletTheme.colors.warningForeground
  )
}

@Preview
@Composable
internal fun IconsLargePreview() {
  IconsForPreview(size = IconSize.Large)
}

@Preview
@Composable
internal fun IconsAvatarPreview() {
  IconsForPreview(size = IconSize.Avatar)
}

@Composable
internal fun IconsForPreview(
  size: IconSize,
  color: Color = Color.Unspecified,
) {
  PreviewWalletTheme {
    LazyVerticalGrid(
      columns = GridCells.Adaptive(50.dp)
    ) {
      items(iconsToPreview()) { icon ->
        Icon(
          modifier = Modifier.padding(5.dp),
          icon = icon,
          size = size,
          color = color
        )
      }
    }
  }
}

private fun iconsToPreview(): List<Icon> {
  // Filter shared icon definitions that shouldn't be snapshot tested
  return Icon.entries
    .filter {
      it != Icon.BuyOwnBitkeyHero && it != Icon.SubtractLeft && it != Icon.SubtractRight && it != Icon.CalloutArrow
    }.toList()
}
