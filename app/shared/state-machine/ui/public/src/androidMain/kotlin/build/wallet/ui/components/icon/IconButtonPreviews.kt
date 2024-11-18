package build.wallet.ui.components.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.core.Icon
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
internal fun IconButtonSmall() {
  PreviewWalletTheme {
    IconButton(
      iconModel =
        IconModel(
          icon = Icon.SmallIconArrowLeft,
          iconSize = IconSize.Small,
          iconBackgroundType = IconBackgroundType.Transient
        ),
      onClick = {}
    )
  }
}

@Preview
@Composable
internal fun IconButtonRegular() {
  PreviewWalletTheme {
    IconButton(
      iconModel =
        IconModel(
          icon = Icon.SmallIconArrowLeft,
          iconSize = IconSize.Regular,
          iconBackgroundType = IconBackgroundType.Transient
        ),
      onClick = {}
    )
  }
}

@Preview
@Composable
internal fun IconButtonLarge() {
  PreviewWalletTheme {
    IconButton(
      iconModel =
        IconModel(
          icon = Icon.SmallIconArrowLeft,
          iconSize = IconSize.Large,
          iconBackgroundType = IconBackgroundType.Transient
        ),
      onClick = {}
    )
  }
}

@Preview
@Composable
internal fun IconButtonAvatar() {
  PreviewWalletTheme {
    IconButton(
      iconModel =
        IconModel(
          icon = Icon.SmallIconArrowLeft,
          iconSize = IconSize.Avatar,
          iconBackgroundType = IconBackgroundType.Transient
        ),
      onClick = {}
    )
  }
}

@Preview
@Composable
internal fun IconButtonInsideCircle() {
  PreviewWalletTheme {
    IconButton(
      iconModel =
        IconModel(
          icon = Icon.SmallIconArrowLeft,
          iconSize = IconSize.Small,
          iconBackgroundType = IconBackgroundType.Circle(circleSize = IconSize.Regular)
        ),
      onClick = {}
    )
  }
}

@Preview
@Composable
internal fun IconButtonInsideSquarePreview() {
  PreviewWalletTheme {
    IconButton(
      iconModel =
        IconModel(
          icon = Icon.SmallIconArrowRight,
          iconSize = IconSize.Accessory,
          iconBackgroundType = IconBackgroundType.Square(
            size = IconSize.Large,
            color = IconBackgroundType.Square.Color.Information,
            cornerRadius = 6
          )
        ),
      color = Color.White,
      onClick = {}
    )
  }
}

@Preview
@Composable
internal fun IconButtonInsideCircleWithText() {
  PreviewWalletTheme {
    IconButton(
      iconModel =
        IconModel(
          icon = Icon.SmallIconArrowDown,
          iconSize = IconSize.Small,
          iconBackgroundType = IconBackgroundType.Circle(circleSize = IconSize.Avatar)
        ),
      text = "hello",
      onClick = {}
    )
  }
}
