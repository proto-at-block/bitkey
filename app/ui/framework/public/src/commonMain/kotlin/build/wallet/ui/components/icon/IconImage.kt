package build.wallet.ui.components.icon

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.loading.LoadingBadge
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.icon.*
import build.wallet.ui.model.icon.IconBackgroundType.*
import build.wallet.ui.model.icon.IconImage.*
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.painter
import org.jetbrains.compose.resources.painterResource

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
  IconImage(
    modifier = modifier,
    model = IconModel(
      icon = icon,
      iconSize = size,
      iconTint = tint,
      iconOpacity = opacity
    ),
    color = color,
    text = text
  )
}

@Composable
fun IconImage(
  model: IconModel,
  modifier: Modifier = Modifier,
  color: Color = Color.Unspecified,
  text: String? = null,
) {
  val style = WalletTheme.iconStyle(
    icon = model.iconImage,
    color = color,
    tint = model.iconTint
  )
  Box(
    modifier = modifier
      .iconBackground(model.iconBackgroundType)
      .thenIf(model.iconBackgroundType !is Transient) {
        Modifier.size(model.totalSize.dp)
      },
    contentAlignment = model.iconAlignmentInBackground.toAlignment()
  ) {
    Box {
      IconImageContent(model = model, style = style, text = text)
      IconBadge(badge = model.badge, styleColor = style.color)
    }
  }
}

@Composable
private fun IconImageContent(
  model: IconModel,
  style: IconStyle,
  text: String?,
) {
  val tint = style.color.tintOrNull()
  val alpha = model.iconOpacity ?: 1f
  val contentDescription = text ?: model.text

  when (val image = model.iconImage) {
    is LocalImage -> Image(
      modifier = Modifier.size(model.iconSize.dp).alpha(alpha),
      painter = image.icon.painter(),
      contentDescription = contentDescription,
      colorFilter = tint
    )
    is DrawableResourceImage -> Image(
      modifier = Modifier.size(model.iconSize.dp).alpha(alpha),
      painter = painterResource(image.resource),
      contentDescription = contentDescription,
      colorFilter = tint
    )
    is MarketIconImage -> Image(
      modifier = Modifier.size(model.iconSize.dp).alpha(alpha),
      painter = painterResource(image.icon.resource),
      contentDescription = contentDescription,
      colorFilter = if (image.icon.multiColor) null else tint
    )
    is UrlImage -> UrlImage(
      image = image,
      iconSize = model.iconSize,
      imageAlpha = model.iconOpacity,
      contentDescription = contentDescription,
      imageTint = if (style.color != Color.Unspecified) style.color else null
    )
    LoadingBadge -> LoadingBadge(
      modifier = Modifier.size(model.iconSize.dp).alpha(alpha),
      color = if (style.color != Color.Unspecified) style.color else WalletTheme.colors.foreground
    )
  }
}

private fun Color.tintOrNull(): ColorFilter? =
  if (this != Color.Unspecified) ColorFilter.tint(this) else null

@Composable
private fun BoxScope.IconBadge(
  badge: BadgeType?,
  styleColor: Color,
) {
  when (badge) {
    BadgeType.Loading -> {
      BadgeBackground()
      LoadingBadge(
        modifier = Modifier.padding(bottom = 5.dp, end = 5.dp)
          .size(IconSize.XSmall.dp)
          .align(Alignment.BottomEnd)
      )
    }
    BadgeType.Error -> {
      BadgeBackground()
      Image(
        modifier = Modifier.padding(bottom = 3.dp, end = 3.dp)
          .size(16.dp)
          .align(Alignment.BottomEnd),
        painter = Icon.WarningBadge.painter(),
        contentDescription = null,
        colorFilter = ColorFilter.tint(
          if (styleColor != Color.Unspecified) {
            styleColor
          } else {
            WalletTheme.colors.foreground
          }
        )
      )
    }
    null -> Unit
  }
}

@Composable
private fun BoxScope.BadgeBackground() {
  val backgroundColor = when (LocalTheme.current) {
    Theme.LIGHT -> WalletTheme.colors.background
    Theme.DARK -> WalletTheme.colors.primaryIconBackground
  }

  Box(
    modifier = Modifier.padding(bottom = 1.dp, end = 1.dp)
      .size(IconSize.Accessory.dp)
      .align(Alignment.BottomEnd)
      .background(backgroundColor, CircleShape)
  )
}

private fun IconAlignmentInBackground.toAlignment(): Alignment =
  when (this) {
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

private fun Modifier.iconBackground(type: IconBackgroundType): Modifier =
  composed {
    when (type) {
      Transient -> this
      is Circle -> background(type.color.toComposeColor(), CircleShape)
      is Square -> background(type.color.toComposeColor(), RoundedCornerShape(type.cornerRadius))
    }
  }

@Composable
private fun Circle.CircleColor.toComposeColor(): Color =
  when (this) {
    Circle.CircleColor.Foreground10 ->
      if (LocalTheme.current == Theme.LIGHT) WalletTheme.colors.secondary else WalletTheme.colors.foreground10
    Circle.CircleColor.SubtleBackground -> WalletTheme.colors.subtleBackground
    Circle.CircleColor.PrimaryBackground20 -> WalletTheme.colors.bitkeyPrimary.copy(alpha = .2f)
    Circle.CircleColor.InverseBackground -> WalletTheme.colors.inverseBackground
    Circle.CircleColor.TranslucentBlack -> Color.Black.copy(alpha = .1f)
    Circle.CircleColor.TranslucentWhite -> Color.White.copy(alpha = .2f)
    Circle.CircleColor.Information -> WalletTheme.colors.calloutInformationTrailingIconBackground.copy(alpha = .25f)
    Circle.CircleColor.InheritanceSurface -> WalletTheme.colors.inheritanceSurface
    Circle.CircleColor.Dark -> WalletTheme.colors.accentDarkBackground
    Circle.CircleColor.Primary -> WalletTheme.colors.primaryIconBackground
    Circle.CircleColor.Hero ->
      if (LocalTheme.current == Theme.LIGHT) WalletTheme.colors.inverseBackground else WalletTheme.colors.primaryIconBackground
    Circle.CircleColor.BitkeyPrimary -> WalletTheme.colors.bitkeyPrimary
    Circle.CircleColor.TransparentForeground -> WalletTheme.colors.foreground.copy(alpha = .2f)
    Circle.CircleColor.Secondary -> WalletTheme.colors.secondary
  }

@Composable
private fun Square.Color.toComposeColor(): Color =
  when (this) {
    Square.Color.Default -> WalletTheme.colors.calloutDefaultTrailingIconBackground
    Square.Color.Information -> WalletTheme.colors.calloutInformationTrailingIconBackground
    Square.Color.Success -> WalletTheme.colors.calloutSuccessTrailingIconBackground
    Square.Color.Warning -> WalletTheme.colors.calloutWarningTrailingIconBackground
    Square.Color.Danger -> WalletTheme.colors.danger
    Square.Color.InverseBackground -> WalletTheme.colors.inverseBackground
    Square.Color.White -> WalletTheme.colors.subtleBackground
    Square.Color.Transparent -> Color.Transparent
  }
