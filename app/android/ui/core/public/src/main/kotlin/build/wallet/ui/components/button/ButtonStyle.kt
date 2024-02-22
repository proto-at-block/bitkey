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
import build.wallet.ui.model.button.ButtonModel.Size.Floating
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Size.Regular
import build.wallet.ui.model.button.ButtonModel.Treatment.Black
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.model.button.ButtonModel.Treatment.Secondary
import build.wallet.ui.model.button.ButtonModel.Treatment.SecondaryDestructive
import build.wallet.ui.model.button.ButtonModel.Treatment.Tertiary
import build.wallet.ui.model.button.ButtonModel.Treatment.TertiaryDestructive
import build.wallet.ui.model.button.ButtonModel.Treatment.TertiaryNoUnderline
import build.wallet.ui.model.button.ButtonModel.Treatment.TertiaryPrimary
import build.wallet.ui.model.button.ButtonModel.Treatment.TertiaryPrimaryNoUnderline
import build.wallet.ui.model.button.ButtonModel.Treatment.Translucent
import build.wallet.ui.model.button.ButtonModel.Treatment.Translucent10
import build.wallet.ui.model.button.ButtonModel.Treatment.Warning
import build.wallet.ui.model.button.ButtonModel.Treatment.White
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
  val height: Dp,
  val fillWidth: Boolean,
  val verticalPadding: Dp,
  val horizontalPadding: Dp,
)

/**
 * Creates [ButtonStyle] using current [WalletTheme] values and provided button properties.
 */
@Composable
@ReadOnlyComposable
fun WalletTheme.buttonStyle(
  treatment: ButtonModel.Treatment,
  size: ButtonModel.Size,
  cornerRadius: Dp = 16.dp,
  enabled: Boolean,
): ButtonStyle {
  val textColor = textColor(enabled = enabled, treatment = treatment)
  val textStyle =
    buttonTextStyle(
      type =
        when (treatment) {
          Tertiary -> LabelType.Label2
          else -> LabelType.Label1
        },
      underline = treatment == Tertiary || treatment == TertiaryPrimary,
      textColor = textColor
    )
  val isTextButton = treatment == TertiaryDestructive || treatment == Tertiary
  return ButtonStyle(
    textStyle = textStyle,
    shape = RoundedCornerShape(cornerRadius),
    backgroundColor =
      if (enabled) {
        treatment.normalBackgroundColor()
      } else {
        treatment.disabledBackgroundColor()
      },
    iconColor = iconColor(enabled, treatment),
    iconSize = treatment.leadingIconSize,
    isTextButton = isTextButton,
    fillWidth = size == Footer,
    height =
      when (size) {
        Compact -> 32.dp
        Floating -> 64.dp
        else -> 52.dp
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
          Regular -> 16.dp
          Compact -> 12.dp
          Footer -> 16.dp
          Floating -> 22.dp
        }
      }
  )
}

@Composable
@ReadOnlyComposable
private fun textColor(
  enabled: Boolean,
  treatment: ButtonModel.Treatment,
): Color {
  if (enabled) {
    return when (treatment) {
      Black,
      Primary,
      -> colors.primaryForeground

      Secondary -> colors.secondaryForeground
      SecondaryDestructive -> colors.destructive
      Tertiary,
      TertiaryNoUnderline,
      -> colors.foreground

      TertiaryDestructive -> colors.destructive
      Translucent, Translucent10 -> colors.translucentForeground
      TertiaryPrimary,
      TertiaryPrimaryNoUnderline,
      -> colors.primary

      White -> Color.Black
      Warning -> colors.warning
    }
  } else {
    return colors.foreground30
  }
}

@Composable
@ReadOnlyComposable
private fun iconColor(
  enabled: Boolean,
  treatment: ButtonModel.Treatment,
): Color {
  if (!enabled) {
    return colors.foreground30
  }

  return when (treatment) {
    Black,
    Primary,
    -> colors.primaryIconForeground

    Secondary -> colors.secondaryIconForeground
    SecondaryDestructive -> colors.destructive
    Tertiary,
    TertiaryNoUnderline,
    -> colors.primaryIcon

    TertiaryDestructive -> colors.destructive
    Translucent, Translucent10 -> colors.translucentForeground
    TertiaryPrimary,
    TertiaryPrimaryNoUnderline,
    -> colors.primary

    White -> Color.Black

    Warning -> colors.warning
  }
}

@Composable
@ReadOnlyComposable
private fun ButtonModel.Treatment.normalBackgroundColor() =
  when (this) {
    Primary -> colors.primary
    Secondary, SecondaryDestructive -> colors.secondary
    Translucent -> colors.translucentButton20
    Translucent10 -> colors.translucentButton10
    TertiaryDestructive,
    Tertiary,
    TertiaryNoUnderline,
    TertiaryPrimary,
    TertiaryPrimaryNoUnderline,
    -> Color.Transparent
    Black -> Color.Black
    White -> Color.White
    Warning -> colors.warningForeground
  }

@Composable
@ReadOnlyComposable
private fun ButtonModel.Treatment.disabledBackgroundColor() =
  when (this) {
    Primary ->
      colors.primary.copy(alpha = 0.8F)
    Secondary, SecondaryDestructive ->
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
    Black -> Color.Black.copy(alpha = 0.8F)
    White -> Color.White.copy(alpha = 0.8F)
    Warning -> colors.warningForeground.copy(alpha = 0.8F)
  }
