package build.wallet.ui.components.icon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.label.Label
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import io.kotest.core.spec.style.FunSpec

/**
 * Snapshot tests for IconImage tint behavior.
 *
 * These tests verify that the color/tint parameter is correctly applied
 * to icons rendered via IconImage. This covers the fix for the partner logo
 * white background regression where imageTint was not being passed through
 * to UrlImage.
 *
 * Note: Uses LocalImage since UrlImage URLs don't load in Paparazzi tests.
 * The tinting code path is the same for both image types after the fix.
 */
class UrlImageSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("icon image tint variations") {
    paparazzi.snapshot {
      IconTintVariations()
    }
  }

  test("icon image with backgrounds and tints") {
    paparazzi.snapshot {
      IconWithBackgroundsAndTints()
    }
  }
})

@Composable
private fun IconTintVariations() {
  Column(
    modifier = Modifier
      .background(WalletTheme.colors.background)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Label(text = "IconImage Tint Variations", type = LabelType.Title2)

    Row(
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // No tint (default)
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconImage(
          model = IconModel(
            iconImage = IconImage.LocalImage(Icon.SmallIconBitcoinStroked),
            iconSize = IconSize.Large
          )
        )
        Label(text = "Default", type = LabelType.Body3Regular)
      }

      // Primary tint
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconImage(
          model = IconModel(
            iconImage = IconImage.LocalImage(Icon.SmallIconBitcoinStroked),
            iconSize = IconSize.Large,
            iconTint = IconTint.Primary
          )
        )
        Label(text = "Primary", type = LabelType.Body3Regular)
      }

      // Foreground tint
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconImage(
          model = IconModel(
            iconImage = IconImage.LocalImage(Icon.SmallIconBitcoinStroked),
            iconSize = IconSize.Large,
            iconTint = IconTint.Foreground
          )
        )
        Label(text = "Foreground", type = LabelType.Body3Regular)
      }

      // Warning tint
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconImage(
          model = IconModel(
            iconImage = IconImage.LocalImage(Icon.SmallIconBitcoinStroked),
            iconSize = IconSize.Large,
            iconTint = IconTint.Warning
          )
        )
        Label(text = "Warning", type = LabelType.Body3Regular)
      }

      // Custom color override
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconImage(
          model = IconModel(
            iconImage = IconImage.LocalImage(Icon.SmallIconBitcoinStroked),
            iconSize = IconSize.Large
          ),
          color = Color.Red
        )
        Label(text = "Custom", type = LabelType.Body3Regular)
      }
    }
  }
}

@Composable
private fun IconWithBackgroundsAndTints() {
  Column(
    modifier = Modifier
      .background(WalletTheme.colors.background)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Label(text = "Icon with Backgrounds", type = LabelType.Title2)

    Row(
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Circle background, no tint
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconImage(
          model = IconModel(
            iconImage = IconImage.LocalImage(Icon.SmallIconBitcoinStroked),
            iconSize = IconSize.Small,
            iconBackgroundType = IconBackgroundType.Circle(
              circleSize = IconSize.Avatar,
              color = IconBackgroundType.Circle.CircleColor.Foreground10
            )
          )
        )
        Label(text = "Circle", type = LabelType.Body3Regular)
      }

      // Square background with primary tint
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconImage(
          model = IconModel(
            iconImage = IconImage.LocalImage(Icon.SmallIconBitcoinStroked),
            iconSize = IconSize.Small,
            iconBackgroundType = IconBackgroundType.Square(
              size = IconSize.Avatar,
              color = IconBackgroundType.Square.Color.Information,
              cornerRadius = 12
            ),
            iconTint = IconTint.Primary
          )
        )
        Label(text = "Square+Tint", type = LabelType.Body3Regular)
      }

      // Transient background (like partner logos)
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconImage(
          model = IconModel(
            iconImage = IconImage.LocalImage(Icon.SmallIconBitcoinStroked),
            iconSize = IconSize.Large,
            iconBackgroundType = IconBackgroundType.Square(
              size = IconSize.Avatar,
              color = IconBackgroundType.Square.Color.Transparent,
              cornerRadius = 0
            )
          )
        )
        Label(text = "Transparent", type = LabelType.Body3Regular)
      }
    }
  }
}
