package build.wallet.ui.compose

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.resId(id: String?): Modifier =
  when (id) {
    null -> this
    else -> semantics { testTagsAsResourceId = true }.testTag(id)
  }
