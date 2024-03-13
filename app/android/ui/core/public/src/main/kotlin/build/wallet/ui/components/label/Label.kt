package build.wallet.ui.components.label

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.LabelModel
import build.wallet.ui.theme.SystemColorMode.DARK
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tokens.LabelType.Title3
import build.wallet.ui.tooling.PreviewWalletTheme
import com.ionspin.kotlin.bignum.decimal.minus

private const val AUTO_SIZE_SCALING_FACTOR = .97

/**
 * A text component used to display single, or multiline texts.
 */
@Composable
fun Label(
  text: String,
  modifier: Modifier = Modifier,
  type: LabelType = Title3,
  alignment: TextAlign = TextAlign.Start,
  treatment: LabelTreatment = LabelTreatment.Primary,
  color: Color = Color.Unspecified,
  onClick: (() -> Unit)? = null,
) {
  Label(
    text = AnnotatedString(text),
    modifier = modifier,
    style = WalletTheme.labelStyle(type, treatment, alignment, color),
    onClick =
      onClick?.let {
        {
          onClick()
        }
      }
  )
}

@Composable
fun Label(
  model: LabelModel,
  modifier: Modifier = Modifier,
  type: LabelType = Title3,
  treatment: LabelTreatment = LabelTreatment.Primary,
  alignment: TextAlign = TextAlign.Start,
) {
  Label(
    modifier = modifier,
    text = buildAnnotatedString {
      when (model) {
        is LabelModel.StringModel ->
          append(model.string)
        is LabelModel.StringWithStyledSubstringModel -> {
          append(model.string)
          model.styledSubstrings.forEach { styledSubstring ->
            addStyle(
              style =
                when (val substringStyle = styledSubstring.style) {
                  is LabelModel.StringWithStyledSubstringModel.SubstringStyle.ColorStyle ->
                    SpanStyle(
                      color = substringStyle.color.toWalletTheme()
                    )
                  is LabelModel.StringWithStyledSubstringModel.SubstringStyle.BoldStyle ->
                    SpanStyle(
                      fontWeight = FontWeight.W600
                    )
                },
              start = styledSubstring.range.first,
              end = styledSubstring.range.last + 1
            )
          }
        }
        is LabelModel.LinkSubstringModel -> {
          append(model.string)
          model.linkedSubstrings.forEach { linkedSubstring ->
            addStyle(
              style = SpanStyle(color = WalletTheme.colors.primary),
              start = linkedSubstring.range.first,
              end = linkedSubstring.range.last + 1
            )
          }
        }
      }
    },
    onClick = (model as? LabelModel.LinkSubstringModel)?.let { linkedLabelModel ->
      { clickPosition ->
        linkedLabelModel.linkedSubstrings.find { ls ->
          ls.range.contains(clickPosition)
        }?.let { matchedLs ->
          matchedLs.onClick()
        }
      }
    },
    type = type,
    treatment = treatment,
    alignment = alignment
  )
}

@Composable
fun Label(
  text: AnnotatedString,
  modifier: Modifier = Modifier,
  type: LabelType,
  alignment: TextAlign = TextAlign.Start,
  treatment: LabelTreatment = LabelTreatment.Primary,
  color: Color = Color.Unspecified,
  onClick: ((TextClickPosition) -> Unit)? = null,
) {
  Label(
    text = text,
    modifier = modifier,
    style = WalletTheme.labelStyle(type, treatment, alignment, color),
    onClick = onClick
  )
}

/**
 * Allows to create label with custom style using [WalletTheme.labelStyle]:
 *
 * ```
 * Label(
 *   text = "hi!",
 *   style = WalletTheme.labelStyle(color = Color.Red)
 * )
 * ```
 */
@Composable
fun Label(
  text: String,
  modifier: Modifier = Modifier,
  style: TextStyle,
  onClick: (() -> Unit)? = null,
) {
  Label(
    text = AnnotatedString(text),
    modifier = modifier,
    style = style,
    onClick =
      onClick?.let {
        {
          onClick()
        }
      }
  )
}

@Composable
fun AutoResizedLabel(
  text: String,
  modifier: Modifier = Modifier,
  type: LabelType = Title3,
  alignment: TextAlign = TextAlign.Start,
  treatment: LabelTreatment = LabelTreatment.Primary,
  color: Color = Color.Unspecified,
  softWrap: Boolean = false,
  onClick: ((TextClickPosition) -> Unit)? = null,
) {
  AutoResizedLabel(
    text = AnnotatedString(text),
    modifier = modifier,
    type = type,
    alignment = alignment,
    treatment = treatment,
    color = color,
    softWrap = softWrap,
    onClick =
      onClick?.let {
        { position -> onClick(position) }
      }
  )
}

@Composable
fun AutoResizedLabel(
  text: AnnotatedString,
  modifier: Modifier = Modifier,
  type: LabelType,
  alignment: TextAlign = TextAlign.Start,
  treatment: LabelTreatment = LabelTreatment.Primary,
  color: Color = Color.Unspecified,
  softWrap: Boolean = false,
  onClick: ((TextClickPosition) -> Unit)? = null,
) {
  val style = WalletTheme.labelStyle(type, treatment, alignment, color)
  var resizedTextStyle by remember(style) {
    mutableStateOf(style.copy(fontSize = style.fontSize))
  }
  // used to store whether we should resize the text or draw the text at the calculated size
  var shouldDraw by remember { mutableStateOf(false) }

  Label(
    text = text,
    modifier =
      modifier.drawWithContent {
        if (shouldDraw) {
          drawContent()
        }
      },
    style = resizedTextStyle,
    softWrap = softWrap,
    onClick =
      onClick?.let {
        { position -> onClick(position) }
      },
    onTextLayout = { result ->
      // if the text is too wide for a single line, try to redraw at 97% size
      if (result.didOverflowWidth) {
        val fontSize = resizedTextStyle.fontSize * AUTO_SIZE_SCALING_FACTOR
        resizedTextStyle = resizedTextStyle.copy(fontSize = fontSize)
        // else if the text is not within five percent of it's original full width and its current
        // font size is less than its original, increase the font size to get close to the range
      } else if (!result.isWithinFivePercentOfFullWidth() && resizedTextStyle.fontSize < style.fontSize) {
        val fontSize = resizedTextStyle.fontSize / AUTO_SIZE_SCALING_FACTOR
        resizedTextStyle = resizedTextStyle.copy(fontSize = fontSize)
      } else {
        // allow draw once the text has been appropriately sized
        shouldDraw = true
      }
    }
  )
}

@Composable
fun Label(
  text: AnnotatedString,
  modifier: Modifier = Modifier,
  style: TextStyle,
  softWrap: Boolean = true,
  onClick: ((TextClickPosition) -> Unit)? = null,
  onTextLayout: ((TextLayoutResult) -> Unit) = {},
) {
  if (onClick != null) {
    ClickableText(
      text = text,
      modifier = modifier,
      style = style,
      onClick = onClick,
      onTextLayout = onTextLayout
    )
  } else {
    BasicText(
      text = text,
      modifier = modifier,
      style = style,
      softWrap = softWrap,
      onTextLayout = onTextLayout
    )
  }
}

private fun TextLayoutResult.isWithinFivePercentOfFullWidth(): Boolean {
  return ((layoutInput.constraints.maxWidth - size.width) / layoutInput.constraints.maxWidth.toFloat()) < .05f
}

@Preview(name = "All Labels Light")
@Composable
private fun AllLabelsLightPreview() {
  PreviewWalletTheme {
    AllLabelsPreview()
  }
}

@Preview(name = "All Labels Dark")
@Composable
private fun AllLabelsDarkPreview() {
  PreviewWalletTheme(systemColorMode = DARK) {
    Box(modifier = Modifier.background(color = WalletTheme.colors.foreground)) {
      AllLabelsPreview()
    }
  }
}

@Composable
internal fun AllLabelsPreview() {
  Box {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      LabelType.entries.forEach { labelType ->
        Row(
          modifier = Modifier.border(width = 1.dp, color = Color.LightGray)
        ) {
          Label(
            text = labelType.name,
            type = labelType
          )
        }
      }
    }
  }
}

@Preview(name = "Long Content")
@Composable
internal fun LabelWithLongContentPreview() {
  PreviewWalletTheme {
    Label(text = LONG_CONTENT, type = LabelType.Label3)
  }
}

private const val LONG_CONTENT =
  "A purely peer-to-peer version of electronic cash would allow online " +
    "payments to be sent directly from one party to another without going through a " +
    "financial institution."

private typealias TextClickPosition = Int
