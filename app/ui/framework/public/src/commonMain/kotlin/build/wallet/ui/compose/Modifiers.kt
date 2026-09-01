package build.wallet.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.platform.haptics.HapticsEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Conditionally animates into and from applying [androidx.compose.ui.draw.blur] modifier.
 */
fun Modifier.blurIf(
  condition: Boolean,
  blurRadius: Dp = 20.dp,
): Modifier = this.then(BlurIfElement(condition, blurRadius))

inline fun Modifier.thenIf(
  condition: Boolean,
  block: () -> Modifier,
): Modifier {
  return then(
    if (condition) {
      block()
    } else {
      Modifier
    }
  )
}

inline fun <T> Modifier.thenIfNotNull(
  value: T?,
  block: Modifier.(T) -> Modifier,
): Modifier {
  return if (value == null) {
    this
  } else {
    then(block(value))
  }
}

/**
 * A clickable modifier that scales down and changes opacity when pressed.
 *
 * @param enabled Whether the click is enabled.
 * @param scaleFactor The scale factor when pressed.
 * @param alphaFactor The alpha factor when pressed.
 * @param pressAnimationDurationMillis Duration for animating into the pressed state.
 * @param releaseAnimationDurationMillis Duration for animating back to the resting state.
 * @param minimumPressedStateDurationMillis Minimum time to keep the pressed visual state visible.
 * @param hapticsEffect The optional haptic effect to trigger on press.
 * @param onClick The callback when clicked.
 */
fun Modifier.scalingClickable(
  enabled: Boolean = true,
  scaleFactor: Float = 0.97f,
  alphaFactor: Float = 0.97f,
  pressAnimationDurationMillis: Int = 70,
  releaseAnimationDurationMillis: Int = 120,
  minimumPressedStateDurationMillis: Int = 0,
  hapticsEffect: HapticsEffect? = HapticsEffect.Selection,
  onClick: () -> Unit,
) = this.then(
  ScalingClickableElement(
    enabled = enabled,
    scaleFactor = scaleFactor,
    alphaFactor = alphaFactor,
    pressAnimationDurationMillis = pressAnimationDurationMillis,
    releaseAnimationDurationMillis = releaseAnimationDurationMillis,
    minimumPressedStateDurationMillis = minimumPressedStateDurationMillis,
    hapticsEffect = hapticsEffect,
    onClick = onClick
  )
)

/**
 * Sets test tag as resource ID to this semantics node, if the tag is provided.
 * The test tag can be used to find nodes in UI testing frameworks.
 */
expect fun Modifier.resId(id: String?): Modifier

private data class BlurIfElement(
  val condition: Boolean,
  val blurRadius: Dp,
) : ModifierNodeElement<BlurIfNode>() {
  override fun create() = BlurIfNode(targetBlur = if (condition) blurRadius.value else 0f)

  override fun update(node: BlurIfNode) {
    node.update(targetBlur = if (condition) blurRadius.value else 0f)
  }
}

private class BlurIfNode(
  targetBlur: Float,
) : Modifier.Node(), LayoutModifierNode {
  /**
   * Animated blur radius in [Dp] units, matching the `animateFloatAsState` behavior of the
   * previous `composed` implementation: starts at the initial target (no animation on first
   * composition) and animates with the default spring on subsequent target changes.
   */
  private val blurAnimatable = Animatable(targetBlur)

  fun update(targetBlur: Float) {
    if (blurAnimatable.targetValue != targetBlur) {
      coroutineScope.launch {
        blurAnimatable.animateTo(targetBlur, spring(visibilityThreshold = 0.01f))
      }
    }
  }

  override fun MeasureScope.measure(
    measurable: Measurable,
    constraints: Constraints,
  ): MeasureResult {
    val placeable = measurable.measure(constraints)
    return layout(placeable.width, placeable.height) {
      placeable.placeWithLayer(0, 0) {
        // Replicates Modifier.blur(radius.dp) with the default
        // BlurredEdgeTreatment.Rectangle: always clip to rect bounds and
        // apply a clamped-edge blur only for non-zero radii.
        val blurPixels = blurAnimatable.value.dp.toPx()
        renderEffect = if (blurPixels > 0f) {
          BlurEffect(blurPixels, blurPixels, TileMode.Clamp)
        } else {
          null
        }
        shape = RectangleShape
        clip = true
      }
    }
  }
}

private data class ScalingClickableElement(
  val enabled: Boolean,
  val scaleFactor: Float,
  val alphaFactor: Float,
  val pressAnimationDurationMillis: Int,
  val releaseAnimationDurationMillis: Int,
  val minimumPressedStateDurationMillis: Int,
  val hapticsEffect: HapticsEffect?,
  val onClick: () -> Unit,
) : ModifierNodeElement<ScalingClickableNode>() {
  override fun create() =
    ScalingClickableNode(
      enabled = enabled,
      scaleFactor = scaleFactor,
      alphaFactor = alphaFactor,
      pressAnimationDurationMillis = pressAnimationDurationMillis,
      releaseAnimationDurationMillis = releaseAnimationDurationMillis,
      minimumPressedStateDurationMillis = minimumPressedStateDurationMillis,
      hapticsEffect = hapticsEffect,
      onClick = onClick
    )

  override fun update(node: ScalingClickableNode) {
    node.update(
      enabled = enabled,
      scaleFactor = scaleFactor,
      alphaFactor = alphaFactor,
      pressAnimationDurationMillis = pressAnimationDurationMillis,
      releaseAnimationDurationMillis = releaseAnimationDurationMillis,
      minimumPressedStateDurationMillis = minimumPressedStateDurationMillis,
      hapticsEffect = hapticsEffect,
      onClick = onClick
    )
  }
}

private class ScalingClickableNode(
  private var enabled: Boolean,
  private var scaleFactor: Float,
  private var alphaFactor: Float,
  private var pressAnimationDurationMillis: Int,
  private var releaseAnimationDurationMillis: Int,
  private var minimumPressedStateDurationMillis: Int,
  private var hapticsEffect: HapticsEffect?,
  private var onClick: () -> Unit,
) : DelegatingNode(),
  LayoutModifierNode,
  SemanticsModifierNode,
  CompositionLocalConsumerModifierNode {
  private val interactionSource = MutableInteractionSource()
  private val scaleAnimation = Animatable(1f)
  private val alphaAnimation = Animatable(1f)
  private var isVisuallyPressed = false
  private var visualPressJob: Job? = null

  // SuspendFunSwallowedCancellation: the CancellationException IS rethrown; the catch only
  // emits a Cancel interaction first so a press can't get stuck when the handler is reset.
  @Suppress("SuspendFunSwallowedCancellation")
  private val pointerInputNode = delegate(
    SuspendingPointerInputModifierNode {
      if (!enabled) {
        return@SuspendingPointerInputModifierNode
      }

      detectTapGestures(
        onPress = { offset ->
          val press = PressInteraction.Press(offset)
          try {
            interactionSource.emit(press)
            val released = tryAwaitRelease()
            interactionSource.emit(
              if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press)
            )
          } catch (e: CancellationException) {
            // The gesture handler was reset or detached mid-press; make sure the press
            // doesn't get stuck in the pressed state.
            interactionSource.tryEmit(PressInteraction.Cancel(press))
            throw e
          }
        },
        onTap = {
          tryPerformClick()
        }
      )
    }
  )

  override fun onAttach() {
    // Replicates collectIsPressedAsState: pressed while any press interaction is active.
    coroutineScope.launch {
      val pressInteractions = mutableListOf<PressInteraction.Press>()
      var isPressed = false
      interactionSource.interactions.collect { interaction ->
        when (interaction) {
          is PressInteraction.Press -> pressInteractions.add(interaction)
          is PressInteraction.Release -> pressInteractions.remove(interaction.press)
          is PressInteraction.Cancel -> pressInteractions.remove(interaction.press)
        }
        val newIsPressed = pressInteractions.isNotEmpty()
        if (newIsPressed != isPressed) {
          isPressed = newIsPressed
          onIsPressedChanged(newIsPressed)
        }
      }
    }
  }

  fun update(
    enabled: Boolean,
    scaleFactor: Float,
    alphaFactor: Float,
    pressAnimationDurationMillis: Int,
    releaseAnimationDurationMillis: Int,
    minimumPressedStateDurationMillis: Int,
    hapticsEffect: HapticsEffect?,
    onClick: () -> Unit,
  ) {
    val enabledChanged = this.enabled != enabled
    this.enabled = enabled
    this.scaleFactor = scaleFactor
    this.alphaFactor = alphaFactor
    this.pressAnimationDurationMillis = pressAnimationDurationMillis
    this.releaseAnimationDurationMillis = releaseAnimationDurationMillis
    this.minimumPressedStateDurationMillis = minimumPressedStateDurationMillis
    this.hapticsEffect = hapticsEffect
    this.onClick = onClick
    if (enabledChanged) {
      // Cancels any in-flight press gesture (emitting PressInteraction.Cancel) and
      // restarts gesture detection with the new enabled state.
      pointerInputNode.resetPointerInputHandler()
      invalidateSemantics()
    }
    // Retarget in-flight animations if the pressed-state factors changed while pressed.
    val targetScale = if (isVisuallyPressed) scaleFactor else 1f
    val targetAlpha = if (isVisuallyPressed) alphaFactor else 1f
    if (scaleAnimation.targetValue != targetScale || alphaAnimation.targetValue != targetAlpha) {
      animateToVisualState(isVisuallyPressed)
    }
  }

  override val shouldMergeDescendantSemantics: Boolean
    get() = true

  override fun SemanticsPropertyReceiver.applySemantics() {
    // Matches Modifier.clickable's semantics (role was not set, indication was null).
    if (enabled) {
      onClick(action = { tryPerformClick() })
    } else {
      disabled()
    }
  }

  override fun MeasureScope.measure(
    measurable: Measurable,
    constraints: Constraints,
  ): MeasureResult {
    val placeable = measurable.measure(constraints)
    return layout(placeable.width, placeable.height) {
      placeable.placeWithLayer(0, 0) {
        scaleX = scaleAnimation.value
        scaleY = scaleAnimation.value
        alpha = alphaAnimation.value
      }
    }
  }

  private fun tryPerformClick(): Boolean {
    if (!enabled) {
      return false
    }

    hapticsEffect?.let { effect ->
      currentValueOf(LocalHaptics)?.let { haptics ->
        coroutineScope.launch { haptics.vibrate(effect) }
      }
    }
    onClick()
    return true
  }

  /**
   * Replicates the `LaunchedEffect(isPressed)` minimum-pressed-duration logic of the previous
   * `composed` implementation, including cancelling a pending "release" delay when a new press
   * arrives.
   */
  private fun onIsPressedChanged(isPressed: Boolean) {
    visualPressJob?.cancel()
    visualPressJob = coroutineScope.launch {
      if (isPressed) {
        updateVisuallyPressed(true)
      } else if (isVisuallyPressed) {
        if (minimumPressedStateDurationMillis > 0) {
          delay(minimumPressedStateDurationMillis.toLong())
        }
        updateVisuallyPressed(false)
      }
    }
  }

  private fun updateVisuallyPressed(pressed: Boolean) {
    if (isVisuallyPressed == pressed) return
    isVisuallyPressed = pressed
    animateToVisualState(pressed)
  }

  private fun animateToVisualState(pressed: Boolean) {
    val durationMillis =
      if (pressed) pressAnimationDurationMillis else releaseAnimationDurationMillis
    val targetScale = if (pressed) scaleFactor else 1f
    val targetAlpha = if (pressed) alphaFactor else 1f
    coroutineScope.launch {
      scaleAnimation.animateTo(targetScale, tween(durationMillis = durationMillis))
    }
    coroutineScope.launch {
      alphaAnimation.animateTo(targetAlpha, tween(durationMillis = durationMillis))
    }
  }
}
