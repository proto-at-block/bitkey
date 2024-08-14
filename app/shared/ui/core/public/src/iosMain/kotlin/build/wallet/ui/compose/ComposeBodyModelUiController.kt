package build.wallet.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import build.wallet.statemachine.core.ComposeBodyModel
import platform.UIKit.UIViewController

/**
 * Adapts a [ComposeBodyModel] to [UIViewController] using the
 * [ComposeUIViewController] interop builder and provides
 * [updateBodyModel] to update the UI.
 */
@Suppress("unused") // NOTE: Called from swift
class ComposeBodyModelUiController(
  initialModel: ComposeBodyModel,
) {
  private val currentBodyModel = mutableStateOf(initialModel)

  /**
   * Update the Compose UI to render the new [bodyModel] UI.
   */
  @Suppress("MemberVisibilityCanBePrivate")
  fun updateBodyModel(bodyModel: ComposeBodyModel) {
    currentBodyModel.value = bodyModel
  }

  /**
   * The [UIViewController] displaying updated the shared UI for
   * [currentBodyModel].
   */
  val viewController: UIViewController = ComposeUIViewController {
    val bodyModel by currentBodyModel
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center
    ) {
      bodyModel.render()
    }
  }
}
