package build.wallet.ui.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import build.wallet.statemachine.core.ComposableRenderedModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.ui.app.App
import build.wallet.ui.model.UiModel
import build.wallet.ui.model.UiModelMap
import platform.UIKit.UIViewController
import kotlin.reflect.KClass

private val ComposableRenderedModelUiModelMap = object : UiModelMap {
  override val uiModels: Map<KClass<*>, UiModel> =
    mapOf(
      Pair(
        FormBodyModel::class,
        UiModel<FormBodyModel> { it.render(Modifier) }
      )
    )
}

@Suppress("unused") // NOTE: Called from swift
class ComposableRenderedScreenModelUiController(
  initialModel: ScreenModel,
) {
  private val currentModel = mutableStateOf(initialModel)

  init {
    requireComposableRenderedModel(initialModel)
  }

  /**
   * Update the Compose UI to render the new [ComposableRenderedModel] UI.
   */
  @Suppress("MemberVisibilityCanBePrivate")
  fun updateBodyModel(model: ScreenModel) {
    currentModel.value = model
    requireComposableRenderedModel(model)
  }

  /**
   * The [UIViewController] displaying updated the shared UI for
   * [currentModel].
   */
  val viewController: UIViewController = ComposeUIViewController {
    val model by currentModel

    App(
      model = model,
      uiModelMap = ComposableRenderedModelUiModelMap
    )
  }

  private fun requireComposableRenderedModel(model: ScreenModel) {
    require(model.body is ComposableRenderedModel) {
      "Expected ComposableRenderedModel but got ${model.body as ComposableRenderedModel?}"
    }
  }
}
