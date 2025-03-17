package build.wallet.ui.components.callout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.icon.IconButton
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.model.Click
import build.wallet.ui.model.callout.CalloutModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

/**
 * A callout is a component that displays a title, leading icon (optional), subtitle, trailing icon (optional)
 * It has 5 treatments: Default, Information, Success, Warning, Danger (see CalloutModel.kt)
 * https://www.figma.com/file/ZFPzTqbSeZliQBu8T7CUSc/%F0%9F%94%91-Bitkey-Design-System?type=design&node-id=72-21181
 */
@Composable
fun Callout(model: CalloutModel) {
  val style = model.calloutStyle()

  // Track the alignment of the leading icon, which varies based on the number of text lines in the
  // subtitle.
  var iconVerticalAlignment by remember { mutableStateOf(Alignment.CenterVertically) }

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
      verticalAlignment = iconVerticalAlignment,
      horizontalArrangement = Arrangement.Start
    ) {
      model.leadingIcon?.let { icon ->
        IconImage(
          modifier = Modifier
            // Adjust the height to match the line height of the title in case of top alignment
            .heightIn(min = 24.dp)
            .padding(end = 12.dp),
          model = IconModel(
            icon = icon,
            iconSize = IconSize.Accessory
          ),
          color = style.leadingIconColor
        )
      }
      Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = when (model.treatment) {
          CalloutModel.Treatment.DefaultCentered -> Alignment.CenterHorizontally
          else -> Alignment.Start
        },
        verticalArrangement = Arrangement.Center
      ) {
        model.title?.let { title ->
          Label(
            text = title,
            style = WalletTheme.labelStyle(
              type = LabelType.CalloutTitle,
              treatment = LabelTreatment.Unspecified,
              textColor = style.titleColor
            ),
            onClick = {
              model.onTitleClick?.invoke()
            }
          )
        }

        model.subtitle?.let { subtitle ->
          Label(
            model = subtitle,
            modifier = Modifier,
            style = WalletTheme.labelStyle(
              type = LabelType.CalloutSubtitle,
              treatment = LabelTreatment.Unspecified,
              textColor = style.subtitleColor
            ),
            onTextLayout = { textLayoutResult ->
              val isSubtitleMultiline = textLayoutResult.lineCount > 1
              iconVerticalAlignment = when {
                // If the subtitle is multiline, align the icon to the top of the row
                isSubtitleMultiline -> Alignment.Top
                // If the subtitle is single line, align the icon to the center of the row
                else -> Alignment.CenterVertically
              }
            }
          )
        }
      }
      model.trailingIcon?.let { trailingIcon ->
        Box(modifier = Modifier.align(Alignment.CenterVertically)) {
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
}

/**
 * Trailing icon button for callout
 */
@Composable
fun CalloutButton(
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
          CalloutModel.Treatment.Default,
          CalloutModel.Treatment.DefaultCentered,
          -> IconBackgroundType.Square.Color.Default
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
  val leadingIconColor: Color,
  val trailingIconColor: Color,
  val trailingIconBackgroundColor: Color,
)

@Composable
@ReadOnlyComposable
private fun CalloutModel.calloutStyle() =
  when (treatment) {
    CalloutModel.Treatment.Default,
    CalloutModel.Treatment.DefaultCentered,
    -> CalloutStyle(
      titleColor = WalletTheme.colors.calloutDefaultTitle,
      subtitleColor = WalletTheme.colors.calloutDefaultSubtitle,
      backgroundColor = WalletTheme.colors.calloutDefaultBackground,
      leadingIconColor = WalletTheme.colors.calloutDefaultTitle,
      trailingIconColor = WalletTheme.colors.calloutDefaultTrailingIcon,
      trailingIconBackgroundColor = WalletTheme.colors.calloutDefaultTrailingIconBackground
    )
    CalloutModel.Treatment.Information -> CalloutStyle(
      titleColor = WalletTheme.colors.calloutInformationTitle,
      subtitleColor = WalletTheme.colors.calloutInformationSubtitle,
      backgroundColor = WalletTheme.colors.calloutInformationBackground,
      leadingIconColor = WalletTheme.colors.calloutInformationLeadingIcon,
      trailingIconColor = WalletTheme.colors.calloutInformationTrailingIcon,
      trailingIconBackgroundColor = WalletTheme.colors.calloutInformationTrailingIconBackground
    )
    CalloutModel.Treatment.Success -> CalloutStyle(
      titleColor = WalletTheme.colors.calloutSuccessTitle,
      subtitleColor = WalletTheme.colors.calloutSuccessSubtitle,
      backgroundColor = WalletTheme.colors.calloutSuccessBackground,
      leadingIconColor = WalletTheme.colors.calloutSuccessTitle,
      trailingIconColor = WalletTheme.colors.calloutSuccessTrailingIcon,
      trailingIconBackgroundColor = WalletTheme.colors.calloutSuccessTrailingIconBackground
    )
    CalloutModel.Treatment.Warning -> CalloutStyle(
      titleColor = WalletTheme.colors.calloutWarningTitle,
      subtitleColor = WalletTheme.colors.calloutWarningSubtitle,
      backgroundColor = WalletTheme.colors.calloutWarningBackground,
      leadingIconColor = WalletTheme.colors.calloutWarningTitle,
      trailingIconColor = WalletTheme.colors.calloutWarningTrailingIcon,
      trailingIconBackgroundColor = WalletTheme.colors.calloutWarningTrailingIconBackground
    )
    CalloutModel.Treatment.Danger -> CalloutStyle(
      titleColor = WalletTheme.colors.calloutDangerTitle,
      subtitleColor = WalletTheme.colors.calloutDangerSubtitle,
      backgroundColor = WalletTheme.colors.calloutDangerBackground,
      leadingIconColor = WalletTheme.colors.danger,
      trailingIconColor = WalletTheme.colors.calloutDangerTrailingIcon,
      trailingIconBackgroundColor = WalletTheme.colors.danger
    )
  }
