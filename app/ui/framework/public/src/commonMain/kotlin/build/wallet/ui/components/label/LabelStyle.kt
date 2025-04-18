package build.wallet.ui.components.label

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import build.wallet.ui.components.label.LabelTreatment.Jumbo
import build.wallet.ui.components.label.LabelTreatment.Primary
import build.wallet.ui.components.label.LabelTreatment.PrimaryDark
import build.wallet.ui.components.label.LabelTreatment.Quaternary
import build.wallet.ui.components.label.LabelTreatment.Secondary
import build.wallet.ui.components.label.LabelTreatment.SecondaryDark
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
fun WalletTheme.textStyle(
  type: LabelType,
  treatment: LabelTreatment,
  textColor: Color,
): TextStyle {
  val color =
    when (treatment) {
      Primary, Tertiary, Quaternary, Jumbo -> colors.foreground
      PrimaryDark -> colors.accentDarkBackground
      Secondary, Strikethrough -> colors.foreground60
      SecondaryDark -> colors.foreground30
      Disabled -> colors.foreground10
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
      )
  )
}

/**
 * Constructs Compose UI [TextStyle] to be used in a button.
 */
@Composable
fun buttonTextStyle(
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
        // Fix for vertically centered text in buttons.
        // https://youtrack.jetbrains.com/issue/CMP-6985/Text-with-unexpected-margins-on-iOS
        trim = Trim.Both
      )
  )
}
