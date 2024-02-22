@file:OptIn(ExperimentalComposeUiApi::class)

package build.wallet.ui.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Conditionally animates into and from applying [blur] modifier.
 */
fun Modifier.blurIf(
  condition: Boolean,
  blurRadius: Dp = 20.dp,
): Modifier =
  composed {
    val blur: Float by animateFloatAsState(if (condition) blurRadius.value else 0f)
    blur(blur.dp)
  }

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

/**
 * Sets test tag as resource ID to this semantics node, if the tag is provided.
 * The test tag can be used to find nodes in UI testing frameworks.
 */
fun Modifier.resId(id: String?): Modifier =
  when (id) {
    null -> this
    else -> semantics { testTagsAsResourceId = true }.testTag(id)
  }
