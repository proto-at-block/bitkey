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
import build.wallet.ui.model.button.ButtonModel.Size.ToolbarAccessory
import build.wallet.ui.model.button.ButtonModel.Treatment.*
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.theme.WalletTheme.colors
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tokens.shouldRenderAllCaps

/**
 * Styling configuration for a [Button].
 *
 * [textStyle] - styling configuration for a text content inside the button.
 */
data class ButtonStyle(
  val textStyle: TextStyle,
  val isAllCaps: Boolean,
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
  val iconGap: Dp = 4.dp,
)

/**
 * Creates [ButtonStyle] using current [WalletTheme] values and provided button properties.
 */
@Composable
fun WalletTheme.buttonStyle(
  treatment: ButtonModel.Treatment,
  size: ButtonModel.Size,
  enabled: Boolean = true,
): ButtonStyle {
  val isTextButton = treatment.isTextButton()
  val labelType = treatment.textLabelType()

  return ButtonStyle(
    textStyle = treatment.toTextStyle(labelType),
    isAllCaps = labelType.shouldRenderAllCaps(),
    shape = RoundedCornerShape(80.dp),
    backgroundColor = treatment.backgroundColor(enabled),
    iconColor = iconColor(treatment),
    iconSize = treatment.leadingIconSize,
    isTextButton = isTextButton,
    fillWidth = size == Footer,
    height = size.toHeight(),
    minWidth = size.toMinWidth(isTextButton),
    verticalPadding = size.toVerticalPadding(),
    horizontalPadding = size.toHorizontalPadding(isTextButton),
    iconGap = if (treatment == BitkeyInteraction) 8.dp else 4.dp
  )
}

private fun ButtonModel.Treatment.isTextButton(): Boolean =
  this == TertiaryDestructive ||
    this == Tertiary ||
    this == TertiaryNoUnderline ||
    this == TertiaryNoUnderlineWhite

private fun ButtonModel.Treatment.textLabelType(): LabelType = LabelType.Body3Mono

@Composable
private fun ButtonModel.Treatment.toTextStyle(
  labelType: LabelType,
): TextStyle {
  val textColor = textColor(treatment = this)

  return buttonTextStyle(
    type = labelType,
    underline = this == Tertiary || this == TertiaryPrimary || this == TertiaryDestructive,
    textColor = textColor
  )
}

@Composable
private fun ButtonModel.Treatment.backgroundColor(
  enabled: Boolean,
): Color =
  if (enabled) {
    normalBackgroundColor()
  } else {
    disabledBackgroundColor()
  }

private fun ButtonModel.Size.toHeight(): Dp? =
  when (this) {
    Compact -> 32.dp
    ToolbarAccessory -> 44.dp
    Floating -> 64.dp
    Footer, Regular -> 56.dp
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
    ToolbarAccessory -> 11.dp
    Floating -> 20.dp
    else -> 8.dp
  }

private fun ButtonModel.Size.toHorizontalPadding(isTextButton: Boolean): Dp =
  if (isTextButton) {
    0.dp
  } else {
    when (this) {
      Regular, Footer, FitContent, Short -> 16.dp
      Compact, ToolbarAccessory -> 12.dp
      Floating -> 22.dp
    }
  }

@Composable
@ReadOnlyComposable
private fun textColor(
  treatment: ButtonModel.Treatment,
): Color {
  if (treatment == SecondaryDestructive || treatment == TertiaryDestructive) {
    return colors.destructive
  }
  return if (treatment.usesNeutralBackground()) {
    colors.inverseBackground
  } else {
    colors.background
  }
}

@Composable
@ReadOnlyComposable
private fun iconColor(
  treatment: ButtonModel.Treatment,
): Color {
  if (treatment == SecondaryDestructive || treatment == TertiaryDestructive) {
    return colors.destructive
  }
  return if (treatment.usesNeutralBackground()) {
    colors.inverseBackground
  } else {
    colors.background
  }
}

private fun ButtonModel.Treatment.usesNeutralBackground(): Boolean =
  when (this) {
    Secondary,
    SecondaryDestructive,
    Translucent,
    Translucent10,
    Grayscale20,
    Tertiary,
    TertiaryNoUnderline,
    TertiaryNoUnderlineWhite,
    TertiaryPrimary,
    TertiaryPrimaryNoUnderline,
    TertiaryDestructive,
    -> true
    else -> false
  }

@Composable
@ReadOnlyComposable
private fun ButtonModel.Treatment.normalBackgroundColor() =
  when (this) {
    Primary -> colors.inverseBackground
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
      colors.inverseBackground.copy(alpha = 0.4F)
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
    BitkeyInteraction ->
      colors.inverseBackground.copy(alpha = 0.4F)
    White -> Color.White.copy(alpha = 0.4F)
    Warning -> colors.warningForeground.copy(alpha = 0.4F)
    Accent -> colors.accentDarkBackground
    Grayscale20 -> colors.grayscale20
  }
