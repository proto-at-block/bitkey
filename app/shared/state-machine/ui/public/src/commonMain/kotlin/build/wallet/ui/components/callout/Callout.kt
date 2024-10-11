package build.wallet.ui.components.callout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bitkey.shared.ui_core_public.generated.resources.Res
import bitkey.shared.ui_core_public.generated.resources.inter_medium
import bitkey.shared.ui_core_public.generated.resources.inter_regular
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.ui.components.icon.IconButton
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.Label
import build.wallet.ui.model.Click
import build.wallet.ui.model.callout.CalloutModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tooling.PreviewWalletTheme
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A callout is a component that displays a title, leading icon (optional), subtitle, trailing icon (optional)
 * It has 5 treatments: Default, Information, Success, Warning, Danger (see CalloutModel.kt)
 * https://www.figma.com/file/ZFPzTqbSeZliQBu8T7CUSc/%F0%9F%94%91-Bitkey-Design-System?type=design&node-id=72-21181
 */
@Composable
fun Callout(model: CalloutModel) {
  val style = model.calloutStyle()
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(
        color = style.backgroundColor,
        shape = RoundedCornerShape(size = 16.dp)
      ),
    contentAlignment = Alignment.CenterStart
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Start
    ) {
      model.leadingIcon?.let { icon ->
        IconImage(
          modifier = Modifier
            .padding(end = 12.dp),
          model = IconModel(
            icon = icon,
            iconSize = IconSize.Accessory
          ),
          color = style.titleColor
        )
      }
      Column(
        modifier = Modifier
          .weight(1f),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
      ) {
        model.title?.let { title ->
          Label(
            text = title,
            style = TextStyle(
              fontSize = 16.sp,
              lineHeight = 24.sp,
              fontFamily = FontFamily(Font(Res.font.inter_medium)),
              fontWeight = FontWeight(500),
              color = style.titleColor
            )
          )
        }

        model.subtitle?.let { subtitle ->
          Label(
            model = subtitle,
            modifier = Modifier
              .padding(top = 4.dp)
              .alpha(0.6f),
            style = TextStyle(
              fontSize = 16.sp,
              lineHeight = 24.sp,
              fontFamily = FontFamily(Font(Res.font.inter_regular)),
              fontWeight = FontWeight(400),
              color = style.subtitleColor
            )
          )
        }
      }
      model.trailingIcon?.let { trailingIcon ->
        CalloutButton(
          trailingIcon,
          style.trailingIconColor,
          model.treatment,
          model.onClick
        )
      }
    }
  }
}

/**
 * Trailing icon button for callout
 */
@Composable
private fun CalloutButton(
  icon: Icon,
  iconColor: Color,
  treatment: CalloutModel.Treatment,
  onClick: Click?,
) {
  IconButton(
    modifier = Modifier
      .padding(start = 12.dp, end = 0.dp),
    iconModel = IconModel(
      icon = icon,
      iconSize = IconSize.Accessory,
      iconBackgroundType = IconBackgroundType.Square(
        size = IconSize.Large,
        color = when (treatment) {
          CalloutModel.Treatment.Default -> IconBackgroundType.Square.Color.Default
          CalloutModel.Treatment.Information -> IconBackgroundType.Square.Color.Information
          CalloutModel.Treatment.Success -> IconBackgroundType.Square.Color.Success
          CalloutModel.Treatment.Warning -> IconBackgroundType.Square.Color.Warning
          CalloutModel.Treatment.Danger -> IconBackgroundType.Square.Color.Danger
        },
        cornerRadius = 12
      )
    ),
    color = iconColor,
    onClick = {
      onClick?.invoke()
    }
  )
}

/**
 * Standard theme variables for a callout treatment
 */
data class CalloutStyle(
  val titleColor: Color,
  val subtitleColor: Color,
  val backgroundColor: Color,
  val trailingIconColor: Color,
  val trailingIconBackgroundColor: Color,
)

@Composable
@ReadOnlyComposable
private fun CalloutModel.calloutStyle() =
  when (treatment) {
    CalloutModel.Treatment.Default -> CalloutStyle(
      titleColor = WalletTheme.colors.calloutDefaultTitle,
      subtitleColor = WalletTheme.colors.calloutDefaultSubtitle,
      backgroundColor = WalletTheme.colors.calloutDefaultBackground,
      trailingIconColor = WalletTheme.colors.calloutDefaultTrailingIcon,
      trailingIconBackgroundColor = WalletTheme.colors.calloutDefaultTrailingIconBackground
    )
    CalloutModel.Treatment.Information -> CalloutStyle(
      titleColor = WalletTheme.colors.calloutInformationTitle,
      subtitleColor = WalletTheme.colors.calloutInformationSubtitle,
      backgroundColor = WalletTheme.colors.calloutInformationBackground,
      trailingIconColor = WalletTheme.colors.calloutInformationTrailingIcon,
      trailingIconBackgroundColor = WalletTheme.colors.calloutInformationTrailingIconBackground
    )
    CalloutModel.Treatment.Success -> CalloutStyle(
      titleColor = WalletTheme.colors.calloutSuccessTitle,
      subtitleColor = WalletTheme.colors.calloutSuccessSubtitle,
      backgroundColor = WalletTheme.colors.calloutSuccessBackground,
      trailingIconColor = WalletTheme.colors.calloutSuccessTrailingIcon,
      trailingIconBackgroundColor = WalletTheme.colors.calloutSuccessTrailingIconBackground
    )
    CalloutModel.Treatment.Warning -> CalloutStyle(
      titleColor = WalletTheme.colors.calloutWarningTitle,
      subtitleColor = WalletTheme.colors.calloutWarningSubtitle,
      backgroundColor = WalletTheme.colors.calloutWarningBackground,
      trailingIconColor = WalletTheme.colors.calloutWarningTrailingIcon,
      trailingIconBackgroundColor = WalletTheme.colors.calloutWarningTrailingIconBackground
    )
    CalloutModel.Treatment.Danger -> CalloutStyle(
      titleColor = WalletTheme.colors.calloutDangerTitle,
      subtitleColor = WalletTheme.colors.calloutDangerSubtitle,
      backgroundColor = WalletTheme.colors.dangerBackground,
      trailingIconColor = WalletTheme.colors.calloutDangerTrailingIcon,
      trailingIconBackgroundColor = WalletTheme.colors.danger
    )
  }

@Preview
@Composable
fun CalloutPreviews() {
  val multipleLines = listOf(false, true)
  val treatments = CalloutModel.Treatment.entries
  val configs = treatments.flatMap { treatment ->
    multipleLines.map { multipleLines ->
      CalloutPreviewConfig(
        multipleLines = multipleLines,
        treatment = treatment
      )
    }
  }

  PreviewWalletTheme {
    LazyVerticalGrid(
      modifier = Modifier
        .fillMaxWidth()
        .background(color = Color.White)
        .padding(all = 10.dp),
      columns = GridCells.Fixed(2),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      items(configs) { config ->
        Callout(
          model = CalloutModel(
            title = "Title",
            subtitle = when (config.multipleLines) {
              true -> StringModel("Subtitle line one\nSubtitle line two")
              false -> StringModel("Subtitle")
            },
            treatment = config.treatment,
            leadingIcon = Icon.SmallIconCheck,
            trailingIcon = Icon.SmallIconArrowRight
          )
        )
      }
    }
  }
}

private data class CalloutPreviewConfig(
  val multipleLines: Boolean,
  val treatment: CalloutModel.Treatment,
)
