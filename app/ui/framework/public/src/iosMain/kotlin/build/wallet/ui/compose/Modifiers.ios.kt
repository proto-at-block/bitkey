package build.wallet.ui.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Sets test tag as resource ID to this semantics node, if the tag is provided.
 * The test tag can be used to find nodes in UI testing frameworks.
 */
actual fun Modifier.resId(id: String?): Modifier =
  when (id) {
    null -> this
    else -> testTag(id)
  }
