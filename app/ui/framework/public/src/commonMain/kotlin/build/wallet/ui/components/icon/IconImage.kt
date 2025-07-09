@file:Suppress("TooManyFunctions")

package build.wallet.ui.components.icon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.loading.LoadingBadge
import build.wallet.ui.components.loading.LoadingBadgePainter
import build.wallet.ui.components.loading.LoadingIndicator
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.icon.*
import build.wallet.ui.model.icon.IconBackgroundType.*
import build.wallet.ui.model.icon.IconImage.LocalImage
import build.wallet.ui.model.icon.IconImage.UrlImage
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.painter

@Composable
fun Icon(
  modifier: Modifier = Modifier,
  icon: Icon,
  size: IconSize,
  color: Color = Color.Unspecified,
  tint: IconTint? = null,
  opacity: Float? = null,
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
        iconAlignmentInBackground = IconAlignmentInBackground.Center,
        text = text,
        iconOpacity = opacity
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
      opacity = iconOpacity,
      text = text,
      iconAlignmentInBackground = iconAlignmentInBackground,
      badgeType = model.badge
    )
  }
}

@Composable
fun IconImage(
  modifier: Modifier = Modifier,
  iconImage: IconImage,
  size: IconSize,
  background: IconBackgroundType = Transient,
  iconAlignmentInBackground: IconAlignmentInBackground = IconAlignmentInBackground.Center,
  color: Color = Color.Unspecified,
  tint: IconTint? = null,
  opacity: Float? = null,
  text: String? = null,
  badgeType: BadgeType? = null,
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
        text = text,
        iconOpacity = opacity,
        iconAlignmentInBackground = iconAlignmentInBackground,
        badge = badgeType
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
          primary = WalletTheme.colors.bitkeyPrimary,
          type = model.iconBackgroundType
        ).thenIf(model.iconBackgroundType is Circle || model.iconBackgroundType is Square) {
          Modifier.size(model.totalSize.dp)
        },
    contentAlignment = when (model.iconAlignmentInBackground) {
      IconAlignmentInBackground.TopStart -> Alignment.TopStart
      IconAlignmentInBackground.TopCenter -> Alignment.TopCenter
      IconAlignmentInBackground.TopEnd -> Alignment.TopEnd
      IconAlignmentInBackground.Start -> Alignment.CenterStart
      IconAlignmentInBackground.Center -> Alignment.Center
      IconAlignmentInBackground.End -> Alignment.CenterEnd
      IconAlignmentInBackground.BottomStart -> Alignment.BottomStart
      IconAlignmentInBackground.BottomCenter -> Alignment.BottomCenter
      IconAlignmentInBackground.BottomEnd -> Alignment.BottomEnd
    }
  ) {
    Box {
      when (val image = model.iconImage) {
        is LocalImage ->
          Icon(
            modifier = Modifier.size(model.iconSize.dp).alpha(model.iconOpacity ?: 1f),
            painter = image.icon.painter(),
            contentDescription = model.text,
            tint = style.color
          )

        is UrlImage ->
          UrlImage(
            image = image,
            iconSize = model.iconSize,
            imageAlpha = model.iconOpacity,
            contentDescription = model.text
          )

        is IconImage.Loader ->
          LoadingIndicator(
            modifier = Modifier.size(model.iconSize.dp),
            color = style.color
          )
        IconImage.LoadingBadge -> LoadingBadgePainter()
      }

      when (model.badge) {
        BadgeType.Loading -> LoadingBadge(
          modifier = Modifier.padding(bottom = 6.dp, end = 6.dp)
            .size(IconSize.XSmall.dp)
            .align(Alignment.BottomEnd)
        )
        BadgeType.Error -> Icon(
          modifier = Modifier.padding(bottom = 4.dp, end = 4.dp)
            .align(Alignment.BottomEnd),
          painter = Icon.WarningBadge.painter(),
          contentDescription = null,
          tint = style.color
        )
        null -> {}
      }
    }
  }
}

private fun Modifier.background(
  foreground10: Color,
  primary: Color,
  type: IconBackgroundType,
): Modifier =
  composed {
    when (type) {
      Transient ->
        this

      is Circle ->
        this.then(
          background(
            color =
              when (type.color) {
                Circle.CircleColor.Foreground10 -> foreground10
                Circle.CircleColor.PrimaryBackground20 -> primary.copy(alpha = .2f)
                Circle.CircleColor.TranslucentBlack -> Color.Black.copy(alpha = .1f)
                Circle.CircleColor.TranslucentWhite -> Color.White.copy(alpha = .2f)
                Circle.CircleColor.Information -> WalletTheme.colors.calloutInformationTrailingIconBackground.copy(alpha = .25f)
                Circle.CircleColor.InheritanceSurface -> WalletTheme.colors.inheritanceSurface
                Circle.CircleColor.Dark -> WalletTheme.colors.accentDarkBackground
                Circle.CircleColor.Primary -> WalletTheme.colors.primaryIconBackground
                Circle.CircleColor.BitkeyPrimary -> WalletTheme.colors.bitkeyPrimary
                Circle.CircleColor.TransparentForeground -> WalletTheme.colors.foreground.copy(alpha = .2f)
              },
            shape = CircleShape
          )
        )
      is Square ->
        this.then(
          background(
            when (type.color) {
              Square.Color.Default -> WalletTheme.colors.calloutDefaultTrailingIconBackground
              Square.Color.Information -> WalletTheme.colors.calloutInformationTrailingIconBackground
              Square.Color.Success -> WalletTheme.colors.calloutSuccessTrailingIconBackground
              Square.Color.Warning -> WalletTheme.colors.calloutWarningTrailingIconBackground
              Square.Color.Danger -> WalletTheme.colors.danger
              Square.Color.Transparent -> Color.Transparent
            },
            shape = RoundedCornerShape(type.cornerRadius)
          )
        )
    }
  }
