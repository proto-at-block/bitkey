package build.wallet.ui.components.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset

private const val ANIMATE_MOTION_OFFSET = 40
private const val ANIMATE_MOTION_DURATION = 200
private const val ANIMATE_VISIBILITY_DURATION = 200
private const val COLLAPSED_SCALE = 1.3f
private const val EXPANDED_SCALE = 0.7f

/**
 * A container to animate between the display of [topContent] and
 * [bottomContent] into a collapsed display of only [collapsedContent].
 *
 * @param collapsed When true, display [collapsedContent] otherwise display both [topContent] and [bottomContent].
 * @param verticalArrangement The vertical arrangement of [topContent] and [bottomContent] composables.
 * @param horizontalAlignment The horizontal arrangement of all content composables.
 * @param topContent The top content for the expanded state.
 * @param bottomContent The bottom content for the expanded state.
 * @param collapsedContent The content to display for the collapsed state.
 */
@Composable
fun CollapsibleLabelContainer(
  modifier: Modifier = Modifier,
  collapsed: Boolean,
  verticalArrangement: Arrangement.Vertical,
  horizontalAlignment: Alignment.Horizontal,
  topContent: (@Composable AnimatedVisibilityScope.() -> Unit)?,
  bottomContent: (@Composable AnimatedVisibilityScope.() -> Unit)?,
  collapsedContent: @Composable AnimatedVisibilityScope.() -> Unit,
) {
  Box(modifier = modifier, contentAlignment = Alignment.CenterEnd) {
    val motionTween = remember { tween<Float>(ANIMATE_MOTION_DURATION, easing = LinearEasing) }
    val fadeTween = remember { tween<Float>(ANIMATE_VISIBILITY_DURATION, easing = LinearEasing) }
    AnimatedVisibility(
      visible = collapsed,
      enter = scaleIn(motionTween, COLLAPSED_SCALE, TransformOrigin.Center) + fadeIn(fadeTween),
      exit = scaleOut(motionTween, COLLAPSED_SCALE, TransformOrigin.Center) + fadeOut(fadeTween),
      modifier = Modifier.matchParentSize(),
      content = {
        Column(
          horizontalAlignment = horizontalAlignment,
          verticalArrangement = Arrangement.Center
        ) {
          collapsedContent()
        }
      }
    )

    MeasureWithoutPlacement {
      Column(verticalArrangement = verticalArrangement) {
        topContent?.let { AnimatedVisibility(visible = true, content = topContent) }
        bottomContent?.let { AnimatedVisibility(visible = true, content = bottomContent) }
      }
      AnimatedVisibility(visible = true) { collapsedContent() }
    }

    Column(
      horizontalAlignment = horizontalAlignment,
      modifier = Modifier.matchParentSize(),
      verticalArrangement = verticalArrangement
    ) {
      if (topContent != null) {
        AnimatedContentContainer(
          collapsed = collapsed,
          transitionSpec = TransitionSpec.Top,
          content = topContent
        )
      }

      if (bottomContent != null) {
        AnimatedContentContainer(
          collapsed = collapsed,
          transitionSpec = TransitionSpec.Bottom,
          content = bottomContent
        )
      }
    }
  }
}

private sealed class TransitionSpec(val offset: Int) {
  data object Top : TransitionSpec(ANIMATE_MOTION_OFFSET)

  data object Bottom : TransitionSpec(-ANIMATE_MOTION_OFFSET)
}

@Composable
private fun AnimatedContentContainer(
  collapsed: Boolean,
  transitionSpec: TransitionSpec,
  content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
  val motionTweenFloat = remember { tween<Float>(ANIMATE_MOTION_DURATION, easing = LinearEasing) }
  val motionTween = remember { tween<IntOffset>(ANIMATE_MOTION_DURATION, easing = LinearEasing) }
  val fadeTween = remember { tween<Float>(ANIMATE_VISIBILITY_DURATION, easing = LinearEasing) }
  AnimatedVisibility(
    visible = !collapsed,
    enter =
      scaleIn(motionTweenFloat, EXPANDED_SCALE) +
        slideInVertically(motionTween) { transitionSpec.offset } +
        fadeIn(fadeTween),
    exit =
      scaleOut(motionTweenFloat, EXPANDED_SCALE) +
        slideOutVertically(motionTween) { transitionSpec.offset } +
        fadeOut(fadeTween),
    content = content
  )
}
