package build.wallet.ui.model.icon

import build.wallet.statemachine.core.Icon
import build.wallet.ui.model.icon.IconBackgroundType.Circle
import build.wallet.ui.model.icon.IconBackgroundType.Transient
import build.wallet.ui.model.icon.IconImage.LocalImage
import build.wallet.ui.model.icon.IconImage.UrlImage
import build.wallet.ui.model.icon.IconSize.Regular

sealed class IconImage {
  /**
   * Represents images generated from "app/style/tokens/icons.json"
   * @property - Refers to [Icon] that represents a saved local image
   */
  data class LocalImage(
    val icon: Icon,
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
}

/**
 * Used to define behavior for an `IconImage`
 */
data class IconModel(
  val iconImage: IconImage,
  val iconSize: IconSize,
  val iconBackgroundType: IconBackgroundType = Transient,
  val iconTint: IconTint? = null,
  val iconOpacity: Float? = null,
  val iconTopSpacing: Int? = null,
  val text: String? = null,
) {
  val totalSize: IconSize
    get() =
      when (iconBackgroundType) {
        is Circle -> iconBackgroundType.circleSize
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

/**
 * Semantic type that describes size of an icon button.
 */
enum class IconSize {
  XSmall,
  Small,
  Regular,
  Large,
  XLarge,
  Avatar,
  Accessory,
  Keypad,
  HeaderToolbar,
  ;

  val value: Int
    get() =
      when (this) {
        XSmall -> 12
        Small -> 24
        Regular -> 32
        Large -> 40
        XLarge -> 80
        Avatar -> 64
        Accessory -> 20
        Keypad -> 30
        HeaderToolbar -> 28
      }
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
    }
  }
}

/**
 * A Color tint representation to be applied to the icon
 */
enum class IconTint {
  Primary,
  Foreground,
  On60,
  On30,
  Destructive,
  OutOfDate,
  OnTranslucent,
  Green,
}
