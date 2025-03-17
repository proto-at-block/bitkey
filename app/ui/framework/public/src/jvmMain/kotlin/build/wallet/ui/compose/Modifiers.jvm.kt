package build.wallet.ui.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

actual fun Modifier.resId(id: String?): Modifier =
  when (id) {
    null -> this
    else -> testTag(id)
  }
