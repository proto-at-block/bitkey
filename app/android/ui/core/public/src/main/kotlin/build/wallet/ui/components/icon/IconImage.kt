@file:Suppress("TooManyFunctions")

package build.wallet.ui.components.icon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.Bitcoin
import build.wallet.statemachine.core.Icon.BuyOwnBitkeyHero
import build.wallet.ui.components.loading.LoadingIndicator
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconBackgroundType.Circle
import build.wallet.ui.model.icon.IconBackgroundType.Transient
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconImage.LocalImage
import build.wallet.ui.model.icon.IconImage.UrlImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconSize.Small
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.painter
import build.wallet.ui.tooling.PreviewWalletTheme
import coil.compose.AsyncImagePainter.State
import coil.compose.AsyncImagePainter.State.Empty
import coil.compose.AsyncImagePainter.State.Loading
import coil.compose.AsyncImagePainter.State.Success
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import androidx.compose.material3.Icon as MaterialIcon

@Composable
fun Icon(
  modifier: Modifier = Modifier,
  icon: Icon,
  size: IconSize,
  color: Color = Color.Unspecified,
  tint: IconTint? = null,
  text: String? = null,
) {
  val style =
    WalletTheme.iconStyle(
      icon = LocalImage(icon),
      color = color,
      tint = tint
    )
  IconImage(
    modifier = modifier,
    model =
      IconModel(
        LocalImage(icon),
        iconSize = size,
        text = text
      ),
    style = style
  )
}

@Composable
fun IconImage(
  model: IconModel,
  modifier: Modifier = Modifier,
  color: Color = Color.Unspecified,
  text: String? = null,
) {
  with(model) {
    IconImage(
      modifier = modifier,
      iconImage = iconImage,
      size = iconSize,
      background = iconBackgroundType,
      color = color,
      tint = iconTint,
      text = text
    )
  }
}

@Composable
fun IconImage(
  modifier: Modifier = Modifier,
  iconImage: IconImage,
  size: IconSize,
  background: IconBackgroundType = Transient,
  color: Color = Color.Unspecified,
  tint: IconTint? = null,
  text: String? = null,
) {
  val style =
    WalletTheme.iconStyle(
      icon = iconImage,
      color = color,
      tint = tint
    )
  IconImage(
    modifier = modifier,
    model =
      IconModel(
        iconImage = iconImage,
        iconBackgroundType = background,
        iconSize = size,
        text = text
      ),
    style = style
  )
}

@Composable
fun IconImage(
  model: IconModel,
  modifier: Modifier = Modifier,
  style: IconStyle,
) {
  Box(
    modifier =
      modifier
        .background(
          foreground10 = WalletTheme.colors.foreground10,
          type = model.iconBackgroundType
        ).thenIf(model.iconBackgroundType is Circle) {
          Modifier.size(model.totalSize.dp)
        },
    contentAlignment = Alignment.Center
  ) {
    when (val image = model.iconImage) {
      is LocalImage ->
        MaterialIcon(
          modifier = Modifier.size(model.iconSize.dp),
          painter = image.icon.painter(),
          contentDescription = model.text,
          tint = style.color
        )

      is UrlImage -> {
        val asyncImagePainter =
          rememberAsyncImagePainter(
            model =
              ImageRequest.Builder(LocalContext.current)
                .data(image.url)
                .size(
                  Size(
                    width = model.iconSize.dp.value.toInt(),
                    height = model.iconSize.dp.value.toInt()
                  )
                )
                .build()
          )

        when (asyncImagePainter.state) {
          is Empty,
          is Loading,
          ->
            LoadingIndicator(
              modifier = Modifier.size(model.iconSize.dp),
              color = style.color
            )

          is Success ->
            MaterialIcon(
              modifier = Modifier.size(model.iconSize.dp),
              painter = asyncImagePainter,
              contentDescription = model.text,
              tint = style.color
            )

          is State.Error ->
            MaterialIcon(
              modifier = Modifier.size(model.iconSize.dp),
              painter = image.fallbackIcon.painter(),
              contentDescription = model.text,
              tint = style.color
            )
        }
      }

      is IconImage.Loader ->
        LoadingIndicator(
          modifier = Modifier.size(model.iconSize.dp),
          color = style.color
        )
    }
  }
}

private fun Modifier.background(
  foreground10: Color,
  type: IconBackgroundType,
): Modifier {
  return when (type) {
    Transient ->
      this

    is Circle ->
      this.then(
        background(
          color =
            when (type.color) {
              Circle.CircleColor.Foreground10 -> foreground10
              Circle.CircleColor.TranslucentBlack -> Color.Black.copy(alpha = .1f)
              Circle.CircleColor.TranslucentWhite -> Color.White.copy(alpha = .2f)
            },
          shape = CircleShape
        )
      )
  }
}

@Preview
@Composable
internal fun IconImageFromUrlPreview() {
  IconImage(
    iconImage =
      UrlImage(
        url = "https://upload.wikimedia.org/wikipedia/commons/c/c5/Square_Cash_app_logo.svg",
        fallbackIcon = Bitcoin
      ),
    size = Small
  )
}

@Preview
@Composable
internal fun IconsSmallPreview() {
  IconsForPreview(size = Small)
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
  return Icon.entries.filter {
    it != BuyOwnBitkeyHero
  }.toList()
}
