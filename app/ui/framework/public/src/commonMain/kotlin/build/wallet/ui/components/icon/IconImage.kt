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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
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
import build.wallet.ui.tokens.colors
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
  when (type) {
    Transient -> this
    is Circle, is Square -> this.then(IconBackgroundElement(type))
  }

private data class IconBackgroundElement(
  val type: IconBackgroundType,
) : ModifierNodeElement<IconBackgroundNode>() {
  override fun create() = IconBackgroundNode(type)

  override fun update(node: IconBackgroundNode) = node.update(type)
}

private class IconBackgroundNode(
  private var type: IconBackgroundType,
) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
  fun update(type: IconBackgroundType) {
    this.type = type
    invalidateDraw()
  }

  override fun ContentDrawScope.draw() {
    // Resolve the theme at draw time so theme switches pick up fresh colors.
    val theme = currentValueOf(LocalTheme)
    when (val backgroundType = type) {
      Transient -> Unit
      is Circle -> drawOutline(
        outline = CircleShape.createOutline(size, layoutDirection, this),
        color = backgroundType.color.toComposeColor(theme)
      )
      is Square -> drawOutline(
        outline = RoundedCornerShape(backgroundType.cornerRadius)
          .createOutline(size, layoutDirection, this),
        color = backgroundType.color.toComposeColor(theme)
      )
    }
    drawContent()
  }
}

private fun Circle.CircleColor.toComposeColor(theme: Theme): Color {
  val colors = theme.colors()
  return when (this) {
    Circle.CircleColor.Foreground10 ->
      if (theme == Theme.LIGHT) colors.secondary else colors.foreground10
    Circle.CircleColor.SubtleBackground -> colors.subtleBackground
    Circle.CircleColor.PrimaryBackground20 -> colors.bitkeyPrimary.copy(alpha = .2f)
    Circle.CircleColor.InverseBackground -> colors.inverseBackground
    Circle.CircleColor.TranslucentBlack -> Color.Black.copy(alpha = .1f)
    Circle.CircleColor.TranslucentWhite -> Color.White.copy(alpha = .2f)
    Circle.CircleColor.Information -> colors.calloutInformationTrailingIconBackground.copy(alpha = .25f)
    Circle.CircleColor.InheritanceSurface -> colors.inheritanceSurface
    Circle.CircleColor.Dark -> colors.accentDarkBackground
    Circle.CircleColor.Primary -> colors.primaryIconBackground
    Circle.CircleColor.Hero ->
      if (theme == Theme.LIGHT) colors.inverseBackground else colors.primaryIconBackground
    Circle.CircleColor.BitkeyPrimary -> colors.bitkeyPrimary
    Circle.CircleColor.TransparentForeground -> colors.foreground.copy(alpha = .2f)
    Circle.CircleColor.Secondary -> colors.secondary
  }
}

private fun Square.Color.toComposeColor(theme: Theme): Color {
  val colors = theme.colors()
  return when (this) {
    Square.Color.Default -> colors.calloutDefaultTrailingIconBackground
    Square.Color.Information -> colors.calloutInformationTrailingIconBackground
    Square.Color.Success -> colors.calloutSuccessTrailingIconBackground
    Square.Color.Warning -> colors.calloutWarningTrailingIconBackground
    Square.Color.Danger -> colors.danger
    Square.Color.InverseBackground -> colors.inverseBackground
    Square.Color.White -> colors.subtleBackground
    Square.Color.Transparent -> Color.Transparent
  }
}
