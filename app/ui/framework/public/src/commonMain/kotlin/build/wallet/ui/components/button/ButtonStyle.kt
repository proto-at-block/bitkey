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
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
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
  val isDesignSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current
  val effectiveCornerRadius = if (isDesignSystemV2Enabled) 80.dp else cornerRadius
  val isTextButton = treatment.isTextButton()

  return ButtonStyle(
    textStyle = treatment.toTextStyle(),
    shape = RoundedCornerShape(effectiveCornerRadius),
    backgroundColor = treatment.backgroundColor(enabled),
    iconColor = iconColor(treatment),
    iconSize = treatment.leadingIconSize,
    isTextButton = isTextButton,
    fillWidth = size == Footer,
    height = size.toHeight(),
    minWidth = size.toMinWidth(isTextButton),
    verticalPadding = size.toVerticalPadding(),
    horizontalPadding = size.toHorizontalPadding(isTextButton)
  )
}

private fun ButtonModel.Treatment.isTextButton(): Boolean =
  this == TertiaryDestructive ||
    this == Tertiary ||
    this == TertiaryNoUnderline ||
    this == TertiaryNoUnderlineWhite

@Composable
private fun ButtonModel.Treatment.toTextStyle(): TextStyle {
  val textColor = textColor(treatment = this)
  return buttonTextStyle(
    type = when (this) {
      Tertiary, TertiaryNoUnderline, TertiaryNoUnderlineWhite -> LabelType.Label2
      else -> LabelType.Label1
    },
    underline = this == Tertiary || this == TertiaryPrimary || this == TertiaryDestructive,
    textColor = textColor
  )
}

@Composable
private fun ButtonModel.Treatment.backgroundColor(enabled: Boolean): Color =
  if (enabled) normalBackgroundColor() else disabledBackgroundColor()

private fun ButtonModel.Size.toHeight(): Dp? =
  when (this) {
    Compact -> 32.dp
    Floating -> 64.dp
    Footer, Regular -> 52.dp
    FitContent -> null
    Short -> 40.dp
  }

private fun ButtonModel.Size.toMinWidth(isTextButton: Boolean): Dp =
  when (this) {
    Regular -> if (isTextButton) 0.dp else 140.dp
    else -> Dp.Unspecified
  }

private fun ButtonModel.Size.toVerticalPadding(): Dp =
  when (this) {
    Compact -> 4.dp
    Floating -> 20.dp
    else -> 8.dp
  }

private fun ButtonModel.Size.toHorizontalPadding(isTextButton: Boolean): Dp =
  if (isTextButton) {
    0.dp
  } else {
    when (this) {
      Regular, Footer, FitContent, Short -> 16.dp
      Compact -> 12.dp
      Floating -> 22.dp
    }
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

    TertiaryNoUnderlineWhite,
    -> colors.primaryForeground

    TertiaryDestructive -> colors.destructiveForeground
    Translucent, Translucent10 -> colors.translucentForeground
    Grayscale20 -> colors.surfaceCorian
    TertiaryPrimary,
    TertiaryPrimaryNoUnderline,
    -> colors.bitkeyPrimary

    BitkeyInteraction -> colors.background
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

    TertiaryNoUnderlineWhite,
    -> colors.primaryIconForeground

    TertiaryDestructive -> colors.destructive
    Translucent, Translucent10 -> colors.translucentForeground
    Grayscale20 -> colors.surfaceCorian
    TertiaryPrimary,
    TertiaryPrimaryNoUnderline,
    -> colors.bitkeyPrimary

    BitkeyInteraction -> colors.background
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
    TertiaryNoUnderlineWhite,
    TertiaryPrimary,
    TertiaryPrimaryNoUnderline,
    -> Color.Transparent
    BitkeyInteraction -> colors.inverseBackground
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
    TertiaryNoUnderlineWhite,
    TertiaryPrimary,
    TertiaryPrimaryNoUnderline,
    ->
      Color.Transparent
    BitkeyInteraction -> colors.inverseBackground.copy(alpha = 0.2F)
    White -> Color.White.copy(alpha = 0.4F)
    Warning -> colors.warningForeground.copy(alpha = 0.4F)
    Accent -> colors.accentDarkBackground
    Grayscale20 -> colors.grayscale20
  }
