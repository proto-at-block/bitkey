package build.wallet.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import build.wallet.statemachine.core.ComposableRenderedModel
import platform.UIKit.UIViewController

@Suppress("unused") // NOTE: Called from swift
class ComposableRenderedModelUiController(
  initialModel: ComposableRenderedModel,
) {
  private val currentModel = mutableStateOf(initialModel)

  /**
   * Update the Compose UI to render the new [bodyModel] UI.
   */
  @Suppress("MemberVisibilityCanBePrivate")
  fun updateBodyModel(model: ComposableRenderedModel) {
    currentModel.value = model
  }

  /**
   * The [UIViewController] displaying updated the shared UI for
   * [currentModel].
   */
  val viewController: UIViewController = ComposeUIViewController {
    val model by currentModel
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center
    ) {
      model.render(modifier = Modifier)
    }
  }
}
