package build.wallet.ui.components.screen

/** This file defines a collection of Transitions for animating content within the App. The
 * definitions for these animations can be found in the Material Design guidelines:
 *
 * https://m2.material.io/develop/android/theming/motion
 */

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.with
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * This defines the Axis for the animation as defined in the Material Design guidelines:
 *
 * https://m2.material.io/develop/android/theming/motion#shared-axis
 */
enum class AxisAnimationDirection {
  Forward,
  Backward,
}

/**
 * This defines the Axis for the animation as defined in the Material Design guidelines:
 *
 * https://m2.material.io/develop/android/theming/motion#shared-axis
 */
enum class Axis {
  X,
  Y,
  Z,
}

private val SharedAxisOffset = 30.dp
private val EnterScales = 1.1f to 0.95f
private val ExitScales = EnterScales.second to EnterScales.first

/**
 * Animation for shared axis, this used for [Root], [RootFullScreen] and [FullScreen] presentations
 * @param axis - [Axis] for the animation
 * @param direction - [AxisAnimationDirection] for the animation, [Forward] for [Push] or [Backward]
 * for [Pop]
 * @return [ContentTransform] with [EnterTransition] and [ExitTransition] for the shared axis
 * animation
 */
fun sharedAxisAnimation(
  axis: Axis,
  direction: AxisAnimationDirection,
  density: Density,
): ContentTransform {
  return when (axis) {
    Axis.X -> SharedXAxisEnterTransition(density, direction) with SharedXAxisExitTransition(density, direction)
    Axis.Y -> SharedYAxisEnterTransition(density, direction) with SharedYAxisExitTransition(density, direction)
    Axis.Z -> SharedZAxisEnterTransition(direction) with SharedZAxisExitTransition(direction)
  }
}

/**
 * Animation for sliding over the view, this used for [Modal] and [ModalFullScreen] presentations
 *
 * @return [ContentTransform] with [EnterTransition] and [ExitTransition] for the slide overlay
 * animation
 */
fun slideOverlayAnimation(direction: AxisAnimationDirection): ContentTransform {
  return when (direction) {
    AxisAnimationDirection.Forward ->
      fadeIn(
        animationSpec =
          tween(
            durationMillis = 300,
            easing = LinearEasing
          )
      ) +
        slideInVertically(
          animationSpec =
            tween(
              durationMillis = 300,
              easing = FastOutSlowInEasing
            ),
          initialOffsetY = { fullHeight -> fullHeight }
        ) with
        fadeOut(
          animationSpec =
            tween(
              durationMillis = 300,
              easing = LinearEasing
            )
        )
    AxisAnimationDirection.Backward ->
      fadeIn(
        animationSpec =
          tween(
            durationMillis = 300,
            easing = LinearEasing
          )
      ) with fadeOut(
        animationSpec =
          tween(
            durationMillis = 300,
            easing = LinearEasing
          )
      ) +
        slideOutVertically(
          animationSpec =
            tween(
              durationMillis = 300,
              easing = FastOutSlowInEasing
            ),
          targetOffsetY = { fullHeight -> fullHeight }
        )
  }
}

private val SharedXAxisEnterTransition: (Density, AxisAnimationDirection) -> EnterTransition = { density, direction ->
  fadeIn(
    animationSpec =
      tween(
        durationMillis = 210,
        delayMillis = 90,
        easing = LinearOutSlowInEasing
      )
  ) +
    slideInHorizontally(
      animationSpec =
        tween(
          durationMillis = 300
        ),
      initialOffsetX = {
        with(density) {
          val offset = SharedAxisOffset.roundToPx()
          when (direction) {
            AxisAnimationDirection.Forward -> offset
            AxisAnimationDirection.Backward -> -offset
          }
        }
      }
    )
}

private val SharedXAxisExitTransition: (Density, AxisAnimationDirection) -> ExitTransition = { density, direction ->
  fadeOut(
    animationSpec =
      tween(
        durationMillis = 90,
        easing = FastOutLinearInEasing
      )
  ) +
    slideOutHorizontally(
      animationSpec =
        tween(
          durationMillis = 300
        ),
      targetOffsetX = {
        with(density) {
          val offset = SharedAxisOffset.roundToPx()
          when (direction) {
            AxisAnimationDirection.Forward -> -offset
            AxisAnimationDirection.Backward -> offset
          }
        }
      }
    )
}

private val SharedYAxisEnterTransition: (Density, AxisAnimationDirection) -> EnterTransition = { density, direction ->
  fadeIn(
    animationSpec =
      tween(
        durationMillis = 210,
        delayMillis = 90,
        easing = LinearOutSlowInEasing
      )
  ) +
    slideInVertically(
      animationSpec =
        tween(
          durationMillis = 300
        ),
      initialOffsetY = {
        with(density) {
          val offset = SharedAxisOffset.roundToPx()
          when (direction) {
            AxisAnimationDirection.Forward -> offset
            AxisAnimationDirection.Backward -> -offset
          }
        }
      }
    )
}

private val SharedYAxisExitTransition: (Density, AxisAnimationDirection) -> ExitTransition = { density, direction ->
  fadeOut(
    animationSpec =
      tween(
        durationMillis = 90,
        easing = FastOutLinearInEasing
      )
  ) +
    slideOutVertically(
      animationSpec =
        tween(
          durationMillis = 300
        ),
      targetOffsetY = {
        with(density) {
          val offset = SharedAxisOffset.roundToPx()
          when (direction) {
            AxisAnimationDirection.Forward -> -offset
            AxisAnimationDirection.Backward -> offset
          }
        }
      }
    )
}

private val SharedZAxisEnterTransition: (AxisAnimationDirection) -> EnterTransition = { direction ->
  val initialScale =
    when (direction) {
      AxisAnimationDirection.Forward -> EnterScales.first
      AxisAnimationDirection.Backward -> EnterScales.second
    }
  scaleIn(
    initialScale = initialScale,
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
  )
}

private val SharedZAxisExitTransition: (AxisAnimationDirection) -> ExitTransition = { direction ->
  val targetScale =
    when (direction) {
      AxisAnimationDirection.Forward -> ExitScales.first
      AxisAnimationDirection.Backward -> ExitScales.second
    }
  scaleOut(targetScale = targetScale, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
}

val NoAnimation: ContentTransform = EnterTransition.None with ExitTransition.None

val FadeAnimation: ContentTransform =
  fadeIn(
    animationSpec = tween(durationMillis = 600)
  ) togetherWith fadeOut(animationSpec = tween(durationMillis = 600))
