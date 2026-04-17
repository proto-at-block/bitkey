package build.wallet.ui.components.label

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.LabelModel.StringWithStyledSubstringModel.SubstringStyle.BoldStyle
import build.wallet.statemachine.core.LabelModel.StringWithStyledSubstringModel.SubstringStyle.ColorStyle
import build.wallet.statemachine.core.LabelModel.StringWithStyledSubstringModel.SubstringStyle.FontFeatureStyle
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.theme.WalletTheme

private const val SLASHED_ZERO_FONT_FEATURE = "zero"

@Composable
fun LabelModel.Color.toWalletTheme(): androidx.compose.ui.graphics.Color {
  return when (this) {
    LabelModel.Color.GREEN -> WalletTheme.colors.deviceLEDGreen
    LabelModel.Color.BLUE -> WalletTheme.colors.deviceLEDBlue
    LabelModel.Color.ON60 -> WalletTheme.colors.foreground60
    LabelModel.Color.FOREGROUND -> WalletTheme.colors.foreground
    LabelModel.Color.PRIMARY -> WalletTheme.colors.bitkeyPrimary
    LabelModel.Color.UNSPECIFIED -> Color.Unspecified
  }
}

/**
 * Constructs an [AnnotatedString] that is appropriately styled for the provided [LabelModel].
 */
@Composable
fun LabelModel.buildAnnotatedString(): AnnotatedString {
  val model = this
  return buildAnnotatedString {
    append(string)
    when (model) {
      is LabelModel.StringModel -> Unit
      is LabelModel.CalloutModel -> Unit
      is LabelModel.StringWithStyledSubstringModel ->
        model.styledSubstrings.forEach { styledSubstring ->
          addStyle(
            style =
              when (val substringStyle = styledSubstring.style) {
                is ColorStyle -> SpanStyle(color = substringStyle.color.toWalletTheme())
                is BoldStyle -> SpanStyle(fontWeight = FontWeight.W600)
                is FontFeatureStyle -> {
                  val fontFeatureSettings = if (LocalDesignSystemUpdatesEnabled.current) {
                    substringStyle.fontFeatureSettings.withSlashedZero()
                  } else {
                    substringStyle.fontFeatureSettings
                  }
                  SpanStyle(fontFeatureSettings = fontFeatureSettings)
                }
              },
            start = styledSubstring.range.first,
            end = styledSubstring.range.last + 1
          )
        }
      is LabelModel.LinkSubstringModel ->
        model.linkedSubstrings.forEach { linkedSubstring ->
          addLink(
            clickable = LinkAnnotation.Clickable(
              tag = model.string.substring(linkedSubstring.range),
              linkInteractionListener = { linkedSubstring.onClick() },
              styles = TextLinkStyles(
                style = SpanStyle(
                  textDecoration = if (model.underline) TextDecoration.Underline else null,
                  fontWeight = if (model.bold) FontWeight.W600 else null,
                  color = model.color.toWalletTheme()
                )
              )
            ),
            start = linkedSubstring.range.first,
            end = linkedSubstring.range.last + 1
          )
        }
    }
  }
}

private fun String.withSlashedZero(): String =
  if (contains("zero")) {
    this
  } else {
    "$this, $SLASHED_ZERO_FONT_FEATURE"
  }
