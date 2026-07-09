package build.wallet.ui.components.label

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint.FontMetrics
import android.graphics.Typeface
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec.AT_MOST
import android.view.View.MeasureSpec.EXACTLY
import android.view.View.MeasureSpec.UNSPECIFIED
import android.view.animation.PathInterpolator
import kotlin.collections.ArrayDeque
import kotlin.math.absoluteValue

internal class AnimatedAmountTextView(
  context: Context,
) : View(context) {
  data class Amount(
    val text: String,
    val value: Long,
    val animationKey: Long = 0L,
  )

  private val paint =
    TextPaint().apply {
      isAntiAlias = true
      color = Color.BLACK
      textSize = 14f * resources.displayMetrics.scaledDensity
      typeface = Typeface.DEFAULT
    }

  private var originalTextSizeInPx: Float = paint.textSize

  var textColor: Int
    get() = paint.color
    set(value) {
      if (value == paint.color) return
      paint.color = value
      measureText()
      invalidate()
    }

  var textSizeInPx: Float
    get() = paint.textSize
    set(value) {
      if (value == originalTextSizeInPx) return
      originalTextSizeInPx = value
      paint.textSize = value
      measureText()
      invalidate()
    }

  var minTextSizeInPx: Float? = null
    set(value) {
      if (field == value) return
      field = value
      paint.textSize = originalTextSizeInPx
      measureText()
      invalidate()
    }

  var letterSpacing: Float
    get() = paint.letterSpacing
    set(value) {
      if (value == paint.letterSpacing) return
      paint.letterSpacing = value
      paint.textSize = originalTextSizeInPx
      measureText()
      invalidate()
    }

  var typeface: Typeface
    get() = paint.typeface
    set(value) {
      if (value == paint.typeface) return
      paint.typeface = value
      paint.textSize = originalTextSizeInPx
      measureText()
      invalidate()
    }

  var fontFeatureSettings: String?
    get() = paint.fontFeatureSettings
    set(value) {
      if (value == paint.fontFeatureSettings) return
      paint.fontFeatureSettings = value
      paint.textSize = originalTextSizeInPx
      measureText()
      invalidate()
    }

  var gravity: Int = Gravity.START
    set(value) {
      require(value == Gravity.START || value == Gravity.CENTER_HORIZONTAL || value == Gravity.END) {
        "Unsupported gravity: $value"
      }
      if (field == value) return
      field = value
      invalidate()
    }

  var animationsEnabled: Boolean = true

  var animateEvenIfSame: Boolean = false
  private val texts = ArrayDeque<AnimatedText>()

  fun setAmount(amount: Amount?) {
    val didCoalesce = coalesceIntermediateTexts()
    val current = texts.lastOrNull()
    if (shouldKeepCurrentAmount(current, amount)) {
      refreshAfterCoalescing(didCoalesce)
      return
    }

    if (shouldShowAmountStatically(current, amount)) {
      showAmountStatically(amount)
      return
    }

    queueAnimatedAmount(current, amount)
    measureText()
    invalidate()
  }

  private fun shouldKeepCurrentAmount(
    current: AnimatedText?,
    amount: Amount?,
  ): Boolean {
    return !animateEvenIfSame &&
      amount?.text == current?.text &&
      amount?.animationKey == current?.animationKey
  }

  private fun refreshAfterCoalescing(didCoalesce: Boolean) {
    if (!didCoalesce) return
    measureText()
    invalidate()
  }

  private fun shouldShowAmountStatically(
    current: AnimatedText?,
    amount: Amount?,
  ): Boolean {
    return !animationsEnabled || hasAnimationKeyChanged(current, amount)
  }

  private fun hasAnimationKeyChanged(
    current: AnimatedText?,
    amount: Amount?,
  ): Boolean {
    return current != null && amount != null && current.animationKey != amount.animationKey
  }

  private fun queueAnimatedAmount(
    current: AnimatedText?,
    amount: Amount?,
  ) {
    val next = AnimatedText(amount?.text, amount?.value, amount?.animationKey ?: 0L)
    contentDescription = amount?.text
    texts.addLast(next)

    if (current != null && amount != null) {
      startTransition(current, next)
    } else {
      next.showAllCharactersStatically()
    }
  }

  private fun startTransition(
    current: AnimatedText,
    next: AnimatedText,
  ) {
    val transitionMask = buildTransitionMask(current, next)
    val animationDirection = animationDirectionFor(current, next)
    current.configureTransition(
      animatedCharacters = transitionMask.exitAnimatedCharacters,
      drawStaticCharacters = false
    )
    next.configureTransition(
      animatedCharacters = transitionMask.enterAnimatedCharacters,
      drawStaticCharacters = true
    )
    current.exit(animationDirection) {
      texts.remove(it)
      requestLayout()
    }
    next.enter(animationDirection)
  }

  private fun animationDirectionFor(
    current: AnimatedText,
    next: AnimatedText,
  ): AnimatedAmountDirection {
    return animatedAmountDirectionFor(
      previousValue = current.value ?: 0L,
      currentValue = next.value ?: 0L
    )
  }

  private fun coalesceIntermediateTexts(): Boolean {
    var removedAny = false
    while (texts.size > 1) {
      texts.removeFirst().cancelAnimations()
      removedAny = true
    }
    return removedAny
  }

  private fun showAmountStatically(amount: Amount?) {
    contentDescription = amount?.text
    texts.forEach { it.cancelAnimations() }
    texts.clear()
    if (amount != null) {
      val next = AnimatedText(amount.text, amount.value, amount.animationKey)
      next.showAllCharactersStatically()
      texts.addLast(next)
    }
    measureText()
    invalidate()
  }

  private fun measureText(requestLayout: Boolean = true) {
    texts.forEach { it.measure(paint) }
    if (requestLayout) {
      requestLayout()
    }
  }

  override fun onMeasure(
    widthMeasureSpec: Int,
    heightMeasureSpec: Int,
  ) {
    val widthMode = MeasureSpec.getMode(widthMeasureSpec)
    val widthSize = MeasureSpec.getSize(widthMeasureSpec)

    if (minTextSizeInPx != null && (widthMode == EXACTLY || widthMode == AT_MOST)) {
      applyAutoSize(widthSize)
    }

    val width =
      when (widthMode) {
        EXACTLY -> widthSize
        AT_MOST, UNSPECIFIED -> {
          val textWidth = texts.maxOfOrNull { it.width() } ?: 0f
          val desiredWidth = textWidth.toInt() + paddingStart + paddingEnd
          if (widthMode == AT_MOST) desiredWidth.coerceAtMost(widthSize) else desiredWidth
        }
        else -> error("Unexpected width measure spec: $widthMeasureSpec")
      }

    val heightMode = MeasureSpec.getMode(heightMeasureSpec)
    val heightSize = MeasureSpec.getSize(heightMeasureSpec)
    val height =
      when (heightMode) {
        EXACTLY -> heightSize
        AT_MOST, UNSPECIFIED -> {
          val fontHeight = paint.fontMetrics.height().toInt()
          val desiredHeight = fontHeight + paddingTop + paddingBottom
          if (heightMode == AT_MOST) desiredHeight.coerceAtMost(heightSize) else desiredHeight
        }
        else -> error("Unexpected height measure spec: $heightMeasureSpec")
      }

    setMeasuredDimension(width, height)
  }

  private fun applyAutoSize(widthSize: Int) {
    val minSize = minTextSizeInPx ?: return
    val availableWidth = widthSize - paddingStart - paddingEnd
    if (availableWidth <= 0) return

    paint.textSize = originalTextSizeInPx
    measureText(requestLayout = false)

    val initialWidth = texts.maxOfOrNull { it.width() } ?: 0f
    if (initialWidth <= availableWidth) return

    var low = minSize
    var high = originalTextSizeInPx
    var bestSize = minSize

    while (low <= high) {
      val mid = (low + high) / 2f
      paint.textSize = mid
      measureText(requestLayout = false)

      val currentWidth = texts.maxOfOrNull { it.width() } ?: 0f
      if (currentWidth <= availableWidth) {
        bestSize = mid
        low = mid + 0.5f
      } else {
        high = mid - 0.5f
      }

      if (high - low < 0.5f) break
    }

    paint.textSize = bestSize
    measureText(requestLayout = false)
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    texts.forEach { it.draw(canvas, paint) }
  }

  private fun buildTransitionMask(
    current: AnimatedText,
    next: AnimatedText,
  ): TransitionMask {
    val currentText = current.text.orEmpty()
    val nextText = next.text.orEmpty()
    if (currentText.isEmpty() || nextText.isEmpty()) {
      return TransitionMask(
        enterAnimatedCharacters = BooleanArray(nextText.length) { true },
        exitAnimatedCharacters = BooleanArray(currentText.length) { true }
      )
    }

    val prefixLength =
      currentText.zip(nextText)
        .takeWhile { (currentChar, nextChar) -> currentChar == nextChar }
        .count()
    val maxSuffixLength = minOf(currentText.length, nextText.length) - prefixLength
    val suffixLength =
      (0 until maxSuffixLength)
        .takeWhile { offset ->
          currentText[currentText.lastIndex - offset] == nextText[nextText.lastIndex - offset]
        }
        .count()

    return TransitionMask(
      enterAnimatedCharacters = BooleanArray(nextText.length) { index ->
        index >= prefixLength && index < nextText.length - suffixLength
      },
      exitAnimatedCharacters = BooleanArray(currentText.length) { index ->
        index >= prefixLength && index < currentText.length - suffixLength
      }
    )
  }

  private inner class AnimatedText(
    val text: String?,
    val value: Long?,
    val animationKey: Long,
  ) {
    private var textWidth = 0f
    private var textHeight = 0f
    private var fontAscent = 0f
    private var characterXLocations = emptyList<Float>()
    private var animatedCharacters = BooleanArray(0)
    private var drawStaticCharacters = true
    private var enterAnimator: ValueAnimator? = null
    private var exitAnimator: ValueAnimator? = null
    private var animationDirection = AnimatedAmountDirection.Increase

    fun width(): Float = textWidth

    fun cancelAnimations() {
      enterAnimator?.removeAllListeners()
      enterAnimator?.removeAllUpdateListeners()
      enterAnimator?.cancel()
      enterAnimator = null
      exitAnimator?.removeAllListeners()
      exitAnimator?.removeAllUpdateListeners()
      exitAnimator?.cancel()
      exitAnimator = null
    }

    fun configureTransition(
      animatedCharacters: BooleanArray,
      drawStaticCharacters: Boolean,
    ) {
      this.animatedCharacters = animatedCharacters
      this.drawStaticCharacters = drawStaticCharacters
    }

    fun showAllCharactersStatically() {
      val resolvedText = text.orEmpty()
      animatedCharacters = BooleanArray(resolvedText.length) { false }
      drawStaticCharacters = true
    }

    fun measure(paint: TextPaint) {
      val fontMetrics = paint.fontMetrics
      textHeight = fontMetrics.height()
      fontAscent = fontMetrics.ascent.absoluteValue

      val resolvedText = text
      if (resolvedText == null) {
        textWidth = 0f
        characterXLocations = emptyList()
        animatedCharacters = BooleanArray(0)
        return
      }

      textWidth = paint.measureText(resolvedText)
      if (animatedCharacters.size != resolvedText.length) {
        animatedCharacters = BooleanArray(resolvedText.length) { true }
      }
      characterXLocations =
        resolvedText.indices.map { index ->
          paint.getRunAdvance(
            resolvedText,
            0,
            resolvedText.length,
            0,
            resolvedText.length,
            false,
            index
          )
        }
    }

    fun draw(
      canvas: Canvas,
      paint: TextPaint,
    ) {
      val resolvedText = text ?: return
      val isRtl = TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(resolvedText, 0, resolvedText.length)
      val enterProgress = enterAnimator?.elapsedTime() ?: Long.MAX_VALUE
      val exitProgress = exitAnimator?.elapsedTime() ?: 0L
      val xTranslation = textXTranslation()

      var animatedGroupIndex = 0
      for (index in resolvedText.indices) {
        val shouldAnimate = animatedCharacters.getOrElse(index) { true }
        if (!shouldAnimate && !drawStaticCharacters) continue

        val drawState =
          characterDrawState(
            shouldAnimate = shouldAnimate,
            animatedGroupIndex = animatedGroupIndex,
            enterProgress = enterProgress,
            exitProgress = exitProgress
          )

        val x = paddingStart + characterXLocations[index] + xTranslation
        val y = paddingTop + fontAscent + drawState.yTranslation
        paint.alpha = drawState.alpha
        canvas.drawTextRun(
          resolvedText,
          index,
          index + 1,
          0,
          resolvedText.length,
          x,
          y,
          isRtl,
          paint
        )

        if (shouldAnimate && (index == resolvedText.lastIndex || resolvedText[index].animatesIndependently())) {
          animatedGroupIndex++
        }
      }
      paint.alpha = 255
    }

    private fun textXTranslation(): Float {
      return when (gravity) {
        Gravity.START -> 0f
        Gravity.CENTER_HORIZONTAL -> (width - paddingStart - paddingEnd - textWidth) / 2f
        Gravity.END -> width - paddingStart - paddingEnd - textWidth
        else -> 0f
      }
    }

    private fun characterDrawState(
      shouldAnimate: Boolean,
      animatedGroupIndex: Int,
      enterProgress: Long,
      exitProgress: Long,
    ): CharacterDrawState {
      if (!shouldAnimate) {
        return CharacterDrawState(alpha = 255, yTranslation = 0f)
      }

      val enterTimeForIndex = adjustedTimeForIndex(enterProgress, animatedGroupIndex)
      val exitTimeForIndex = adjustedTimeForIndex(exitProgress, animatedGroupIndex)
      val alpha =
        if (exitProgress > 0) {
          calculateExitAlpha(exitTimeForIndex)
        } else {
          calculateEnterAlpha(enterTimeForIndex)
        }
      var yTranslation =
        if (exitProgress > 0) {
          calculateExitTranslation(exitTimeForIndex)
        } else {
          calculateEnterTranslation(enterTimeForIndex)
        }
      if (animationDirection == AnimatedAmountDirection.Decrease) {
        yTranslation = -yTranslation
      }
      return CharacterDrawState(alpha = alpha, yTranslation = yTranslation)
    }

    fun enter(animationDirection: AnimatedAmountDirection) {
      val resolvedText = text ?: return
      this.animationDirection = animationDirection
      if (animatedCharacterCount(resolvedText) == 0) {
        enterAnimator?.cancel()
        enterAnimator = null
        invalidate()
        return
      }
      enterAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = calculateDuration(resolvedText)
        addUpdateListener { invalidate() }
        start()
      }
    }

    fun exit(
      animationDirection: AnimatedAmountDirection,
      onComplete: (AnimatedText) -> Unit,
    ) {
      val resolvedText = text
      if (resolvedText == null) {
        onComplete(this)
        return
      }

      this.animationDirection = animationDirection
      enterAnimator?.cancel()
      if (animatedCharacterCount(resolvedText) == 0) {
        onComplete(this)
        return
      }
      exitAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = calculateDuration(resolvedText)
        addUpdateListener { invalidate() }
        addListener(
          object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
              onComplete(this@AnimatedText)
            }
          }
        )
        start()
      }
    }

    private fun calculateDuration(text: String): Long {
      val animatedCount = animatedCharacterCount(text)
      return SLIDE_DURATION + CHARACTER_START_OFFSET * (animatedCount - 1).coerceAtLeast(0)
    }

    private fun animatedCharacterCount(text: String): Int {
      var animatedCount = 0
      for (index in text.indices) {
        val shouldAnimate = animatedCharacters.getOrElse(index) { true }
        if (shouldAnimate && (index == text.lastIndex || text[index].animatesIndependently())) {
          animatedCount++
        }
      }
      return animatedCount
    }

    private fun calculateEnterAlpha(time: Long): Int {
      val adjustedTime = time - FADE_IN_DELAY
      val fraction = (adjustedTime.toFloat() / FADE_DURATION).coerceIn(0f, 1f)
      return (fraction * 255).toInt()
    }

    private fun calculateExitAlpha(time: Long): Int {
      val fraction = 1f - (time.toFloat() / FADE_DURATION).coerceIn(0f, 1f)
      return (fraction * 255).toInt()
    }

    private fun calculateEnterTranslation(time: Long): Float {
      val fraction = (time.toFloat() / SLIDE_DURATION).coerceIn(0f, 1f)
      return (1f - SLIDE_INTERPOLATOR.getInterpolation(fraction)) * (textHeight / 2f)
    }

    private fun calculateExitTranslation(time: Long): Float {
      val fraction = (time.toFloat() / SLIDE_DURATION).coerceIn(0f, 1f)
      return SLIDE_INTERPOLATOR.getInterpolation(fraction) * (-textHeight / 2f)
    }

    private fun adjustedTimeForIndex(
      time: Long,
      index: Int,
    ): Long {
      return time - index * CHARACTER_START_OFFSET
    }

    private fun ValueAnimator.elapsedTime(): Long {
      return (animatedFraction * duration).toLong()
    }

    private fun Char.animatesIndependently(): Boolean = this != ',' && this != '.'
  }

  private fun FontMetrics.height(): Float = descent - ascent

  private data class TransitionMask(
    val enterAnimatedCharacters: BooleanArray,
    val exitAnimatedCharacters: BooleanArray,
  )

  private data class CharacterDrawState(
    val alpha: Int,
    val yTranslation: Float,
  )

  private companion object {
    private const val SLIDE_DURATION = 350L
    private const val FADE_DURATION = 120L
    private const val FADE_IN_DELAY = 100L
    private const val CHARACTER_START_OFFSET = 30L

    private val SLIDE_INTERPOLATOR = PathInterpolator(0.3f, 0.9f)
  }
}
