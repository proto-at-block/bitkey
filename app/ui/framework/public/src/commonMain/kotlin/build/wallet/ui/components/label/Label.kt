package build.wallet.ui.components.label

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import build.wallet.statemachine.core.LabelModel
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tokens.LabelType.Title3
import build.wallet.ui.tokens.shouldRenderAllCaps

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
  overflow: TextOverflow = TextOverflow.Clip,
  treatment: LabelTreatment = LabelTreatment.Primary,
  color: Color = Color.Unspecified,
  allowFontScaling: Boolean = true,
  onClick: (() -> Unit)? = null,
) {
  val textToRender = if (type.shouldRenderAllCaps()) text.uppercase() else text
  Label(
    text = AnnotatedString(textToRender),
    modifier = modifier,
    style = WalletTheme.labelStyle(type, treatment, alignment, color),
    allowFontScaling = allowFontScaling,
    overflow = overflow,
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
  allowFontScaling: Boolean = true,
  onTextLayout: (TextLayoutResult) -> Unit = {},
) {
  val modelToRender = if (type.shouldRenderAllCaps()) {
    when (model) {
      is LabelModel.StringModel -> model.copy(string = model.string.uppercase())
      is LabelModel.CalloutModel -> model.copy(string = model.string.uppercase())
      else -> model
    }
  } else {
    model
  }
  Label(
    model = modelToRender,
    modifier = modifier,
    style = style,
    maxLines = maxLines,
    overflow = overflow,
    allowFontScaling = allowFontScaling,
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
  allowFontScaling: Boolean = true,
  onTextLayout: (TextLayoutResult) -> Unit = {},
) {
  Label(
    modifier = modifier,
    text = model.buildAnnotatedString(),
    style = style,
    overflow = overflow,
    onTextLayout = onTextLayout,
    maxLines = maxLines,
    allowFontScaling = allowFontScaling
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
  allowFontScaling: Boolean = true,
  onClick: ((TextClickPosition) -> Unit)? = null,
) {
  val textToRender = if (type.shouldRenderAllCaps()) {
    if (text.spanStyles.isEmpty() && text.paragraphStyles.isEmpty() && !text.hasAnnotations()) {
      text.uppercasePreservingAnnotations()
    } else {
      text
    }
  } else {
    text
  }
  Label(
    text = textToRender,
    modifier = modifier,
    style = WalletTheme.labelStyle(type, treatment, alignment, color),
    allowFontScaling = allowFontScaling,
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
  allowFontScaling: Boolean = true,
  onClick: (() -> Unit)? = null,
) {
  Label(
    text = AnnotatedString(text),
    modifier = modifier,
    style = style,
    allowFontScaling = allowFontScaling,
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
  allowFontScaling: Boolean = true,
  onClick: ((TextClickPosition) -> Unit)? = null,
) {
  val textToRender = if (type.shouldRenderAllCaps()) text.uppercase() else text
  AutoResizedLabel(
    text = AnnotatedString(textToRender),
    modifier = modifier,
    type = type,
    alignment = alignment,
    treatment = treatment,
    color = color,
    softWrap = softWrap,
    allowFontScaling = allowFontScaling,
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
  allowFontScaling: Boolean = true,
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
    allowFontScaling = allowFontScaling,
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
  allowFontScaling: Boolean = true,
  onClick: ((TextClickPosition) -> Unit)? = null,
  onTextLayout: ((TextLayoutResult) -> Unit) = {},
) {
  val fontScale = LocalDensity.current.fontScale
  val styleWithFontScaling =
    if (allowFontScaling) {
      style
    } else {
      style.copy(
        fontSize = style.fontSize / fontScale,
        lineHeight = style.lineHeight / fontScale
      )
    }
  val textWithFeatureSpan = remember(text, styleWithFontScaling.fontFeatureSettings) {
    text.withGlobalFontFeatureIfNeeded(styleWithFontScaling.fontFeatureSettings)
  }
  val styleToRender = styleWithFontScaling.copy(fontFeatureSettings = null)
  if (onClick != null) {
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val currentOnClick by rememberUpdatedState(onClick)
    BasicText(
      text = textWithFeatureSpan,
      modifier = modifier
        .pointerInput(Unit) {
          detectTapGestures { offset ->
            textLayoutResult
              ?.getOffsetForPosition(offset)
              ?.let(currentOnClick)
          }
        }
        .semantics {
          semanticsOnClick {
            textLayoutResult
              ?.getOffsetForPosition(Offset.Zero)
              ?.let(currentOnClick) != null
          }
        },
      style = styleToRender,
      softWrap = softWrap,
      overflow = overflow,
      onTextLayout = {
        textLayoutResult = it
        onTextLayout(it)
      },
      maxLines = maxLines
    )
  } else {
    BasicText(
      text = textWithFeatureSpan,
      modifier = modifier,
      style = styleToRender,
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

private fun AnnotatedString.withGlobalFontFeatureIfNeeded(
  fontFeatureSettings: String?,
): AnnotatedString {
  if (fontFeatureSettings.isNullOrBlank()) return this
  val hasFontFeatureSpan = spanStyles.any { !it.item.fontFeatureSettings.isNullOrBlank() }
  if (hasFontFeatureSpan) return this

  return buildAnnotatedString {
    append(this@withGlobalFontFeatureIfNeeded)
    addStyle(
      style = SpanStyle(fontFeatureSettings = fontFeatureSettings),
      start = 0,
      end = this@withGlobalFontFeatureIfNeeded.length
    )
  }
}

private fun AnnotatedString.uppercasePreservingAnnotations(): AnnotatedString {
  val uppercaseText = text.uppercase()
  if (uppercaseText == text) return this
  if (uppercaseText.length != text.length) return this

  val annotations = mutableListOf<AnnotatedString.Range<out AnnotatedString.Annotation>>()
  mapAnnotations {
    annotations.add(it)
    it
  }
  return AnnotatedString(
    text = uppercaseText,
    annotations = annotations
  )
}

private fun AnnotatedString.hasAnnotations(): Boolean {
  var hasAnnotations = false
  mapAnnotations {
    hasAnnotations = true
    it
  }
  return hasAnnotations
}

private typealias TextClickPosition = Int
