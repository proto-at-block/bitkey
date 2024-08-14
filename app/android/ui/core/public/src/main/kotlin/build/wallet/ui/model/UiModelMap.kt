package build.wallet.ui.model

import build.wallet.statemachine.core.ComposeBodyModel
import kotlin.reflect.KClass

/**
 * Map of [UiModel]s, usually used via [LocalUiModelMap].
 *
 * Can be implemented as [TypedUiModelMap].
 */
interface UiModelMap {
  val uiModels: Map<KClass<*>, UiModel>

  /**
   * Retrieve a [UiModel] using given [Model] type, if any.
   */
  fun <ModelT : Model> getUiModelFor(
    type: KClass<out ModelT>,
    model: ModelT? = null,
  ): UiModel? {
    return if (model is ComposeBodyModel) {
      uiModels[ComposeBodyModel::class]
    } else {
      uiModels[type]
    }
  }
}
