package build.wallet.ui.components.label

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.LineHeightStyle.Alignment
import androidx.compose.ui.text.style.LineHeightStyle.Trim
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration.Companion.LineThrough
import androidx.compose.ui.text.style.TextDecoration.Companion.None
import androidx.compose.ui.text.style.TextDecoration.Companion.Underline
import build.wallet.ui.components.label.LabelTreatment.Disabled
import build.wallet.ui.components.label.LabelTreatment.Primary
import build.wallet.ui.components.label.LabelTreatment.Secondary
import build.wallet.ui.components.label.LabelTreatment.Strikethrough
import build.wallet.ui.components.label.LabelTreatment.Tertiary
import build.wallet.ui.components.label.LabelTreatment.Unspecified
import build.wallet.ui.components.label.LabelTreatment.Warning
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tokens.style
import build.wallet.ui.typography.font.interFontFamily

/**
 * Use this to method to customize label style:
 *
 * ```
 * Label(
 *   text = "hi!",
 *   style = WalletTheme.labelStyle(color = Color.Red)
 * )
 * ```
 */
@Composable
fun WalletTheme.labelStyle(
  type: LabelType,
  treatment: LabelTreatment = Primary,
  alignment: TextAlign = TextAlign.Start,
  textColor: Color = Color.Unspecified,
): TextStyle = textStyle(type, treatment, textColor).copy(textAlign = alignment)

/**
 * Constructs Compose UI [TextStyle] for the given [LabelType].
 */
@Composable
@ReadOnlyComposable
internal fun WalletTheme.textStyle(
  type: LabelType,
  treatment: LabelTreatment,
  textColor: Color,
): TextStyle {
  val color =
    when (treatment) {
      Primary, Tertiary -> colors.foreground
      Secondary, Strikethrough -> colors.foreground60
      Disabled -> colors.foreground30
      Warning -> colors.warningForeground
      Unspecified -> textColor
    }

  val textDecoration =
    when (treatment) {
      Tertiary -> Underline
      Strikethrough -> LineThrough
      else -> None
    }

  val baseFont =
    TextStyle(
      fontFamily = interFontFamily,
      fontStyle = FontStyle.Normal
    )

  return type.style(baseFont).copy(
    textAlign = TextAlign.Center,
    color = color,
    textDecoration = textDecoration,
    lineHeightStyle =
      LineHeightStyle(
        alignment = Alignment.Center,
        // Respect line height value and do not trim padding.
        trim = Trim.None
      ),
    // TODO(W-1783): remove when Compose UI makes this a default value.
    platformStyle = PlatformTextStyle(includeFontPadding = false)
  )
}

/**
 * Constructs Compose UI [TextStyle] to be used in a button.
 */
@Composable
@ReadOnlyComposable
internal fun buttonTextStyle(
  type: LabelType,
  underline: Boolean,
  textColor: Color,
): TextStyle {
  val baseFont =
    TextStyle(
      fontFamily = interFontFamily,
      fontStyle = FontStyle.Normal
    )

  return type.style(baseFont).copy(
    textAlign = TextAlign.Center,
    color = textColor,
    textDecoration = if (underline) Underline else None,
    lineHeightStyle =
      LineHeightStyle(
        alignment = Alignment.Center,
        // Respect line height value and do not trim padding.
        trim = Trim.None
      ),
    // TODO(W-1783): remove when Compose UI makes this a default value.
    platformStyle = PlatformTextStyle(includeFontPadding = false)
  )
}
