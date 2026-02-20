package build.wallet.ui.components.icon

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.ui.model.icon.*
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.market.MarketIcon
import build.wallet.ui.tokens.market.MarketIcons
import build.wallet.ui.tokens.market.painter
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun IconsSmallPreview() {
  IconsForPreview(size = IconSize.Small)
}

@Preview
@Composable
fun IconsRegularPreview() {
  IconsForPreview(size = IconSize.Regular)
}

@Preview
@Composable
fun IconsLargePreview() {
  IconsForPreview(size = IconSize.Large)
}

@Preview
@Composable
fun IconsTintedPreview() {
  IconsForPreview(
    size = IconSize.Regular,
    color = WalletTheme.colors.warningForeground
  )
}

@Preview
@Composable
fun IconsAvatarPreview() {
  IconsForPreview(size = IconSize.Avatar)
}

@Composable
fun IconsForPreview(
  size: IconSize,
  color: Color = Color.Unspecified,
) {
  PreviewWalletTheme {
    IconGrid(
      size = size,
      color = color
    )
  }
}

@Composable
fun IconGrid(
  size: IconSize,
  color: Color = Color.Unspecified,
) {
  Box(
    modifier = Modifier.background(WalletTheme.colors.background)
  ) {
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

@Composable
fun MarketIconGrid(
  size: IconSize,
  color: Color = Color.Unspecified,
) {
  Box(
    modifier = Modifier.background(WalletTheme.colors.background)
  ) {
    LazyVerticalGrid(
      columns = GridCells.Adaptive(50.dp)
    ) {
      items(marketIconsToPreview(size)) { marketIcon ->
        Image(
          modifier = Modifier
            .padding(5.dp)
            .size(size.dp),
          painter = marketIcon.painter(),
          contentDescription = null,
          colorFilter = if (color != Color.Unspecified && !marketIcon.multiColor) {
            ColorFilter.tint(color)
          } else {
            null
          }
        )
      }
    }
  }
}

private fun iconsToPreview(): List<Icon> {
  // Filter shared icon definitions that shouldn't be snapshot tested
  return Icon.entries
    .filter {
      it != Icon.BuyOwnBitkeyHero && it != Icon.CalloutArrow
    }.toList()
}

/**
 * Returns MarketIcons for preview/snapshot testing.
 *
 * Some token sets no longer encode size in the field name. If a size-specific
 * subset isn't found, fall back to all icons to avoid empty grids.
 */
fun marketIconsToPreview(size: IconSize): List<MarketIcon> {
  val desiredSize =
    when (size) {
      IconSize.Small -> 16
      IconSize.Regular -> 24
      IconSize.Large -> 32
      else -> 24
    }

  val iconFields =
    MarketIcons::class.java.declaredFields
      .filter { field -> field.type == MarketIcon::class.java }
      .sortedBy { it.name }
      .onEach { field -> field.isAccessible = true }

  val allIcons = iconFields.map { field ->
    field.get(null) as MarketIcon
  }

  val sizedIcons =
    iconFields
      .filter { field -> field.name.endsWith(desiredSize.toString()) }
      .map { field -> field.get(null) as MarketIcon }

  return if (sizedIcons.isNotEmpty()) sizedIcons else allIcons
}
