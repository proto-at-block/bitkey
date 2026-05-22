package build.wallet.ui.components.label

import android.graphics.Typeface
import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tooling.LocalIsPreviewTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tokens.LabelType.Body2Mono
import build.wallet.ui.tokens.LabelType.Body3Mono
import build.wallet.ui.tokens.LabelType.Body4Mono
import build.wallet.ui.tokens.LabelType.Header1
import kotlin.math.max

private const val COMPOSE_RESOURCES_PREFIX =
  "composeResources/bitkey.ui.framework_public.generated.resources/font/"
private const val CASH_SANS_REGULAR_ASSET = "${COMPOSE_RESOURCES_PREFIX}cash_sans_regular.otf"
private const val CASH_SANS_MEDIUM_ASSET = "${COMPOSE_RESOURCES_PREFIX}cash_sans_medium.otf"
private const val CASH_SANS_MONO_REGULAR_ASSET =
  "${COMPOSE_RESOURCES_PREFIX}cash_sans_mono_regular.otf"
private const val CASH_SANS_MONO_MEDIUM_ASSET =
  "${COMPOSE_RESOURCES_PREFIX}cash_sans_mono_medium.otf"
private const val FOUNDERS_GROTESK_ASSET =
  "${COMPOSE_RESOURCES_PREFIX}founders_grotesk_x_condensed_bold.otf"
private const val INTER_REGULAR_ASSET = "${COMPOSE_RESOURCES_PREFIX}inter_regular.otf"
private const val INTER_MEDIUM_ASSET = "${COMPOSE_RESOURCES_PREFIX}inter_medium.otf"
private const val INTER_SEMIBOLD_ASSET = "${COMPOSE_RESOURCES_PREFIX}inter_semibold.otf"
private const val INTER_BOLD_ASSET = "${COMPOSE_RESOURCES_PREFIX}inter_bold.otf"
private const val ROBOTO_MONO_ASSET = "${COMPOSE_RESOURCES_PREFIX}roboto_mono.ttf"

@Composable
actual fun AnimatedAmountAutoResizedLabel(
  amount: AnimatedAmount,
  modifier: Modifier,
  type: LabelType,
  alignment: TextAlign,
  treatment: LabelTreatment,
  color: Color,
  allowFontScaling: Boolean,
  animate: Boolean,
  animationLabel: String,
  minTextSize: TextUnit,
) {
  if (LocalIsPreviewTheme.current) {
    PreviewAnimatedAmountLabel(
      amount = amount,
      modifier = modifier,
      type = type,
      alignment = alignment,
      treatment = treatment,
      color = color,
      allowFontScaling = allowFontScaling
    )
  } else {
    AndroidAnimatedAmountLabel(
      amount = amount,
      modifier = modifier,
      type = type,
      alignment = alignment,
      treatment = treatment,
      color = color,
      allowFontScaling = allowFontScaling,
      animate = animate,
      minTextSize = minTextSize
    )
  }
}

@Composable
private fun PreviewAnimatedAmountLabel(
  amount: AnimatedAmount,
  modifier: Modifier = Modifier,
  type: LabelType,
  alignment: TextAlign,
  treatment: LabelTreatment,
  color: Color,
  allowFontScaling: Boolean,
) {
  AutoResizedLabel(
    text = amount.text,
    modifier = modifier,
    type = type,
    alignment = alignment,
    treatment = treatment,
    color = color,
    allowFontScaling = allowFontScaling
  )
}

@Composable
private fun AndroidAnimatedAmountLabel(
  amount: AnimatedAmount,
  modifier: Modifier = Modifier,
  type: LabelType,
  alignment: TextAlign,
  treatment: LabelTreatment,
  color: Color,
  allowFontScaling: Boolean,
  animate: Boolean,
  minTextSize: TextUnit,
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val style = WalletTheme.labelStyle(type, treatment, alignment, color)
  val isDesignSystemV2Enabled = true

  val typeface = remember(type, style.fontWeight, isDesignSystemV2Enabled) {
    val assetPath = resolveFontAssetPath(type, style.fontWeight, isDesignSystemV2Enabled)
    try {
      Typeface.createFromAsset(context.assets, assetPath)
    } catch (_: RuntimeException) {
      Typeface.DEFAULT
    }
  }

  val resolvedFontSizeSp = style.fontSize.resolveSp(allowFontScaling, density.fontScale)
  val resolvedFontSizePx = with(density) { resolvedFontSizeSp.sp.toPx() }
  val resolvedMinTextSizePx =
    if (minTextSize.type == TextUnitType.Sp) {
      with(density) {
        minTextSize.resolveSp(allowFontScaling, density.fontScale).sp.toPx()
      }
    } else {
      0f
    }
  val resolvedLetterSpacingSp = style.letterSpacing.resolveSp(allowFontScaling, density.fontScale)
  val resolvedLetterSpacing =
    when (style.letterSpacing.type) {
      TextUnitType.Sp -> if (resolvedFontSizeSp == 0f) 0f else resolvedLetterSpacingSp / resolvedFontSizeSp
      TextUnitType.Em -> style.letterSpacing.value
      else -> 0f
    }

  AndroidView(
    modifier = modifier,
    factory = ::AnimatedAmountTextView,
    update = { view ->
      view.gravity =
        when (alignment) {
          TextAlign.Center -> Gravity.CENTER_HORIZONTAL
          TextAlign.End, TextAlign.Right -> Gravity.END
          else -> Gravity.START
        }
      view.textSizeInPx = resolvedFontSizePx
      view.minTextSizeInPx = resolvedMinTextSizePx.takeIf { it > 0f }
      view.letterSpacing = resolvedLetterSpacing
      view.typeface = typeface
      view.fontFeatureSettings = style.fontFeatureSettings
      view.textColor = style.color.toArgb()
      view.animationsEnabled = animate
      view.animateEvenIfSame = false
      view.setAmount(
        AnimatedAmountTextView.Amount(
          text = amount.text,
          value = amount.value,
          animationKey = amount.animationKey
        )
      )
    }
  )
}

private fun resolveFontAssetPath(
  type: LabelType,
  fontWeight: FontWeight?,
  isDesignSystemV2Enabled: Boolean,
): String {
  val weight = fontWeight?.weight ?: FontWeight.Normal.weight
  val isMono = type in setOf(Body2Mono, Body3Mono, Body4Mono)

  return when {
    isDesignSystemV2Enabled && isMono ->
      if (weight >= FontWeight.Medium.weight) CASH_SANS_MONO_MEDIUM_ASSET else CASH_SANS_MONO_REGULAR_ASSET

    isDesignSystemV2Enabled ->
      if (weight >= FontWeight.Medium.weight) CASH_SANS_MEDIUM_ASSET else CASH_SANS_REGULAR_ASSET

    type == Header1 -> FOUNDERS_GROTESK_ASSET
    isMono -> ROBOTO_MONO_ASSET
    weight >= FontWeight.Bold.weight -> INTER_BOLD_ASSET
    weight >= FontWeight.SemiBold.weight -> INTER_SEMIBOLD_ASSET
    weight >= FontWeight.Medium.weight -> INTER_MEDIUM_ASSET
    else -> INTER_REGULAR_ASSET
  }
}

private fun TextUnit.resolveSp(
  allowFontScaling: Boolean,
  fontScale: Float,
): Float {
  return when (type) {
    TextUnitType.Sp -> value / if (allowFontScaling) 1f else max(fontScale, 1f)
    else -> 0f
  }
}
