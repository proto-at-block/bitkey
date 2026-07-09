package build.wallet.ui.components.amount

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.label.AutoResizedLabel
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.LabelTreatment.Disabled
import build.wallet.ui.components.label.LabelTreatment.Primary
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.components.label.loadingScrim
import build.wallet.ui.components.layout.MeasureWithoutPlacement
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

private const val POSITION_ANIMATION_DURATION_MS = 220
private const val VISIBILITY_ANIMATION_DURATION_MS = 140
private const val EXIT_SCALE = 0.82f
private const val DIGIT_MATCH_WEIGHT = 1
private const val NON_DIGIT_MATCH_WEIGHT = 3
private val ENTRY_TRANSLATION_Y = 16.dp
private val SEPARATOR_UNDER_TRANSLATION_Y = 12.dp

@Composable
fun AnimatedHeroAmount(
  modifier: Modifier = Modifier,
  primaryAmount: String,
  primaryAmountGhostedSubstringRange: IntRange?,
  primaryAmountAnimationResetKey: Any = Unit,
  primaryAmountLabelType: LabelType = LabelType.Display2,
  contextLine: String?,
  contextLineTreatment: LabelTreatment = LabelTreatment.Secondary,
  hideBalance: Boolean = false,
  disabled: Boolean = false,
  centerContent: Boolean = false,
  onSwapClick: (() -> Unit)? = null,
  isLoading: Boolean = false,
) {
  val shouldUseStartAlignment = !centerContent
  val primaryTreatment = if (disabled) Disabled else Primary
  val primaryAmountMinHeight =
    heroAmountMinHeight(primaryAmountLabelType, primaryTreatment)
  HeroAmountContainer(
    modifier = modifier,
    contextLine = contextLine,
    contextLineTreatment = contextLineTreatment,
    hideBalance = hideBalance,
    disabled = disabled,
    centerContent = centerContent,
    onSwapClick = onSwapClick,
    isLoading = isLoading,
    topContent = {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(primaryAmountMinHeight)
          .wrapContentSize(
            align = if (shouldUseStartAlignment) Alignment.CenterStart else Alignment.Center
          )
          .loadingScrim(isLoading)
      ) {
        if (isLoading) {
          MeasureWithoutPlacement {
            AutoResizedLabel(
              text = AnnotatedString("$000,000.00"),
              type = primaryAmountLabelType,
              treatment = Primary
            )
          }
        }
        key(primaryAmountAnimationResetKey) {
          AnimatedAmountLabel(
            modifier = Modifier.fillMaxWidth(),
            text = primaryAmount,
            ghostedSubstringRange = primaryAmountGhostedSubstringRange,
            type = primaryAmountLabelType,
            treatment = primaryTreatment,
            alignStart = shouldUseStartAlignment
          )
        }
      }
    }
  )
}

@Composable
private fun heroAmountMinHeight(
  primaryAmountLabelType: LabelType,
  treatment: LabelTreatment,
) = with(LocalDensity.current) {
  WalletTheme.labelStyle(
    type = primaryAmountLabelType,
    treatment = treatment
  ).lineHeight.toDp()
}

@Composable
private fun AnimatedAmountLabel(
  text: String,
  ghostedSubstringRange: IntRange?,
  type: LabelType,
  treatment: LabelTreatment,
  alignStart: Boolean,
  modifier: Modifier = Modifier,
) {
  val textMeasurer = rememberTextMeasurer()
  val density = LocalDensity.current
  val entryTranslationYPx = remember(density) { with(density) { ENTRY_TRANSLATION_Y.toPx() } }
  val separatorUnderTranslationYPx = remember(density) {
    with(density) { SEPARATOR_UNDER_TRANSLATION_Y.toPx() }
  }
  val style = WalletTheme.labelStyle(type = type, treatment = treatment)
  val baseColor = style.color
  val ghostedColor = WalletTheme.colors.foreground30

  BoxWithConstraints(
    modifier =
      modifier
        .semantics {
          contentDescription = text
        }
  ) {
    val maxWidthPx = remember(maxWidth, density) {
      with(density) {
        if (maxWidth.value.isFinite()) maxWidth.toPx() else Float.POSITIVE_INFINITY
      }
    }
    val layout = remember(text, ghostedSubstringRange, style) {
      measureAnimatedAmountLayout(
        text = text,
        ghostedSubstringRange = ghostedSubstringRange,
        style = style,
        textMeasurer = textMeasurer
      )
    }

    val fitScale = remember(layout.width, maxWidthPx) {
      if (layout.width == 0f || !maxWidthPx.isFinite()) {
        1f
      } else {
        min(1f, maxWidthPx / layout.width)
      }
    }
    val maxAnimatedTranslationPx = maxOf(entryTranslationYPx, separatorUnderTranslationYPx)

    val glyphStates = remember { mutableStateListOf<AnimatedAmountGlyphState>() }
    var nextGlyphId by remember { mutableLongStateOf(0L) }

    LaunchedEffect(
      text,
      ghostedSubstringRange?.first,
      ghostedSubstringRange?.last,
      layout.width,
      layout.height
    ) {
      if (glyphStates.isEmpty()) {
        layout.characters.forEachIndexed { index, character ->
          glyphStates +=
            AnimatedAmountGlyphState(
              id = nextGlyphId++,
              char = character.char,
              order = index,
              targetLeft = character.left,
              settledLeft = character.left,
              underMotionStartLeft = character.left,
              ghosted = character.ghosted,
              layoutResult = character.layoutResult,
              entering = false,
              movesUnderNeighbor = false
            )
        }
      } else {
        updateAnimatedAmountGlyphs(
          glyphStates = glyphStates,
          nextGlyphId = { nextGlyphId++ },
          targetLayout = layout
        )
      }
    }

    glyphStates.forEach { glyph ->
      key(glyph.id, glyph.exiting) {
        LaunchedEffect(glyph.id, glyph.entering) {
          if (glyph.entering) {
            glyph.targetLeft = glyph.settledLeft
            glyph.entering = false
          }
        }

        LaunchedEffect(glyph.id, glyph.exiting) {
          if (glyph.exiting) {
            delay(POSITION_ANIMATION_DURATION_MS.toLong())
            if (glyph.exiting) {
              glyphStates.remove(glyph)
            }
          }
        }
      }
    }

    val renderedGlyphs =
      if (glyphStates.isEmpty()) {
        layout.characters.map { character ->
          RenderedGlyph(
            layoutResult = character.layoutResult,
            left = character.left,
            scale = 1f,
            translateY = 0f,
            alpha = 1f,
            color = if (character.ghosted) ghostedColor else baseColor,
            exiting = false
          )
        }
      } else {
        glyphStates.map { glyph ->
          key(glyph.id) {
            glyph.toRenderedGlyph(
              baseColor = baseColor,
              ghostedColor = ghostedColor,
              entryTranslationYPx = entryTranslationYPx,
              separatorUnderTranslationYPx = separatorUnderTranslationYPx
            )
          }
        }
      }

    if (layout.characters.isEmpty()) {
      Spacer(Modifier.height(0.dp))
    } else {
      Spacer(
        modifier =
          Modifier
            .fillMaxWidth()
            .height(with(density) { ((layout.height + maxAnimatedTranslationPx) * fitScale).toDp() })
            .drawBehind {
              val startX =
                animatedAmountStartX(
                  containerWidth = size.width,
                  contentWidth = layout.width * fitScale,
                  alignStart = alignStart
                )

              translate(left = startX, top = 0f) {
                scale(scaleX = fitScale, scaleY = fitScale, pivot = Offset.Zero) {
                  renderedGlyphs
                    .sortedByDescending { it.exiting }
                    .forEach { glyph ->
                      val pivot =
                        Offset(
                          x = glyph.left + glyph.layoutResult.size.width / 2f,
                          y = layout.height / 2f
                        )
                      translate(top = glyph.translateY) {
                        scale(
                          scaleX = glyph.scale,
                          scaleY = glyph.scale,
                          pivot = pivot
                        ) {
                          drawText(
                            textLayoutResult = glyph.layoutResult,
                            color = glyph.color.copy(alpha = glyph.alpha),
                            topLeft = Offset(x = glyph.left, y = 0f)
                          )
                        }
                      }
                    }
                }
              }
            }
      )
    }
  }
}

private class AnimatedAmountGlyphState(
  val id: Long,
  char: Char,
  order: Int,
  targetLeft: Float,
  settledLeft: Float,
  underMotionStartLeft: Float,
  ghosted: Boolean,
  layoutResult: TextLayoutResult,
  entering: Boolean,
  movesUnderNeighbor: Boolean,
) {
  var char by mutableStateOf(char)
  var order by mutableIntStateOf(order)
  var targetLeft by mutableStateOf(targetLeft)
  var settledLeft by mutableStateOf(settledLeft)
  var underMotionStartLeft by mutableStateOf(underMotionStartLeft)
  var ghosted by mutableStateOf(ghosted)
  var layoutResult by mutableStateOf(layoutResult)
  var entering by mutableStateOf(entering)
  var exiting by mutableStateOf(false)
  var movesUnderNeighbor by mutableStateOf(movesUnderNeighbor)
}

@Composable
private fun AnimatedAmountGlyphState.toRenderedGlyph(
  baseColor: Color,
  ghostedColor: Color,
  entryTranslationYPx: Float,
  separatorUnderTranslationYPx: Float,
): RenderedGlyph {
  val left by animateFloatAsState(
    targetValue = targetLeft,
    animationSpec = tween(
      durationMillis = POSITION_ANIMATION_DURATION_MS,
      easing = FastOutSlowInEasing
    ),
    label = "amount-left"
  )
  val alpha by animateFloatAsState(
    targetValue = glyphAlphaTarget(),
    animationSpec = tween(durationMillis = VISIBILITY_ANIMATION_DURATION_MS),
    label = "amount-alpha"
  )
  val scale by animateFloatAsState(
    targetValue = glyphScaleTarget(),
    animationSpec = tween(
      durationMillis = POSITION_ANIMATION_DURATION_MS,
      easing = FastOutSlowInEasing
    ),
    label = "amount-scale"
  )
  val entryTranslateY by animateFloatAsState(
    targetValue = glyphEntryTranslationTarget(entryTranslationYPx),
    animationSpec = tween(
      durationMillis = POSITION_ANIMATION_DURATION_MS,
      easing = FastOutSlowInEasing
    ),
    label = "amount-translateY"
  )
  val separatorTranslateY = separatorTranslateY(left, separatorUnderTranslationYPx)
  val color by animateColorAsState(
    targetValue = if (ghosted) ghostedColor else baseColor,
    animationSpec = tween(durationMillis = VISIBILITY_ANIMATION_DURATION_MS),
    label = "amount-color"
  )
  return RenderedGlyph(
    layoutResult = layoutResult,
    left = left,
    scale = scale,
    translateY = maxOf(entryTranslateY, separatorTranslateY),
    alpha = alpha,
    color = color,
    exiting = exiting
  )
}

private fun AnimatedAmountGlyphState.glyphAlphaTarget(): Float {
  return when {
    exiting -> 0f
    entering -> 0f
    else -> 1f
  }
}

private fun AnimatedAmountGlyphState.glyphScaleTarget(): Float {
  return if (exiting) EXIT_SCALE else 1f
}

private fun AnimatedAmountGlyphState.glyphEntryTranslationTarget(
  entryTranslationYPx: Float,
): Float {
  return animatedAmountVerticalTranslationTarget(
    entering = entering,
    exiting = exiting,
    entryTranslationYPx = entryTranslationYPx
  )
}

internal fun animatedAmountVerticalTranslationTarget(
  entering: Boolean,
  exiting: Boolean,
  entryTranslationYPx: Float,
): Float {
  return if (entering || exiting) entryTranslationYPx else 0f
}

private fun AnimatedAmountGlyphState.separatorTranslateY(
  left: Float,
  separatorUnderTranslationYPx: Float,
): Float {
  if (!movesUnderNeighbor) return 0f

  return separatorUnderTranslationYPx *
    separatorUnderCurve(
      progressForHorizontalMotion(
        start = underMotionStartLeft,
        target = targetLeft,
        current = left
      )
    )
}

private data class RenderedGlyph(
  val layoutResult: TextLayoutResult,
  val left: Float,
  val scale: Float,
  val translateY: Float,
  val alpha: Float,
  val color: Color,
  val exiting: Boolean,
)

private data class AnimatedAmountLayout(
  val text: String,
  val width: Float,
  val height: Float,
  val characters: List<AnimatedAmountCharacter>,
)

private data class AnimatedAmountCharacter(
  val char: Char,
  val order: Int,
  val left: Float,
  val ghosted: Boolean,
  val layoutResult: TextLayoutResult,
)

private data class GlyphEntryMotion(
  val startLeft: Float,
)

private fun measureAnimatedAmountLayout(
  text: String,
  ghostedSubstringRange: IntRange?,
  style: TextStyle,
  textMeasurer: TextMeasurer,
): AnimatedAmountLayout {
  if (text.isEmpty()) {
    return AnimatedAmountLayout(
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

  val characters =
    text.mapIndexed { index, char ->
      val isGhosted = ghostedSubstringRange?.contains(index) == true
      val characterBoundsForIndex = characterBounds[index]
      AnimatedAmountCharacter(
        char = char,
        order = index,
        left = characterBoundsForIndex.left,
        ghosted = isGhosted,
        layoutResult =
          textMeasurer.measure(
            text = AnnotatedString(char.toString()),
            style = style,
            maxLines = 1,
            softWrap = false
          )
      )
    }

  return AnimatedAmountLayout(
    text = text,
    width = fullTextLayout.size.width.toFloat(),
    height = fullTextLayout.size.height.toFloat(),
    characters = characters
  )
}

private fun updateAnimatedAmountGlyphs(
  glyphStates: MutableList<AnimatedAmountGlyphState>,
  nextGlyphId: () -> Long,
  targetLayout: AnimatedAmountLayout,
) {
  val activeGlyphs =
    glyphStates
      .filterNot { it.exiting }
      .sortedBy { it.order }

  val matches =
    matchCharacterIndices(
      previous = activeGlyphs.joinToString(separator = "") { it.char.toString() },
      current = targetLayout.text
    )
  val currentMatchesByPreviousIndex = matches.associate { it.previousIndex to it.currentIndex }
  val previousMatchesByCurrentIndex = matches.associate { it.currentIndex to it.previousIndex }

  activeGlyphs.forEachIndexed { previousIndex, glyph ->
    val currentIndex = currentMatchesByPreviousIndex[previousIndex]
    if (currentIndex == null) {
      glyph.exiting = true
    } else {
      val currentCharacter = targetLayout.characters[currentIndex]
      val shouldMoveSeparatorUnderNeighbor =
        glyph.char.isAnimatedSeparator() && currentIndex != previousIndex
      val previousLeft = glyph.targetLeft
      glyph.char = currentCharacter.char
      glyph.order = currentIndex
      glyph.targetLeft = currentCharacter.left
      glyph.settledLeft = currentCharacter.left
      glyph.underMotionStartLeft = previousLeft
      glyph.ghosted = currentCharacter.ghosted
      glyph.layoutResult = currentCharacter.layoutResult
      glyph.exiting = false
      glyph.entering = false
      glyph.movesUnderNeighbor = shouldMoveSeparatorUnderNeighbor
    }
  }

  targetLayout.characters.forEachIndexed { currentIndex, character ->
    if (previousMatchesByCurrentIndex[currentIndex] == null) {
      val entryMotion =
        entryMotionForInsertedGlyph(
          insertedCharacter = character
        )
      glyphStates +=
        AnimatedAmountGlyphState(
          id = nextGlyphId(),
          char = character.char,
          order = currentIndex,
          targetLeft = entryMotion.startLeft,
          settledLeft = character.left,
          underMotionStartLeft = entryMotion.startLeft,
          ghosted = character.ghosted,
          layoutResult = character.layoutResult,
          entering = true,
          movesUnderNeighbor = false
        )
    }
  }
}

private fun entryMotionForInsertedGlyph(
  insertedCharacter: AnimatedAmountCharacter,
): GlyphEntryMotion {
  return GlyphEntryMotion(startLeft = insertedCharacter.left)
}

private fun Char.isAnimatedSeparator(): Boolean {
  return this == ',' || this == '.' || this == ' ' || this == '\u00A0' || this == '\u202F'
}

private fun progressForHorizontalMotion(
  start: Float,
  target: Float,
  current: Float,
): Float {
  val distance = abs(target - start)
  if (distance == 0f) return 1f
  return (abs(current - start) / distance).coerceIn(0f, 1f)
}

private fun separatorUnderCurve(progress: Float): Float {
  return sin(progress * PI).toFloat()
}

internal fun animatedAmountStartX(
  containerWidth: Float,
  contentWidth: Float,
  alignStart: Boolean,
): Float {
  return if (alignStart) {
    0f
  } else {
    (containerWidth - contentWidth) / 2f
  }
}

internal data class CharacterIndexMatch(
  val previousIndex: Int,
  val currentIndex: Int,
)

internal fun matchCharacterIndices(
  previous: String,
  current: String,
): List<CharacterIndexMatch> {
  if (previous.isEmpty() || current.isEmpty()) {
    return emptyList()
  }

  val bestMatchScore = Array(previous.length + 1) { IntArray(current.length + 1) }
  for (previousIndex in previous.lastIndex downTo 0) {
    for (currentIndex in current.lastIndex downTo 0) {
      bestMatchScore[previousIndex][currentIndex] =
        if (previous[previousIndex] == current[currentIndex]) {
          bestMatchScore[previousIndex + 1][currentIndex + 1] +
            previous[previousIndex].animatedAmountMatchWeight()
        } else {
          maxOf(
            bestMatchScore[previousIndex + 1][currentIndex],
            bestMatchScore[previousIndex][currentIndex + 1]
          )
        }
    }
  }

  val matches = mutableListOf<CharacterIndexMatch>()
  var previousIndex = 0
  var currentIndex = 0
  while (previousIndex < previous.length && currentIndex < current.length) {
    when {
      previous[previousIndex] == current[currentIndex] &&
        bestMatchScore[previousIndex][currentIndex] ==
        bestMatchScore[previousIndex + 1][currentIndex + 1] +
        previous[previousIndex].animatedAmountMatchWeight() -> {
        matches += CharacterIndexMatch(previousIndex, currentIndex)
        previousIndex += 1
        currentIndex += 1
      }

      bestMatchScore[previousIndex][currentIndex + 1] >=
        bestMatchScore[previousIndex + 1][currentIndex] -> {
        currentIndex += 1
      }

      else -> {
        previousIndex += 1
      }
    }
  }

  return matches
}

private fun Char.animatedAmountMatchWeight(): Int {
  return if (isDigit()) {
    DIGIT_MATCH_WEIGHT
  } else {
    NON_DIGIT_MATCH_WEIGHT
  }
}
