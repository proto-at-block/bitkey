package build.wallet.ui.model.icon

import build.wallet.statemachine.core.Icon
import build.wallet.ui.model.icon.IconBackgroundType.*
import build.wallet.ui.model.icon.IconImage.LocalImage
import build.wallet.ui.model.icon.IconImage.MarketIconImage
import build.wallet.ui.model.icon.IconImage.UrlImage
import build.wallet.ui.model.icon.IconSize.Regular
import build.wallet.ui.tokens.market.MarketIcon

sealed class IconImage {
  /**
   * Represents images from "app/ui/framework/public/src/commonMain/composeResources/drawable"
   * @property - Refers to [Icon] that represents a saved local image
   */
  data class LocalImage(
    val icon: Icon,
  ) : IconImage()

  /**
   * Represents icons sourced from the Market icon library.
   */
  data class MarketIconImage(
    val icon: MarketIcon,
  ) : IconImage()

  /**
   * Represents images that can be rendered via a URL
   * @property - url: the website that will generate the image in string form
   * @property - fallbackIcon: local [Icon] used when the `url` does not generate an image
   */
  data class UrlImage(
    val url: String,
    val fallbackIcon: Icon,
  ) : IconImage()

  /** Represents a loading indicator */
  data object Loader : IconImage()

  /** Represents a circular loading badge */
  data object LoadingBadge : IconImage()
}

/**
 * Used to define behavior for an `IconImage`
 */
data class IconModel(
  val iconImage: IconImage,
  val iconSize: IconSize,
  val iconBackgroundType: IconBackgroundType = Transient,
  val iconAlignmentInBackground: IconAlignmentInBackground = IconAlignmentInBackground.Center,
  val iconTint: IconTint? = null,
  val iconOpacity: Float? = null,
  val iconTopSpacing: Int? = null,
  val text: String? = null,
  val badge: BadgeType? = null,
) {
  val totalSize: IconSize
    get() =
      when (iconBackgroundType) {
        is Circle -> iconBackgroundType.circleSize
        is Square -> iconBackgroundType.size
        else -> iconSize
      }
}

fun IconModel(
  imageUrl: String,
  fallbackIcon: Icon,
  iconSize: IconSize = Regular,
  iconBackgroundType: IconBackgroundType = Transient,
  iconTint: IconTint? = null,
  iconOpacity: Float? = null,
  iconTopSpacing: Int? = null,
): IconModel {
  return IconModel(
    iconImage =
      UrlImage(
        url = imageUrl,
        fallbackIcon = fallbackIcon
      ),
    iconSize = iconSize,
    iconBackgroundType = iconBackgroundType,
    iconTint = iconTint,
    iconOpacity = iconOpacity,
    iconTopSpacing = iconTopSpacing
  )
}

fun IconModel(
  icon: Icon,
  iconSize: IconSize,
  iconBackgroundType: IconBackgroundType = Transient,
  iconTint: IconTint? = null,
  iconOpacity: Float? = null,
  iconTopSpacing: Int? = null,
): IconModel {
  return IconModel(
    iconImage = LocalImage(icon = icon),
    iconSize = iconSize,
    iconBackgroundType = iconBackgroundType,
    iconTint = iconTint,
    iconOpacity = iconOpacity,
    iconTopSpacing = iconTopSpacing
  )
}

fun IconModel(
  icon: MarketIcon,
  iconSize: IconSize,
  iconBackgroundType: IconBackgroundType = Transient,
  iconTint: IconTint? = null,
  iconOpacity: Float? = null,
  iconTopSpacing: Int? = null,
): IconModel {
  return IconModel(
    iconImage = MarketIconImage(icon = icon),
    iconSize = iconSize,
    iconBackgroundType = iconBackgroundType,
    iconTint = iconTint,
    iconOpacity = iconOpacity,
    iconTopSpacing = iconTopSpacing
  )
}

/**
 * Semantic type that describes size of an icon button.
 */
sealed class IconSize(open val value: Int) {
  data object XSmall : IconSize(12)

  data object Small : IconSize(24)

  data object Regular : IconSize(32)

  data object Medium : IconSize(36)

  data object Large : IconSize(40)

  data object XLarge : IconSize(80)

  data object XXXLarge : IconSize(250)

  data object Avatar : IconSize(64)

  data object AvatarLarge : IconSize(72)

  data object Accessory : IconSize(20)

  data object Keypad : IconSize(30)

  data object HeaderToolbar : IconSize(24)

  data object Subtract : IconSize(18)

  data class Custom(override val value: Int) : IconSize(value)
}

sealed interface IconBackgroundType {
  /**
   * Draws no background behind the icon.
   */
  data object Transient : IconBackgroundType

  /**
   * Draws a circle background behind the icon with padding.
   */
  data class Circle(
    val circleSize: IconSize,
    val color: CircleColor = CircleColor.Foreground10,
  ) : IconBackgroundType {
    enum class CircleColor {
      Foreground10,

      /** primary color with a .2 transparency */
      PrimaryBackground20,

      /** Black with a .1 transparency */
      TranslucentBlack,

      /** White with a .2 transparency */
      TranslucentWhite,

      /** Pale blue */
      Information,

      InheritanceSurface,

      Dark,

      Primary,

      BitkeyPrimary,

      TransparentForeground,

      Secondary,
    }
  }

  /**
   * Draws a square background behind the icon with padding.
   */
  data class Square(
    val size: IconSize,
    val color: Color,
    val cornerRadius: Int,
  ) : IconBackgroundType {
    enum class Color {
      Default,
      Information,
      Success,
      Warning,
      Danger,
      Transparent,
      White,
    }
  }
}

/**
 * A Color tint representation to be applied to the icon
 */
enum class IconTint {
  Primary,
  Foreground,
  Background,
  On60,
  On30,
  On10,
  Destructive,
  OutOfDate,
  OnTranslucent,
  Green,
  Warning,
  Success,
  Information,
  White,
}

enum class IconAlignmentInBackground {
  TopStart,
  TopCenter,
  TopEnd,
  Start,
  Center,
  End,
  BottomStart,
  BottomCenter,
  BottomEnd,
}

enum class BadgeType {
  Loading,
  Error,
}
