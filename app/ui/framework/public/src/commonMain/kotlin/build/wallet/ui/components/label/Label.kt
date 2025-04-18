package build.wallet.ui.components.label

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import build.wallet.statemachine.core.LabelModel
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tokens.LabelType.Title3

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
  overflow: TextOverflow = TextOverflow.Clip,
  maxLines: Int = Int.MAX_VALUE,
  style: TextStyle = WalletTheme.labelStyle(type, treatment, alignment),
  onTextLayout: (TextLayoutResult) -> Unit = {},
) {
  Label(
    model = model,
    modifier = modifier,
    style = style,
    maxLines = maxLines,
    overflow = overflow,
    onTextLayout = onTextLayout
  )
}

@Composable
private fun Label(
  model: LabelModel,
  modifier: Modifier = Modifier,
  style: TextStyle,
  overflow: TextOverflow = TextOverflow.Clip,
  maxLines: Int = Int.MAX_VALUE,
  onTextLayout: (TextLayoutResult) -> Unit = {},
) {
  Label(
    modifier = modifier,
    text = model.buildAnnotatedString(),
    style = style,
    overflow = overflow,
    onTextLayout = onTextLayout,
    maxLines = maxLines
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
  maxLines: Int = Int.MAX_VALUE,
  overflow: TextOverflow = TextOverflow.Clip,
  onClick: ((TextClickPosition) -> Unit)? = null,
  onTextLayout: ((TextLayoutResult) -> Unit) = {},
) {
  if (onClick != null) {
    ClickableText(
      text = text,
      modifier = modifier,
      style = style,
      overflow = overflow,
      onClick = onClick,
      onTextLayout = onTextLayout
    )
  } else {
    BasicText(
      text = text,
      modifier = modifier,
      style = style,
      softWrap = softWrap,
      overflow = overflow,
      onTextLayout = onTextLayout,
      maxLines = maxLines
    )
  }
}

private fun TextLayoutResult.isWithinFivePercentOfFullWidth(): Boolean {
  return ((layoutInput.constraints.maxWidth - size.width) / layoutInput.constraints.maxWidth.toFloat()) < .05f
}

private typealias TextClickPosition = Int
