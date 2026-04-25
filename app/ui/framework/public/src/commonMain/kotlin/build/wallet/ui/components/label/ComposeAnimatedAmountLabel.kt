package build.wallet.ui.components.label

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnit.Companion.Unspecified
import androidx.compose.ui.unit.TextUnitType
import kotlin.math.max
import kotlin.math.min

private const val SLIDE_DURATION_MS = 350
private const val FADE_DURATION_MS = 120
private const val FADE_IN_DELAY_MS = 100
private const val CHARACTER_START_OFFSET_MS = 30
private val SLIDE_EASING = androidx.compose.animation.core.FastOutSlowInEasing

@Composable
internal fun ComposeAnimatedAmountAutoResizedLabel(
  amount: AnimatedAmount,
  modifier: Modifier = Modifier,
  style: TextStyle,
  alignment: TextAlign = TextAlign.Start,
  allowFontScaling: Boolean = true,
  animate: Boolean = true,
  minTextSize: TextUnit = Unspecified,
) {
  val density = LocalDensity.current
  val textMeasurer = rememberTextMeasurer()
  val fontScale = density.fontScale
  val adjustedStyle = remember(style, allowFontScaling, fontScale) {
    if (allowFontScaling) {
      style
    } else {
      style.copy(
        fontSize = style.fontSize / fontScale,
        lineHeight = style.lineHeight / fontScale
      )
    }
  }
  val minScale = remember(adjustedStyle.fontSize, minTextSize, allowFontScaling, fontScale) {
    resolvedAnimatedAmountMinScale(
      styleFontSize = adjustedStyle.fontSize,
      minTextSize = minTextSize,
      allowFontScaling = allowFontScaling,
      fontScale = fontScale
    )
  }
  val transitionState = remember { ComposeAnimatedAmountTransitionState(amount) }

  LaunchedEffect(amount.text, amount.value, amount.animationKey, animate) {
    transitionState.update(amount = amount, animate = animate)
  }

  val currentLayout = remember(transitionState.current.text, adjustedStyle) {
    measureComposeAnimatedAmountLayout(
      text = transitionState.current.text,
      style = adjustedStyle,
      textMeasurer = textMeasurer
    )
  }
  val previousLayout = remember(transitionState.previous?.text, adjustedStyle) {
    transitionState.previous?.let { previousAmount ->
      measureComposeAnimatedAmountLayout(
        text = previousAmount.text,
        style = adjustedStyle,
        textMeasurer = textMeasurer
      )
    }
  }
  val currentMask by rememberUpdatedState(transitionState.mask)
  val currentElapsedMs by rememberUpdatedState(transitionState.elapsedMs.value)
  val currentDirection by rememberUpdatedState(transitionState.direction)
  val currentEnterDurationMs by rememberUpdatedState(transitionState.enterDurationMs)
  val currentExitDurationMs by rememberUpdatedState(transitionState.exitDurationMs)

  BoxWithConstraints(
    modifier = modifier.semantics { contentDescription = amount.text },
    contentAlignment = animatedAmountBoxAlignment(alignment)
  ) {
    val maxWidthPx = remember(maxWidth, density) {
      with(density) {
        if (maxWidth.value.isFinite()) {
          maxWidth.toPx()
        } else {
          Float.POSITIVE_INFINITY
        }
      }
    }
    val contentWidth = max(currentLayout.width, previousLayout?.width ?: 0f)
    val contentHeight = max(currentLayout.height, previousLayout?.height ?: 0f)
    val rawScale =
      if (minScale != null && contentWidth > 0f && maxWidthPx.isFinite()) {
        min(1f, maxWidthPx / contentWidth)
      } else {
        1f
      }
    val fitScale = max(rawScale, minScale ?: 1f).coerceAtMost(1f)

    Spacer(
      modifier = Modifier
        .width(with(density) { (contentWidth * fitScale).toDp() })
        .height(with(density) { (contentHeight * fitScale).toDp() })
        .drawBehind {
          if (contentWidth == 0f || contentHeight == 0f) return@drawBehind

          scale(scaleX = fitScale, scaleY = fitScale, pivot = Offset.Zero) {
            previousLayout?.let { layout ->
              drawAnimatedAmountLayout(
                layout = layout,
                xOffset = animatedAmountHorizontalOffset(alignment, contentWidth, layout.width),
                animatedCharacters = currentMask?.exitAnimatedCharacters,
                drawStaticCharacters = false,
                elapsedMs = currentElapsedMs,
                durationMs = currentExitDurationMs,
                direction = currentDirection,
                phase = AnimatedAmountPhase.Exit,
                color = adjustedStyle.color
              )
            }
            drawAnimatedAmountLayout(
              layout = currentLayout,
              xOffset = animatedAmountHorizontalOffset(alignment, contentWidth, currentLayout.width),
              animatedCharacters = currentMask?.enterAnimatedCharacters,
              drawStaticCharacters = true,
              elapsedMs = currentElapsedMs,
              durationMs = currentEnterDurationMs,
              direction = currentDirection,
              phase = AnimatedAmountPhase.Enter,
              color = adjustedStyle.color
            )
          }
        }
    )
  }
}

private class ComposeAnimatedAmountTransitionState(
  initialAmount: AnimatedAmount,
) {
  var current by mutableStateOf(initialAmount)
  var previous by mutableStateOf<AnimatedAmount?>(null)
  var mask by mutableStateOf<AnimatedAmountTransitionMask?>(null)
  var direction by mutableStateOf(AnimatedAmountDirection.Increase)
  var enterDurationMs by mutableStateOf(0)
  var exitDurationMs by mutableStateOf(0)
  val elapsedMs = Animatable(0f)

  suspend fun update(
    amount: AnimatedAmount,
    animate: Boolean,
  ) {
    val previousAmount = current
    if (amount.text == current.text && amount.animationKey == current.animationKey) {
      return
    }

    if (!animate || amount.animationKey != previousAmount.animationKey) {
      elapsedMs.stop()
      elapsedMs.snapTo(0f)
      previous = null
      current = amount
      mask = null
      enterDurationMs = 0
      exitDurationMs = 0
      return
    }

    val nextMask = buildAnimatedAmountTransitionMask(previousAmount.text, amount.text)
    val nextEnterDuration = animatedAmountDurationMs(amount.text, nextMask.enterAnimatedCharacters)
    val nextExitDuration = animatedAmountDurationMs(previousAmount.text, nextMask.exitAnimatedCharacters)

    if (nextEnterDuration == 0 && nextExitDuration == 0) {
      elapsedMs.stop()
      elapsedMs.snapTo(0f)
      previous = null
      current = amount
      mask = null
      enterDurationMs = 0
      exitDurationMs = 0
      return
    }

    previous = previousAmount
    current = amount
    mask = nextMask
    direction =
      if (amount.value > previousAmount.value) {
        AnimatedAmountDirection.Increase
      } else {
        AnimatedAmountDirection.Decrease
      }
    enterDurationMs = nextEnterDuration
    exitDurationMs = nextExitDuration

    elapsedMs.stop()
    elapsedMs.snapTo(0f)
    val maxDurationMs = max(nextEnterDuration, nextExitDuration)
    elapsedMs.animateTo(
      targetValue = maxDurationMs.toFloat(),
      animationSpec = tween(durationMillis = maxDurationMs, easing = LinearEasing)
    )

    previous = null
    mask = null
    enterDurationMs = 0
    exitDurationMs = 0
  }
}

private enum class AnimatedAmountDirection {
  Increase,
  Decrease,
}

private enum class AnimatedAmountPhase {
  Enter,
  Exit,
}

internal data class AnimatedAmountTransitionMask(
  val enterAnimatedCharacters: List<Boolean>,
  val exitAnimatedCharacters: List<Boolean>,
)

private data class ComposeAnimatedAmountLayout(
  val text: String,
  val width: Float,
  val height: Float,
  val characters: List<ComposeAnimatedAmountCharacter>,
)

private data class ComposeAnimatedAmountCharacter(
  val left: Float,
  val layoutResult: TextLayoutResult,
)

private fun measureComposeAnimatedAmountLayout(
  text: String,
  style: TextStyle,
  textMeasurer: TextMeasurer,
): ComposeAnimatedAmountLayout {
  if (text.isEmpty()) {
    return ComposeAnimatedAmountLayout(
      text = text,
      width = 0f,
      height = 0f,
      characters = emptyList()
    )
  }

  val fullTextLayout =
    textMeasurer.measure(
      text = AnnotatedString(text),
      style = style,
      maxLines = 1,
      softWrap = false
    )
  val characterBounds = List(text.length) { index -> fullTextLayout.getBoundingBox(index) }

  return ComposeAnimatedAmountLayout(
    text = text,
    width = fullTextLayout.size.width.toFloat(),
    height = fullTextLayout.size.height.toFloat(),
    characters =
      text.mapIndexed { index, character ->
        ComposeAnimatedAmountCharacter(
          left = characterBounds[index].left,
          layoutResult =
            textMeasurer.measure(
              text = AnnotatedString(character.toString()),
              style = style,
              maxLines = 1,
              softWrap = false
            )
        )
      }
  )
}

internal fun buildAnimatedAmountTransitionMask(
  previous: String,
  current: String,
): AnimatedAmountTransitionMask {
  if (previous.isEmpty() || current.isEmpty()) {
    return AnimatedAmountTransitionMask(
      enterAnimatedCharacters = List(current.length) { true },
      exitAnimatedCharacters = List(previous.length) { true }
    )
  }

  val prefixLength =
    previous.zip(current)
      .takeWhile { (previousChar, currentChar) -> previousChar == currentChar }
      .count()
  val maxSuffixLength = min(previous.length, current.length) - prefixLength
  val suffixLength =
    (0 until maxSuffixLength)
      .takeWhile { offset ->
        previous[previous.lastIndex - offset] == current[current.lastIndex - offset]
      }
      .count()

  return AnimatedAmountTransitionMask(
    enterAnimatedCharacters = List(current.length) { index ->
      index >= prefixLength && index < current.length - suffixLength
    },
    exitAnimatedCharacters = List(previous.length) { index ->
      index >= prefixLength && index < previous.length - suffixLength
    }
  )
}

private fun animatedAmountDurationMs(
  text: String,
  animatedCharacters: List<Boolean>,
): Int {
  val animatedCount = animatedAmountAnimatedCharacterCount(text, animatedCharacters)
  if (animatedCount == 0) return 0
  return SLIDE_DURATION_MS + CHARACTER_START_OFFSET_MS * (animatedCount - 1).coerceAtLeast(0)
}

private fun animatedAmountAnimatedCharacterCount(
  text: String,
  animatedCharacters: List<Boolean>,
): Int {
  var animatedCount = 0
  for (index in text.indices) {
    val shouldAnimate = animatedCharacters.getOrElse(index) { true }
    if (shouldAnimate && (index == text.lastIndex || text[index].animatesIndependently())) {
      animatedCount += 1
    }
  }
  return animatedCount
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAnimatedAmountLayout(
  layout: ComposeAnimatedAmountLayout,
  xOffset: Float,
  animatedCharacters: List<Boolean>?,
  drawStaticCharacters: Boolean,
  elapsedMs: Float,
  durationMs: Int,
  direction: AnimatedAmountDirection,
  phase: AnimatedAmountPhase,
  color: Color,
) {
  val resolvedAnimatedCharacters = animatedCharacters ?: List(layout.text.length) { false }
  val animatedCharacterGroups = animatedAmountCharacterGroups(layout.text, resolvedAnimatedCharacters)

  layout.characters.forEachIndexed { index, character ->
    val shouldAnimate = resolvedAnimatedCharacters.getOrElse(index) { false }
    if (!shouldAnimate && !drawStaticCharacters) {
      return@forEachIndexed
    }

    val drawState =
      if (!shouldAnimate || durationMs == 0) {
        AnimatedAmountDrawState(alpha = 1f, translateY = 0f)
      } else {
        animatedAmountDrawState(
          elapsedMs = elapsedMs,
          durationMs = durationMs,
          groupIndex = animatedCharacterGroups[index],
          textHeight = layout.height,
          direction = direction,
          phase = phase
        )
      }

    if (drawState.alpha <= 0f) {
      return@forEachIndexed
    }

    drawText(
      textLayoutResult = character.layoutResult,
      color = color.copy(alpha = drawState.alpha),
      topLeft = Offset(x = xOffset + character.left, y = drawState.translateY)
    )
  }
}

private data class AnimatedAmountDrawState(
  val alpha: Float,
  val translateY: Float,
)

private fun animatedAmountDrawState(
  elapsedMs: Float,
  durationMs: Int,
  groupIndex: Int,
  textHeight: Float,
  direction: AnimatedAmountDirection,
  phase: AnimatedAmountPhase,
): AnimatedAmountDrawState {
  val clampedElapsedMs = elapsedMs.coerceAtMost(durationMs.toFloat())
  val adjustedTime = (clampedElapsedMs - groupIndex * CHARACTER_START_OFFSET_MS).coerceAtLeast(0f)
  val slideFraction = (adjustedTime / SLIDE_DURATION_MS).coerceIn(0f, 1f)
  val fadeFraction =
    when (phase) {
      AnimatedAmountPhase.Enter ->
        ((adjustedTime - FADE_IN_DELAY_MS) / FADE_DURATION_MS).coerceIn(0f, 1f)
      AnimatedAmountPhase.Exit ->
        (adjustedTime / FADE_DURATION_MS).coerceIn(0f, 1f)
    }
  val alpha =
    when (phase) {
      AnimatedAmountPhase.Enter -> fadeFraction
      AnimatedAmountPhase.Exit -> 1f - fadeFraction
    }
  val baseTranslation =
    when (phase) {
      AnimatedAmountPhase.Enter ->
        (1f - SLIDE_EASING.transform(slideFraction)) * (textHeight / 2f)
      AnimatedAmountPhase.Exit ->
        SLIDE_EASING.transform(slideFraction) * (-textHeight / 2f)
    }
  val translatedY =
    if (direction == AnimatedAmountDirection.Decrease) {
      -baseTranslation
    } else {
      baseTranslation
    }

  return AnimatedAmountDrawState(
    alpha = alpha,
    translateY = translatedY
  )
}

private fun animatedAmountCharacterGroups(
  text: String,
  animatedCharacters: List<Boolean>,
): IntArray {
  val groupIndices = IntArray(text.length)
  var currentGroupIndex = 0
  for (index in text.indices) {
    groupIndices[index] = currentGroupIndex
    val shouldAnimate = animatedCharacters.getOrElse(index) { true }
    if (shouldAnimate && (index == text.lastIndex || text[index].animatesIndependently())) {
      currentGroupIndex += 1
    }
  }
  return groupIndices
}

private fun Char.animatesIndependently(): Boolean {
  return this != ',' && this != '.'
}

private fun animatedAmountBoxAlignment(
  alignment: TextAlign,
): Alignment {
  return when (alignment) {
    TextAlign.Center -> Alignment.Center
    TextAlign.End, TextAlign.Right -> Alignment.CenterEnd
    else -> Alignment.CenterStart
  }
}

internal fun animatedAmountHorizontalOffset(
  alignment: TextAlign,
  contentWidth: Float,
  layoutWidth: Float,
): Float {
  val remainingWidth = (contentWidth - layoutWidth).coerceAtLeast(0f)
  return when (alignment) {
    TextAlign.Center -> remainingWidth / 2f
    TextAlign.End, TextAlign.Right -> remainingWidth
    else -> 0f
  }
}

internal fun resolvedAnimatedAmountMinScale(
  styleFontSize: TextUnit,
  minTextSize: TextUnit,
  allowFontScaling: Boolean = true,
  fontScale: Float = 1f,
): Float? {
  if (styleFontSize.type != TextUnitType.Sp || minTextSize.type != TextUnitType.Sp) {
    return null
  }

  if (styleFontSize.value <= 0f || minTextSize.value <= 0f) {
    return null
  }

  val resolvedMinTextSize =
    if (allowFontScaling || fontScale <= 0f) {
      minTextSize.value
    } else {
      minTextSize.value / fontScale
    }

  return (resolvedMinTextSize / styleFontSize.value).coerceIn(0f, 1f)
}
