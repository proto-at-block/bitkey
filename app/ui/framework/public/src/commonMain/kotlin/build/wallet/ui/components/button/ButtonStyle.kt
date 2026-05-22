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
import build.wallet.ui.tokens.isAllCapsInCurrentDesignSystem

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
  cornerRadius: Dp = 16.dp,
  enabled: Boolean = true,
): ButtonStyle {
  val isDesignSystemV2Enabled = true
  val effectiveCornerRadius = if (isDesignSystemV2Enabled) 80.dp else cornerRadius
  val isTextButton = treatment.isTextButton()
  val labelType = treatment.textLabelType(isDesignSystemV2Enabled)

  return ButtonStyle(
    textStyle = treatment.toTextStyle(labelType, isDesignSystemV2Enabled),
    isAllCaps = labelType.isAllCapsInCurrentDesignSystem(),
    shape = RoundedCornerShape(effectiveCornerRadius),
    backgroundColor = treatment.backgroundColor(enabled, isDesignSystemV2Enabled),
    iconColor = iconColor(treatment, isDesignSystemV2Enabled),
    iconSize = treatment.leadingIconSize,
    isTextButton = isTextButton,
    fillWidth = size == Footer,
    height = size.toHeight(isDesignSystemV2Enabled),
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

private fun ButtonModel.Treatment.textLabelType(isDesignSystemV2Enabled: Boolean): LabelType =
  if (isDesignSystemV2Enabled) {
    LabelType.Body3Mono
  } else {
    when (this) {
      Tertiary, TertiaryNoUnderline, TertiaryNoUnderlineWhite -> LabelType.Label2
      else -> LabelType.Label1
    }
  }

@Composable
private fun ButtonModel.Treatment.toTextStyle(
  labelType: LabelType,
  isDesignSystemV2Enabled: Boolean,
): TextStyle {
  val textColor = textColor(treatment = this, isDesignSystemV2Enabled = isDesignSystemV2Enabled)

  return buttonTextStyle(
    type = labelType,
    underline = this == Tertiary || this == TertiaryPrimary || this == TertiaryDestructive,
    textColor = textColor
  )
}

@Composable
private fun ButtonModel.Treatment.backgroundColor(
  enabled: Boolean,
  isDesignSystemV2Enabled: Boolean,
): Color =
  if (enabled) {
    normalBackgroundColor(isDesignSystemV2Enabled)
  } else {
    disabledBackgroundColor(isDesignSystemV2Enabled)
  }

private fun ButtonModel.Size.toHeight(isDesignSystemV2Enabled: Boolean): Dp? =
  when (this) {
    Compact -> 32.dp
    ToolbarAccessory -> 44.dp
    Floating -> 64.dp
    Footer, Regular -> if (isDesignSystemV2Enabled) 56.dp else 52.dp
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
  isDesignSystemV2Enabled: Boolean,
): Color {
  if (isDesignSystemV2Enabled) {
    if (treatment == SecondaryDestructive || treatment == TertiaryDestructive) {
      return colors.destructive
    }
    return if (treatment.isV2NeutralBackground()) {
      colors.inverseBackground
    } else {
      colors.background
    }
  }
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
private fun iconColor(
  treatment: ButtonModel.Treatment,
  isDesignSystemV2Enabled: Boolean,
): Color {
  if (isDesignSystemV2Enabled) {
    if (treatment == SecondaryDestructive || treatment == TertiaryDestructive) {
      return colors.destructive
    }
    return if (treatment.isV2NeutralBackground()) {
      colors.inverseBackground
    } else {
      colors.background
    }
  }
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

private fun ButtonModel.Treatment.isV2NeutralBackground(): Boolean =
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
private fun ButtonModel.Treatment.normalBackgroundColor(isDesignSystemV2Enabled: Boolean) =
  when (this) {
    Primary ->
      if (isDesignSystemV2Enabled) {
        colors.inverseBackground
      } else {
        colors.bitkeyPrimary
      }
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
    BitkeyInteraction ->
      if (isDesignSystemV2Enabled) {
        colors.inverseBackground
      } else {
        colors.inverseBackground
      }
    White -> Color.White
    Warning -> colors.warningForeground
    Accent,
    -> colors.accentDarkBackground
    Grayscale20 -> colors.grayscale20
  }

@Composable
@ReadOnlyComposable
private fun ButtonModel.Treatment.disabledBackgroundColor(isDesignSystemV2Enabled: Boolean) =
  when (this) {
    Primary ->
      if (isDesignSystemV2Enabled) {
        colors.inverseBackground.copy(alpha = 0.4F)
      } else {
        colors.bitkeyPrimary.copy(alpha = 0.4F)
      }
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
      if (isDesignSystemV2Enabled) {
        colors.inverseBackground.copy(alpha = 0.4F)
      } else {
        colors.inverseBackground.copy(alpha = 0.2F)
      }
    White -> Color.White.copy(alpha = 0.4F)
    Warning -> colors.warningForeground.copy(alpha = 0.4F)
    Accent -> colors.accentDarkBackground
    Grayscale20 -> colors.grayscale20
  }
