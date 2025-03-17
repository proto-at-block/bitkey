package build.wallet.ui.components.button

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.label.buttonTextStyle
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.button.ButtonModel.Size.FitContent
import build.wallet.ui.model.button.ButtonModel.Size.Floating
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Size.Regular
import build.wallet.ui.model.button.ButtonModel.Size.Short
import build.wallet.ui.model.button.ButtonModel.Treatment.*
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.theme.WalletTheme.colors
import build.wallet.ui.tokens.LabelType

/**
 * Styling configuration for a [Button].
 *
 * [textStyle] - styling configuration for a text content inside the button.
 */
data class ButtonStyle(
  val textStyle: TextStyle,
  val shape: Shape,
  val isTextButton: Boolean,
  val iconColor: Color,
  val iconSize: IconSize,
  val backgroundColor: Color,
  val minWidth: Dp,
  val height: Dp?,
  val fillWidth: Boolean,
  val verticalPadding: Dp,
  val horizontalPadding: Dp,
)

/**
 * Creates [ButtonStyle] using current [WalletTheme] values and provided button properties.
 */
@Composable
fun WalletTheme.buttonStyle(
  treatment: ButtonModel.Treatment,
  size: ButtonModel.Size,
  cornerRadius: Dp = 16.dp,
  enabled: Boolean = true,
): ButtonStyle {
  val textColor = textColor(treatment = treatment)
  val textStyle =
    buttonTextStyle(
      type =
        when (treatment) {
          Tertiary, TertiaryNoUnderline -> LabelType.Label2
          else -> LabelType.Label1
        },
      underline =
        treatment == Tertiary ||
          treatment == TertiaryPrimary ||
          treatment == TertiaryDestructive,
      textColor = textColor
    )
  val isTextButton =
    treatment == TertiaryDestructive ||
      treatment == Tertiary ||
      treatment == TertiaryNoUnderline
  return ButtonStyle(
    textStyle = textStyle,
    shape = RoundedCornerShape(cornerRadius),
    backgroundColor =
      if (enabled) {
        treatment.normalBackgroundColor()
      } else {
        treatment.disabledBackgroundColor()
      },
    iconColor = iconColor(treatment),
    iconSize = treatment.leadingIconSize,
    isTextButton = isTextButton,
    fillWidth = size == Footer,
    height =
      when (size) {
        Compact -> 32.dp
        Floating -> 64.dp
        Footer, Regular -> 52.dp
        FitContent -> null
        Short -> 40.dp
      },
    minWidth =
      when (size) {
        Regular ->
          if (isTextButton) {
            0.dp
          } else {
            140.dp
          }
        else -> Dp.Unspecified
      },
    verticalPadding =
      when (size) {
        Compact -> 4.dp
        Floating -> 20.dp
        else -> 8.dp
      },
    horizontalPadding =
      if (isTextButton) {
        0.dp
      } else {
        when (size) {
          Regular, Footer, FitContent, Short -> 16.dp
          Compact -> 12.dp
          Floating -> 22.dp
        }
      }
  )
}

@Composable
@ReadOnlyComposable
private fun textColor(treatment: ButtonModel.Treatment): Color {
  return when (treatment) {
    Primary,
    PrimaryDestructive,
    -> colors.primaryForeground
    PrimaryDanger -> colors.dangerButtonText

    Secondary -> colors.secondaryForeground
    SecondaryDestructive -> colors.destructive
    Tertiary,
    TertiaryNoUnderline,
    -> colors.foreground

    TertiaryDestructive -> colors.destructiveForeground
    Translucent, Translucent10 -> colors.translucentForeground
    Grayscale20 -> colors.surfaceCorian
    TertiaryPrimary,
    TertiaryPrimaryNoUnderline,
    -> colors.bitkeyPrimary

    Black -> colors.background
    White -> Color.Black
    Warning -> colors.foreground10
    Accent -> colors.primaryForeground
  }
}

@Composable
@ReadOnlyComposable
private fun iconColor(treatment: ButtonModel.Treatment): Color {
  return when (treatment) {
    Primary,
    PrimaryDestructive,
    -> colors.primaryIconForeground
    PrimaryDanger -> colors.dangerButtonText

    Secondary -> colors.secondaryIconForeground
    SecondaryDestructive -> colors.destructive
    Tertiary,
    TertiaryNoUnderline,
    -> colors.primaryIcon

    TertiaryDestructive -> colors.destructive
    Translucent, Translucent10 -> colors.translucentForeground
    Grayscale20 -> colors.surfaceCorian
    TertiaryPrimary,
    TertiaryPrimaryNoUnderline,
    -> colors.bitkeyPrimary

    Black -> colors.background
    White -> Color.Black

    Warning -> colors.warning
    Accent -> colors.primaryForeground
  }
}

@Composable
@ReadOnlyComposable
private fun ButtonModel.Treatment.normalBackgroundColor() =
  when (this) {
    Primary -> colors.bitkeyPrimary
    PrimaryDanger -> colors.dangerBackground
    Secondary, SecondaryDestructive -> colors.secondary
    PrimaryDestructive -> colors.destructive
    Translucent -> colors.translucentButton20
    Translucent10 -> colors.translucentButton10
    TertiaryDestructive,
    Tertiary,
    TertiaryNoUnderline,
    TertiaryPrimary,
    TertiaryPrimaryNoUnderline,
    -> Color.Transparent
    Black -> colors.inverseBackground
    White -> Color.White
    Warning -> colors.warningForeground
    Accent,
    -> colors.accentDarkBackground
    Grayscale20 -> colors.grayscale20
  }

@Composable
@ReadOnlyComposable
private fun ButtonModel.Treatment.disabledBackgroundColor() =
  when (this) {
    Primary ->
      colors.bitkeyPrimary.copy(alpha = 0.4F)
    PrimaryDestructive ->
      colors.destructive.copy(alpha = 0.4F)
    PrimaryDanger, Secondary, SecondaryDestructive ->
      colors.secondary
    Translucent ->
      colors.translucentButton20
    Translucent10 ->
      colors.translucentButton10
    TertiaryDestructive,
    Tertiary,
    TertiaryNoUnderline,
    TertiaryPrimary,
    TertiaryPrimaryNoUnderline,
    ->
      Color.Transparent
    Black -> Color.Black.copy(alpha = 0.4F)
    White -> Color.White.copy(alpha = 0.4F)
    Warning -> colors.warningForeground.copy(alpha = 0.4F)
    Accent -> colors.accentDarkBackground
    Grayscale20 -> colors.grayscale20
  }
